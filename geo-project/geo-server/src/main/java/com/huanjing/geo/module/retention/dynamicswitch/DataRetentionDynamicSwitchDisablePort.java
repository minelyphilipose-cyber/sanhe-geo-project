package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository.ChangeRow;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository.CreateCommand;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.CasResult;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.CasResultCode;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DynamicSwitchException;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RequestSource;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Snapshot;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SnapshotIntegrity;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public interface DataRetentionDynamicSwitchDisablePort {
    ChangeRow disable(SwitchKey switchKey, String changeId, long operatorUserId,
                      RequestSource source, String reason, String ticketRef);
    ChangeRow resume(ChangeRow requestedDisable);

    @Service
    final class SafeDisableService implements DataRetentionDynamicSwitchDisablePort {
        private final DataRetentionDynamicSwitchChangeRepository repository;
        private final DataRetentionDynamicSwitchRedisStore redisStore;
        private final DataRetentionDynamicSwitchAuthorizationPolicy authorization;
        private final DataRetentionDynamicSwitchAlertSink alertSink;
        private final DataRetentionProperties properties;
        private final DataRetentionDynamicSwitchMetrics metrics;
        private final Clock clock;

        @Autowired
        public SafeDisableService(DataRetentionDynamicSwitchChangeRepository repository,
                                  DataRetentionDynamicSwitchRedisStore redisStore,
                                  DataRetentionDynamicSwitchAuthorizationPolicy authorization,
                                  DataRetentionDynamicSwitchAlertSink alertSink,
                                  DataRetentionProperties properties,
                                  DataRetentionDynamicSwitchMetrics metrics) {
            this(repository, redisStore, authorization, alertSink, properties, metrics, Clock.systemUTC());
        }

        SafeDisableService(DataRetentionDynamicSwitchChangeRepository repository,
                           DataRetentionDynamicSwitchRedisStore redisStore,
                           DataRetentionDynamicSwitchAuthorizationPolicy authorization,
                           DataRetentionDynamicSwitchAlertSink alertSink,
                           DataRetentionProperties properties,
                           DataRetentionDynamicSwitchMetrics metrics,
                           Clock clock) {
            this.repository = repository;
            this.redisStore = redisStore;
            this.authorization = authorization;
            this.alertSink = alertSink;
            this.properties = properties;
            this.metrics = metrics;
            this.clock = clock;
        }

        @Override
        public ChangeRow disable(SwitchKey switchKey, String changeId, long operatorUserId,
                                 RequestSource source, String reason, String ticketRef) {
            if (source != RequestSource.RECONCILE) {
                throw new IllegalArgumentException("typed internal port only accepts reconcile source");
            }
            authorization.validateReconcileOperator(operatorUserId);
            Snapshot observed = readBestEffort(switchKey);
            Long observedVersion = observed.integrity() == SnapshotIntegrity.VALID
                    ? observed.payload().version() : null;
            ChangeRow row = repository.createRequested(new CreateCommand(changeId, switchKey, false, now(),
                    operatorUserId, source, reason, null, ticketRef), observedVersion);
            return resume(row);
        }

        @Override
        public ChangeRow resume(ChangeRow requestedDisable) {
            if (requestedDisable.enabled()) throw new IllegalArgumentException("disable row required");
            if (requestedDisable.terminal()) return requestedDisable;
            try {
                CasResult result = redisStore.disableCas(requestedDisable.switchKey(), requestedDisable.payload());
                metrics.cas(requestedDisable.switchKey(), false, result.code());
                if (result.exactSuccess()) {
                    return repository.finalizeApplied(requestedDisable, result.observed().payload(), now());
                }
                if (result.code() == CasResultCode.STALE_TARGET) {
                    ChangeRow replacement = compensateStale(requestedDisable, result.observed());
                    return replacement.id() == requestedDisable.id() ? replacement : resume(replacement);
                }
                repository.recordUncertain(requestedDisable, result.observed().payload(), result.code().name().toLowerCase());
                if (result.code() == CasResultCode.MALFORMED_CURRENT
                        || result.code() == CasResultCode.SAME_VERSION_DIFFERENT_PAYLOAD) {
                    alertSink.alert(result.code().name(), requestedDisable.switchKey(), "manual repair required");
                }
                return requestedDisable;
            } catch (DynamicSwitchException error) {
                if ("MALFORMED_CURRENT".equals(error.resultCode())
                        || "SAME_VERSION_DIFFERENT_PAYLOAD".equals(error.resultCode())) {
                    throw error;
                }
                repository.recordUncertain(requestedDisable, null,
                        "redis_unavailable:" + error.getClass().getSimpleName());
                alertSink.alert("REDIS_UNAVAILABLE", requestedDisable.switchKey(), error.getClass().getSimpleName());
                throw new DynamicSwitchException(503, "REDIS_UNAVAILABLE",
                        "Disable request remains requested for reconciliation", error);
            } catch (RuntimeException error) {
                repository.recordUncertain(requestedDisable, null,
                        "redis_unavailable:" + error.getClass().getSimpleName());
                alertSink.alert("REDIS_UNAVAILABLE", requestedDisable.switchKey(), error.getClass().getSimpleName());
                throw new DynamicSwitchException(503, "REDIS_UNAVAILABLE",
                        "Disable request remains requested for reconciliation", error);
            }
        }

        private ChangeRow compensateStale(ChangeRow stale, Snapshot observed) {
            if (observed.integrity() != SnapshotIntegrity.VALID || observed.payload() == null) {
                repository.recordUncertain(stale, null, "stale_without_trusted_observation");
                return stale;
            }
            long operatorId = properties.getDynamicSwitch().getReconcileOperatorUserId();
            authorization.validateReconcileOperator(operatorId);
            String compensationId = java.util.UUID.nameUUIDFromBytes(("retention-switch-v1:stale:"
                    + stale.switchKey().databaseValue() + ":" + stale.version() + ":" + observed.payload().version())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            CreateCommand compensation = new CreateCommand(compensationId, stale.switchKey(), false, now(),
                    operatorId, RequestSource.RECONCILE, "stale disable compensation", null, stale.ticketRef());
            ChangeRow replacement = repository.replaceStaleDisableAtomically(
                    stale, observed.payload(), compensation);
            alertSink.alert("STALE_DISABLE_COMPENSATED", stale.switchKey(), "higher Redis version observed");
            return replacement;
        }

        private Snapshot readBestEffort(SwitchKey key) {
            try {
                return redisStore.read(key);
            } catch (RuntimeException error) {
                return Snapshot.unavailable(key, error);
            }
        }

        private Instant now() {
            return clock.instant().truncatedTo(ChronoUnit.MILLIS);
        }
    }
}
