package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository.ChangeRow;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchDisablePort;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.ChangeStatus;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RequestSource;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Snapshot;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SnapshotIntegrity;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchRedisStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class DataRetentionDetailCleanupSafetyConverger {
    private final DataRetentionDynamicSwitchRedisStore redis;
    private final DataRetentionDynamicSwitchChangeRepository changes;
    private final DataRetentionDynamicSwitchDisablePort disablePort;
    private final DataRetentionProperties properties;
    private final DataRetentionDetailCleanupAlertSink alerts;

    public DataRetentionDetailCleanupSafetyConverger(DataRetentionDynamicSwitchRedisStore redis,
                                                      DataRetentionDynamicSwitchChangeRepository changes,
                                                      DataRetentionDynamicSwitchDisablePort disablePort,
                                                      DataRetentionProperties properties,
                                                      DataRetentionDetailCleanupAlertSink alerts) {
        this.redis=redis; this.changes=changes; this.disablePort=disablePort; this.properties=properties; this.alerts=alerts;
    }

    public void converge(ReadinessEvaluation evaluation) {
        if (!evaluation.requiresDisable()) return;
        ChangeRow requested = changes.findRequested(1000).stream()
                .filter(row -> row.switchKey() == SwitchKey.GLOBAL_EXECUTE && !row.enabled())
                .findFirst().orElse(null);
        if (requested != null) {
            ChangeRow resumed = disablePort.resume(requested);
            if (resumed.status() != ChangeStatus.APPLIED) unresolved(evaluation, "requested disable remains unresolved");
            return;
        }

        Snapshot observed;
        try { observed = redis.read(SwitchKey.GLOBAL_EXECUTE); }
        catch (RuntimeException error) { observed = Snapshot.unavailable(SwitchKey.GLOBAL_EXECUTE, error); }
        if (observed.integrity() == SnapshotIntegrity.VALID && !observed.payload().enabled()) return;

        String decision = evaluation.primaryCode().name();
        String canonical;
        String reason;
        if (observed.integrity() == SnapshotIntegrity.VALID) {
            String changeId = observed.payload().changeId();
            long version = observed.payload().version();
            canonical = trustedCanonical(decision, changeId, version);
            reason = "wp8:" + decision + ":global_true=" + changeId + "@" + version;
        } else {
            ChangeRow latest = changes.findLatest(SwitchKey.GLOBAL_EXECUTE).orElse(null);
            String changeId = latest == null ? "<bootstrap>" : latest.changeId();
            long version = latest == null ? 0 : latest.version();
            canonical = untrustedCanonical(decision, changeId, version);
            reason = "wp8:" + decision + ":global_audit=" + changeId + "@" + version + ":redis_untrusted";
            alerts.alert("REDIS_UNTRUSTED_DISABLE_BASIS", decision);
        }
        String disableId = UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8)).toString();
        ChangeRow result = disablePort.disable(SwitchKey.GLOBAL_EXECUTE, disableId,
                properties.getDynamicSwitch().getReconcileOperatorUserId(), RequestSource.RECONCILE,
                reason, null);
        if (result.status() != ChangeStatus.APPLIED) unresolved(evaluation, "global disable remains unresolved");
    }

    static String trustedCanonical(String decision, String changeId, long version) {
        return "schema=retention-wp8-disable-v1\n"
                + "switchKey=global_execute\n"
                + "decision=" + decision + "\n"
                + "observedGlobalChangeId=" + changeId + "\n"
                + "observedGlobalVersion=" + version + "\n";
    }
    static String untrustedCanonical(String decision, String changeId, long version) {
        return "schema=retention-wp8-disable-v1\n"
                + "switchKey=global_execute\n"
                + "decision=" + decision + "\n"
                + "untrustedAuditChangeId=" + changeId + "\n"
                + "untrustedAuditVersion=" + version + "\n";
    }
    private void unresolved(ReadinessEvaluation evaluation, String detail) {
        alerts.alert("AUTO_DISABLE_UNRESOLVED", evaluation.primaryCode() + ":" + detail);
        throw new DataRetentionDetailCleanupException("AUTO_DISABLE_UNRESOLVED", detail);
    }
}
