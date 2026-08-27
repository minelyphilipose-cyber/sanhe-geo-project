package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository.ChangeRow;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.ChangeStatus;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DynamicSwitchException;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RequestSource;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Snapshot;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SnapshotIntegrity;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class DataRetentionDynamicSwitchReconciler {

    private final DataRetentionDynamicSwitchChangeRepository repository;
    private final DataRetentionDynamicSwitchRedisStore redisStore;
    private final DataRetentionDynamicSwitchDisablePort disablePort;
    private final DataRetentionDynamicSwitchAuthorizationPolicy authorization;
    private final DataRetentionDynamicSwitchAlertSink alertSink;
    private final DataRetentionProperties properties;
    private final DataRetentionDynamicSwitchMetrics metrics;
    private final Clock clock;

    public DataRetentionDynamicSwitchReconciler(DataRetentionDynamicSwitchChangeRepository repository,
                                                 DataRetentionDynamicSwitchRedisStore redisStore,
                                                 DataRetentionDynamicSwitchDisablePort disablePort,
                                                 DataRetentionDynamicSwitchAuthorizationPolicy authorization,
                                                 DataRetentionDynamicSwitchAlertSink alertSink,
                                                 DataRetentionProperties properties,
                                                 DataRetentionDynamicSwitchMetrics metrics) {
        this.repository = repository;
        this.redisStore = redisStore;
        this.disablePort = disablePort;
        this.authorization = authorization;
        this.alertSink = alertSink;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = Clock.systemUTC();
    }

    @Scheduled(
            initialDelayString = "${geo.retention.dynamic-switch.reconcile-initial-delay-ms:30000}",
            fixedDelayString = "${geo.retention.dynamic-switch.reconcile-fixed-delay-ms:30000}")
    public void reconcile() {
        if (!properties.getDynamicSwitch().isReconcilerEnabled()) return;
        long operatorId = properties.getDynamicSwitch().getReconcileOperatorUserId();
        try {
            authorization.validateReconcileOperator(operatorId);
        } catch (RuntimeException error) {
            String code = error instanceof DynamicSwitchException dynamic
                    ? dynamic.resultCode()
                    : "RECONCILE_OPERATOR_INVALID";
            if ("RECONCILE_OPERATOR_MISSING".equals(code)) {
                alertSink.resolve("RECONCILE_OPERATOR_INVALID", null);
            } else if ("RECONCILE_OPERATOR_INVALID".equals(code)) {
                alertSink.resolve("RECONCILE_OPERATOR_MISSING", null);
            }
            alertSink.alert(code, null, error.getClass().getSimpleName());
            return;
        }
        alertSink.resolve("RECONCILE_OPERATOR_MISSING", null);
        alertSink.resolve("RECONCILE_OPERATOR_INVALID", null);
        for (ChangeRow row : repository.findRequested(properties.getDynamicSwitch().getReconcileBatchSize())) {
            try {
                reconcileRequested(row, operatorId);
            } catch (RuntimeException error) {
                log.warn("Dynamic switch reconcile failed changeId={} switchKey={} error={}",
                        row.changeId(), row.switchKey().databaseValue(), error.getClass().getSimpleName());
            }
        }
        for (SwitchKey key : SwitchKey.values()) {
            try {
                reconcileKey(key, operatorId);
            } catch (RuntimeException error) {
                log.warn("Dynamic switch full-key reconcile failed switchKey={} error={}",
                        key.databaseValue(), error.getClass().getSimpleName());
            }
        }
        metrics.requestedBacklog(repository.summarizeRequested(), now());
    }

    void reconcileRequested(ChangeRow row, long operatorId) {
        if (!row.enabled()) {
            disablePort.resume(row);
            return;
        }
        final Snapshot current;
        try {
            current = redisStore.read(row.switchKey());
        } catch (RuntimeException error) {
            repository.recordUncertain(row, null, "redis_unavailable:" + error.getClass().getSimpleName());
            alertSink.alert("REDIS_UNAVAILABLE", row.switchKey(), "requested enable remains unresolved");
            return;
        }
        if (current.integrity() == SnapshotIntegrity.MALFORMED
                || current.integrity() == SnapshotIntegrity.UNAVAILABLE) {
            String code = current.integrity() == SnapshotIntegrity.MALFORMED
                    ? "MALFORMED_CURRENT" : "REDIS_UNAVAILABLE";
            repository.recordUncertain(row, null, code.toLowerCase());
            alertSink.alert(code, row.switchKey(), "requested enable remains unresolved");
            return;
        }
        if (current.integrity() == SnapshotIntegrity.VALID && current.payload().equals(row.payload())) {
            if (row.approvedBy() != null) {
                repository.finalizeApplied(row, current.payload(), now());
            } else {
                repository.finalizeFailed(row, current.payload(), "unapproved_enable_observed", now());
                alertSink.alert("UNAPPROVED_ENABLE_OBSERVED", row.switchKey(), "compensating disable");
                compensate(row.switchKey(), operatorId, "unapproved Redis true");
            }
            return;
        }
        if (row.approvedAt() == null) {
            Instant redisNow = redisStore.redisTime();
            if (!redisNow.isBefore(row.changedAt()) && redisNow.isAfter(row.changedAt().plusMillis(
                    DataRetentionDynamicSwitchModels.ENABLE_APPROVAL_EXPIRY_MILLIS))) {
                repository.finalizeFailed(row,
                        current.integrity() == SnapshotIntegrity.VALID ? current.payload() : null,
                        "approval_expired", now());
            }
            return;
        }
        Instant redisNow = redisStore.redisTime();
        if (redisNow.isBefore(row.approvedAt())) {
            alertSink.alert("REDIS_TIME_ROLLBACK", row.switchKey(), "approved enable remains requested");
        } else if (redisNow.isAfter(row.approvedAt().plusMillis(
                DataRetentionDynamicSwitchModels.ENABLE_CAS_GRACE_MILLIS))) {
            repository.expireApprovedEnable(row,
                    current.integrity() == SnapshotIntegrity.VALID ? current.payload() : null, now());
        }
        // Deliberately no enable CAS here. Only the approval service can write true.
    }

    void reconcileKey(SwitchKey key, long operatorId) {
        ChangeRow latest = repository.findLatest(key).orElse(null);
        if (latest == null) return;
        final Snapshot current;
        try {
            current = redisStore.read(key);
        } catch (RuntimeException error) {
            alertSink.alert("REDIS_UNAVAILABLE", key, error.getClass().getSimpleName());
            metrics.divergence(key, "redis_unavailable");
            return;
        }
        if (current.integrity() == SnapshotIntegrity.MALFORMED) {
            alertSink.alert("MALFORMED_CURRENT", key, "preserved for break-glass repair");
            return;
        }
        if (current.integrity() == SnapshotIntegrity.UNAVAILABLE) return;
        long currentVersion = current.integrity() == SnapshotIntegrity.VALID ? current.payload().version() : 0L;
        if (currentVersion < latest.version()) {
            alertSink.alert("VERSION_ROLLBACK", key,
                    latest.status() == ChangeStatus.REQUESTED ? "REQUEST_IN_FLIGHT" : "audit version is newer");
            metrics.divergence(key, "version_rollback");
            if (latest.status() == ChangeStatus.REQUESTED && !latest.enabled()) {
                disablePort.resume(latest);
            } else if (current.integrity() == SnapshotIntegrity.VALID && current.payload().enabled()) {
                compensate(key, operatorId, "unsafe Redis true version rollback");
            } else if (latest.status() == ChangeStatus.REQUESTED && latest.enabled()) {
                reconcileRequested(latest, operatorId);
            } else {
                compensate(key, operatorId, "Redis version rollback");
            }
            return;
        }
        if (currentVersion > latest.version()) {
            alertSink.alert("REDIS_VERSION_AHEAD", key, "compensating disable");
            metrics.divergence(key, "redis_version_ahead");
            compensate(key, operatorId, "Redis version ahead of audit");
            return;
        }
        if (current.integrity() == SnapshotIntegrity.VALID && !current.payload().equals(latest.payload())) {
            alertSink.alert("SAME_VERSION_DIFFERENT_PAYLOAD", key, "manual repair required");
            metrics.divergence(key, "same_version_different_payload");
            return;
        }
        if (current.integrity() == SnapshotIntegrity.VALID && current.payload().equals(latest.payload())) {
            if (latest.status() == ChangeStatus.REQUESTED && !latest.enabled()) {
                disablePort.resume(latest);
                return;
            }
            if (latest.status() == ChangeStatus.REQUESTED && latest.enabled()) {
                if (latest.approvedBy() != null) {
                    repository.finalizeApplied(latest, current.payload(), now());
                } else {
                    repository.finalizeFailed(latest, current.payload(), "unapproved_enable_observed", now());
                    compensate(key, operatorId, "unapproved Redis true");
                }
                return;
            }
            if (latest.status() == ChangeStatus.FAILED && !latest.enabled()) {
                compensate(key, operatorId, "failed disable lacks applied successor");
                return;
            }
        }
        if (current.integrity() == SnapshotIntegrity.VALID && current.payload().enabled()
                && !(latest.status() == ChangeStatus.APPLIED && latest.enabled()
                && latest.approvedBy() != null && latest.approvalRef() != null)) {
            alertSink.alert("UNAUTHORIZED_TRUE", key, "compensating disable");
            compensate(key, operatorId, "Redis true lacks approved applied audit");
        }
    }

    private void compensate(SwitchKey key, long operatorId, String reason) {
        ChangeRow latest = repository.findLatest(key).orElse(null);
        if (latest != null && latest.status() == ChangeStatus.REQUESTED && !latest.enabled()) {
            disablePort.resume(latest);
            return;
        }
        String compensationId = UUID.nameUUIDFromBytes(("retention-switch-v1:" + key.databaseValue()
                + ":" + (latest == null ? 0 : latest.version())).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        disablePort.disable(key, compensationId, operatorId, RequestSource.RECONCILE,
                reason, null);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
