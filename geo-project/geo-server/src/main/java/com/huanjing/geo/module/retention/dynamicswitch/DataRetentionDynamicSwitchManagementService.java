package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupModels.ReadinessEvaluation;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupException;
import com.huanjing.geo.module.retention.detailcleanup.DataRetentionDetailCleanupPromotionGate;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository.ChangeRow;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository.CreateCommand;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.CasResult;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.CasResultCode;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.ChangeStatus;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DynamicSwitchException;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Payload;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RequestSource;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RequestedSubstate;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Snapshot;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SnapshotIntegrity;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import com.huanjing.geo.module.system.entity.SysUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class DataRetentionDynamicSwitchManagementService {

    private final DataRetentionDynamicSwitchChangeRepository repository;
    private final DataRetentionDynamicSwitchRedisStore redisStore;
    private final DataRetentionDynamicSwitchDisablePort disablePort;
    private final DataRetentionDynamicSwitchAuthorizationPolicy authorization;
    private final DataRetentionDynamicSwitchAlertSink alertSink;
    private final DataRetentionProperties properties;
    private final DataRetentionDynamicSwitchMetrics metrics;
    private final Clock clock;
    private final DataRetentionDetailCleanupPromotionGate detailCleanupGate;

    @Autowired
    public DataRetentionDynamicSwitchManagementService(DataRetentionDynamicSwitchChangeRepository repository,
                                                        DataRetentionDynamicSwitchRedisStore redisStore,
                                                        DataRetentionDynamicSwitchDisablePort disablePort,
                                                        DataRetentionDynamicSwitchAuthorizationPolicy authorization,
                                                        DataRetentionDynamicSwitchAlertSink alertSink,
                                                        DataRetentionProperties properties,
                                                        DataRetentionDynamicSwitchMetrics metrics,
                                                        DataRetentionDetailCleanupPromotionGate detailCleanupGate) {
        this(repository, redisStore, disablePort, authorization, alertSink, properties, metrics,
                detailCleanupGate, Clock.systemUTC());
    }

    public DataRetentionDynamicSwitchManagementService(DataRetentionDynamicSwitchChangeRepository repository,
                                                        DataRetentionDynamicSwitchRedisStore redisStore,
                                                        DataRetentionDynamicSwitchDisablePort disablePort,
                                                        DataRetentionDynamicSwitchAuthorizationPolicy authorization,
                                                        DataRetentionDynamicSwitchAlertSink alertSink,
                                                        DataRetentionProperties properties,
                                                        DataRetentionDynamicSwitchMetrics metrics) {
        this(repository, redisStore, disablePort, authorization, alertSink, properties, metrics, null, Clock.systemUTC());
    }

    DataRetentionDynamicSwitchManagementService(DataRetentionDynamicSwitchChangeRepository repository,
                                                DataRetentionDynamicSwitchRedisStore redisStore,
                                                DataRetentionDynamicSwitchDisablePort disablePort,
                                                DataRetentionDynamicSwitchAuthorizationPolicy authorization,
                                                DataRetentionDynamicSwitchAlertSink alertSink,
                                                DataRetentionProperties properties,
                                                DataRetentionDynamicSwitchMetrics metrics,
                                                Clock clock) {
        this(repository, redisStore, disablePort, authorization, alertSink, properties, metrics, null, clock);
    }

    DataRetentionDynamicSwitchManagementService(DataRetentionDynamicSwitchChangeRepository repository,
                                                DataRetentionDynamicSwitchRedisStore redisStore,
                                                DataRetentionDynamicSwitchDisablePort disablePort,
                                                DataRetentionDynamicSwitchAuthorizationPolicy authorization,
                                                DataRetentionDynamicSwitchAlertSink alertSink,
                                                DataRetentionProperties properties,
                                                DataRetentionDynamicSwitchMetrics metrics,
                                                DataRetentionDetailCleanupPromotionGate detailCleanupGate,
                                                Clock clock) {
        this.repository = repository;
        this.redisStore = redisStore;
        this.disablePort = disablePort;
        this.authorization = authorization;
        this.alertSink = alertSink;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
        this.detailCleanupGate = detailCleanupGate;
    }

    public ChangeView createManual(SwitchKey switchKey, ManualChangeCommand command) {
        Objects.requireNonNull(command, "command");
        SysUser user = authorization.requireCurrent(command.targetEnabled()
                ? DataRetentionDynamicSwitchAuthorizationPolicy.ENABLE
                : DataRetentionDynamicSwitchAuthorizationPolicy.DISABLE);
        Optional<ChangeRow> existing = repository.findByChangeId(command.changeId());
        if (existing.isPresent()) {
            ChangeRow row = existing.get();
            if (row.enabled() && !(row.status() == ChangeStatus.APPLIED && row.terminal())) {
                requireDetailCleanupReady();
            }
            ChangeRow replay = repository.createRequested(new CreateCommand(command.changeId(), switchKey,
                    command.targetEnabled(), row.changedAt(), user.getId(), RequestSource.MANUAL,
                    command.reason(), command.approvalRef(), command.ticketRef()), null);
            if (replay.enabled()) return view(replay, readBestEffort(switchKey), "REQUEST_REPLAY");
            return applyDisable(replay);
        }
        if (command.targetEnabled() && !properties.getDynamicSwitch().isEnableWriteEnabled()) {
            throw new DynamicSwitchException(409, "ENABLE_WRITE_DISABLED", "Dynamic enable writes are statically disabled");
        }
        if (command.targetEnabled()) requireDetailCleanupReady();
        Instant changedAt = command.targetEnabled() ? readRedisTimeForEnable() : now();
        Snapshot observed = readBestEffort(switchKey);
        Long observedVersion = observed != null && observed.integrity() == SnapshotIntegrity.VALID
                ? observed.payload().version() : null;
        ChangeRow row = repository.createRequested(new CreateCommand(command.changeId(), switchKey,
                command.targetEnabled(), changedAt, user.getId(), RequestSource.MANUAL,
                command.reason(), command.approvalRef(), command.ticketRef()), observedVersion);
        metrics.change(row.switchKey(), row.enabled(), row.status().databaseValue(), row.source());
        if (row.enabled()) return view(row, observed, "AWAITING_APPROVAL");
        return applyDisable(row);
    }

    public ChangeView emergencyDisable(SwitchKey switchKey, DisableCommand command) {
        SysUser user = authorization.requireCurrent(DataRetentionDynamicSwitchAuthorizationPolicy.EMERGENCY_DISABLE);
        ChangeRow row = createDisable(switchKey, command.changeId(), command.reason(), command.ticketRef(),
                user.getId(), RequestSource.EMERGENCY);
        return applyDisable(row);
    }

    public ChangeView internalDisable(SwitchKey switchKey, DisableCommand command, long operatorUserId) {
        authorization.validateReconcileOperator(operatorUserId);
        ChangeRow row = createDisable(switchKey, command.changeId(), command.reason(), command.ticketRef(),
                operatorUserId, RequestSource.RECONCILE);
        return applyDisable(row);
    }

    public ChangeView approve(String changeId, String approvalRef) {
        SysUser approver = authorization.requireCurrent(DataRetentionDynamicSwitchAuthorizationPolicy.APPROVE);
        String normalizedRef = DataRetentionDynamicSwitchChangeRepository.normalizeRequired(
                approvalRef, 128, "approvalRef");
        Optional<ChangeRow> existing = repository.findByChangeId(changeId);
        if (existing.isPresent() && existing.get().status() == ChangeStatus.APPLIED
                && existing.get().enabled()
                && Objects.equals(existing.get().approvalRef(), normalizedRef)
                && Objects.equals(existing.get().approvedBy(), approver.getId())) {
            return view(existing.get(), readBestEffort(existing.get().switchKey()), "TERMINAL_REPLAY");
        }
        if (!properties.getDynamicSwitch().isEnableWriteEnabled()) {
            throw new DynamicSwitchException(409, "ENABLE_WRITE_DISABLED", "Dynamic enable writes are statically disabled");
        }
        Instant approvalTime = readRedisTimeForEnable();
        ChangeRow approved = repository.approve(changeId, approver.getId(), normalizedRef, approvalTime);
        if (approved.status() == ChangeStatus.FAILED && "approval_expired".equals(approved.lastError())) {
            throw new DynamicSwitchException(409, "APPROVAL_EXPIRED", "Enable approval request expired");
        }
        if (approved.status() == ChangeStatus.APPLIED) {
            return view(approved, readBestEffort(approved.switchKey()), "TERMINAL_REPLAY");
        }
        if (!Objects.equals(approved.approvedBy(), approver.getId())) {
            throw new DynamicSwitchException(403, "APPROVER_REPLAY_MISMATCH",
                    "Only the original approver may replay an enable write");
        }
        requireDetailCleanupReady();
        Snapshot expected = repository.findExpectedAppliedPayload(approved)
                .map(payload -> new Snapshot(approved.switchKey(), SnapshotIntegrity.VALID, payload, null))
                .orElseGet(() -> Snapshot.missing(approved.switchKey()));
        final CasResult result;
        try {
            result = redisStore.enableCas(approved.switchKey(), approved.payload(), expected, approved.approvedAt());
        } catch (RuntimeException error) {
            repository.recordUncertain(approved, null, "redis_unavailable:" + error.getClass().getSimpleName());
            alertSink.alert("REDIS_UNAVAILABLE", approved.switchKey(), error.getClass().getSimpleName());
            throw new DynamicSwitchException(503, "REDIS_UNAVAILABLE", "Enable CAS outcome is uncertain", error);
        }
        if (result.exactSuccess()) {
            metrics.cas(approved.switchKey(), true, result.code());
            ChangeRow terminal = repository.finalizeApplied(approved, result.observed().payload(), now());
            return view(terminal, result.observed(), result.code().name());
        }
        if (result.code() == CasResultCode.ENABLE_APPROVAL_EXPIRED) {
            metrics.cas(approved.switchKey(), true, result.code());
            ChangeRow terminal = repository.expireApprovedEnable(approved, result.observed().payload(), now());
            return view(terminal, result.observed(), result.code().name());
        }
        repository.recordUncertain(approved, result.observed().payload(), result.code().name().toLowerCase());
        metrics.cas(approved.switchKey(), true, result.code());
        if (result.code() == CasResultCode.REDIS_TIME_ROLLBACK) {
            alertSink.alert("REDIS_TIME_ROLLBACK", approved.switchKey(), "enable CAS rejected");
            throw new DynamicSwitchException(503, result.code().name(), "Redis time rollback rejected enable CAS");
        }
        throw new DynamicSwitchException(409, result.code().name(), "Enable CAS did not apply");
    }

    public ChangeView reconcile(String changeId) {
        SysUser operator = authorization.requireCurrent(DataRetentionDynamicSwitchAuthorizationPolicy.RECONCILE);
        ChangeRow row = requireChange(changeId);
        if (row.terminal()) return view(row, readBestEffort(row.switchKey()), "TERMINAL_REPLAY");
        return reconcileRequested(row, operator.getId());
    }

    public ChangeView get(String changeId) {
        authorization.requireCurrent(DataRetentionDynamicSwitchAuthorizationPolicy.READ);
        ChangeRow row = requireChange(changeId);
        return view(row, readBestEffort(row.switchKey()), "READ");
    }

    public List<SwitchView> list() {
        authorization.requireCurrent(DataRetentionDynamicSwitchAuthorizationPolicy.READ);
        List<SwitchView> views = new ArrayList<>();
        for (SwitchKey key : SwitchKey.values()) {
            Snapshot snapshot = readBestEffort(key);
            ChangeRow latest = repository.findLatest(key).orElse(null);
            views.add(new SwitchView(key, snapshot, latest == null ? null : view(latest, snapshot, "READ"),
                    divergence(latest, snapshot)));
        }
        return List.copyOf(views);
    }

    ChangeView reconcileRequested(ChangeRow row, long operatorUserId) {
        if (!row.enabled()) return applyDisable(row);
        Snapshot observed = readBestEffort(row.switchKey());
        if (observed != null && observed.integrity() == SnapshotIntegrity.VALID
                && observed.payload().equals(row.payload())) {
            if (row.approvedBy() == null) {
                repository.finalizeFailed(row, observed.payload(), "unapproved_enable_observed", now());
                alertSink.alert("UNAPPROVED_ENABLE_OBSERVED", row.switchKey(), "compensation required");
                String compensationId = java.util.UUID.nameUUIDFromBytes(("retention-switch-v1:manual:"
                        + row.switchKey().databaseValue() + ":" + row.version())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                ChangeRow compensated = disablePort.disable(row.switchKey(), compensationId, operatorUserId,
                        RequestSource.RECONCILE, "unapproved Redis true", row.ticketRef());
                return view(compensated, readBestEffort(row.switchKey()), "UNAPPROVED_TRUE_COMPENSATED");
            }
            return view(repository.finalizeApplied(row, observed.payload(), now()), observed, "ALREADY_APPLIED");
        }
        if (observed == null || observed.integrity() == SnapshotIntegrity.UNAVAILABLE
                || observed.integrity() == SnapshotIntegrity.MALFORMED) {
            String errorCode = observed != null && observed.integrity() == SnapshotIntegrity.MALFORMED
                    ? "malformed_current" : "redis_unavailable";
            repository.recordUncertain(row, null, errorCode);
            alertSink.alert(errorCode.toUpperCase(), row.switchKey(), "requested enable remains unresolved");
            return view(row, observed, errorCode.toUpperCase());
        }
        if (row.approvedAt() == null) return view(row, observed, "AWAITING_APPROVAL");
        final Instant redisNow;
        try {
            redisNow = redisStore.redisTime();
        } catch (RuntimeException error) {
            repository.recordUncertain(row, observed == null ? null : observed.payload(), "redis_time_unavailable");
            return view(row, observed, "REDIS_UNAVAILABLE");
        }
        if (redisNow.isBefore(row.approvedAt())) {
            alertSink.alert("REDIS_TIME_ROLLBACK", row.switchKey(), "reconciler observed rollback");
            return view(row, observed, "REDIS_TIME_ROLLBACK");
        }
        if (redisNow.isAfter(row.approvedAt().plusMillis(DataRetentionDynamicSwitchModels.ENABLE_CAS_GRACE_MILLIS))) {
            ChangeRow failed = repository.expireApprovedEnable(row, observed == null ? null : observed.payload(), now());
            return view(failed, observed, "ENABLE_APPROVAL_EXPIRED");
        }
        return view(row, observed, "AWAITING_EXPLICIT_APPROVER_REPLAY");
    }

    private ChangeRow createDisable(SwitchKey key, String changeId, String reason, String ticketRef,
                                    long requestedBy, RequestSource source) {
        Snapshot observed = readBestEffort(key);
        Long observedVersion = observed != null && observed.integrity() == SnapshotIntegrity.VALID
                ? observed.payload().version() : null;
        return repository.createRequested(new CreateCommand(changeId, key, false, now(), requestedBy,
                source, reason, null, ticketRef), observedVersion);
    }

    private ChangeView applyDisable(ChangeRow row) {
        if (row.status() == ChangeStatus.APPLIED || row.status() == ChangeStatus.FAILED) {
            return view(row, readBestEffort(row.switchKey()), "TERMINAL_REPLAY");
        }
        try {
            ChangeRow resolved = disablePort.resume(row);
            Snapshot observed = readBestEffort(row.switchKey());
            String resultCode = resolved.changeId().equals(row.changeId())
                    ? resolved.status().name() : "STALE_TARGET_COMPENSATED";
            return view(resolved, observed, resultCode);
        } catch (DynamicSwitchException error) {
            if ("MALFORMED_CURRENT".equals(error.resultCode())
                    || "SAME_VERSION_DIFFERENT_PAYLOAD".equals(error.resultCode())) throw error;
            throw error;
        } catch (RuntimeException error) {
            repository.recordUncertain(row, null, "redis_unavailable:" + error.getClass().getSimpleName());
            alertSink.alert("REDIS_UNAVAILABLE", row.switchKey(), error.getClass().getSimpleName());
            throw new DynamicSwitchException(503, "REDIS_UNAVAILABLE",
                    "Disable request is persisted and awaiting reconciliation", error);
        }
    }

    private ChangeRow requireChange(String changeId) {
        return repository.findByChangeId(changeId)
                .orElseThrow(() -> new DynamicSwitchException(404, "CHANGE_NOT_FOUND", "Dynamic switch change not found"));
    }

    private void requireDetailCleanupReady() {
        if (detailCleanupGate == null) return;
        final ReadinessEvaluation evaluation;
        try { evaluation=detailCleanupGate.evaluateAndConverge(); }
        catch (DataRetentionDetailCleanupException error) {
            throw new DynamicSwitchException(503,error.code(),"WP8 fail-closed convergence did not reach a trusted terminal state",error);
        }
        if (evaluation.ready()) return;
        int status = DataRetentionDetailCleanupPromotionGate.infrastructure(evaluation.primaryCode()) ? 503 : 409;
        throw new DynamicSwitchException(status, evaluation.primaryCode().name(),
                "WP8 detail cleanup readiness blocks dynamic enable");
    }

    private Instant readRedisTimeForEnable() {
        try {
            return redisStore.redisTime();
        } catch (DynamicSwitchException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DynamicSwitchException(503, "REDIS_UNAVAILABLE", "Redis TIME is unavailable", e);
        }
    }

    private Snapshot readBestEffort(SwitchKey key) {
        try {
            return redisStore.read(key);
        } catch (RuntimeException error) {
            alertSink.alert("REDIS_UNAVAILABLE", key, error.getClass().getSimpleName());
            return Snapshot.unavailable(key, error);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    static RequestedSubstate substate(ChangeRow row, Instant redisNow) {
        if (row.status() != ChangeStatus.REQUESTED) return RequestedSubstate.TERMINAL;
        if (!row.enabled()) return RequestedSubstate.PENDING_DISABLE;
        if (row.approvedAt() == null) return RequestedSubstate.AWAITING_APPROVAL;
        if (redisNow != null && redisNow.isAfter(row.approvedAt().plusMillis(
                DataRetentionDynamicSwitchModels.ENABLE_CAS_GRACE_MILLIS))) return RequestedSubstate.EXPIRED_ENABLE;
        return RequestedSubstate.APPROVED_ENABLE;
    }

    private ChangeView view(ChangeRow row, Snapshot snapshot, String resultCode) {
        return new ChangeView(row.changeId(), row.switchKey(), row.version(), row.enabled(), row.status(),
                substate(row, null), snapshot, row.appliedAt(), row.failedAt(), resultCode);
    }

    static String divergence(ChangeRow latest, Snapshot snapshot) {
        if (snapshot == null || snapshot.integrity() == SnapshotIntegrity.UNAVAILABLE) return "REDIS_UNAVAILABLE";
        if (snapshot.integrity() == SnapshotIntegrity.MALFORMED) return "MALFORMED_CURRENT";
        if (latest == null) return snapshot.integrity() == SnapshotIntegrity.MISSING ? "NONE" : "REDIS_AHEAD_OF_AUDIT";
        long runtimeVersion = snapshot.integrity() == SnapshotIntegrity.VALID ? snapshot.payload().version() : 0L;
        if (runtimeVersion < latest.version()) return "VERSION_ROLLBACK";
        if (runtimeVersion > latest.version()) return "REDIS_AHEAD_OF_AUDIT";
        if (snapshot.integrity() == SnapshotIntegrity.VALID && !snapshot.payload().equals(latest.payload())) {
            return "SAME_VERSION_DIFFERENT_PAYLOAD";
        }
        if (latest.status() == ChangeStatus.REQUESTED) return "REQUEST_IN_FLIGHT";
        if (latest.status() == ChangeStatus.FAILED) return "FAILED_TARGET";
        return "NONE";
    }

    public record ManualChangeCommand(String changeId, boolean targetEnabled, String reason,
                                      String approvalRef, String ticketRef) {
    }

    public record DisableCommand(String changeId, String reason, String ticketRef) {
    }

    public record ChangeView(String changeId, SwitchKey switchKey, long version, boolean targetEnabled,
                             ChangeStatus status, RequestedSubstate substate, Snapshot observed,
                             Instant appliedAt, Instant failedAt, String resultCode) {
    }

    public record SwitchView(SwitchKey switchKey, Snapshot runtime, ChangeView latestAudit, String divergence) {
    }
}
