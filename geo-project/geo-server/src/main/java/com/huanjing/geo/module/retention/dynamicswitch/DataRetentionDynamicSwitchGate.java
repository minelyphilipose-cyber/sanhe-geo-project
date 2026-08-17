package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Decision;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DecisionCode;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DynamicSwitchException;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Snapshot;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SnapshotIntegrity;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SnapshotPair;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public interface DataRetentionDynamicSwitchGate {
    Decision check(RetentionDomain domain);
    Decision requireEnabled(RetentionDomain domain);

    @Service
    final class RedisGate implements DataRetentionDynamicSwitchGate {
        private final DataRetentionDynamicSwitchRedisStore redisStore;
        private final DataRetentionDynamicSwitchAlertSink alertSink;
        private final DataRetentionDynamicSwitchMetrics metrics;
        private final Clock clock;

        @Autowired
        public RedisGate(DataRetentionDynamicSwitchRedisStore redisStore,
                         DataRetentionDynamicSwitchAlertSink alertSink,
                         DataRetentionDynamicSwitchMetrics metrics) {
            this(redisStore, alertSink, metrics, Clock.systemUTC());
        }

        RedisGate(DataRetentionDynamicSwitchRedisStore redisStore,
                  DataRetentionDynamicSwitchAlertSink alertSink,
                  DataRetentionDynamicSwitchMetrics metrics,
                  Clock clock) {
            this.redisStore = redisStore;
            this.alertSink = alertSink;
            this.metrics = metrics;
            this.clock = clock;
        }

        @Override
        public Decision check(RetentionDomain domain) {
            Instant checkedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            if (domain == null) {
                metrics.gate(null, DecisionCode.UNSUPPORTED_DOMAIN);
                return new Decision(null, false, DecisionCode.UNSUPPORTED_DOMAIN, checkedAt, null, null);
            }
            SwitchKey domainKey = SwitchKey.forDomain(domain);
            final SnapshotPair pair;
            try {
                pair = redisStore.readPair(domainKey);
            } catch (RuntimeException error) {
                alertSink.alert("REDIS_UNAVAILABLE", domainKey, error.getClass().getSimpleName());
                Snapshot global = Snapshot.unavailable(SwitchKey.GLOBAL_EXECUTE, error);
                Snapshot domainSnapshot = Snapshot.unavailable(domainKey, error);
                metrics.gate(domain, DecisionCode.REDIS_UNAVAILABLE);
                return new Decision(domain, false, DecisionCode.REDIS_UNAVAILABLE, checkedAt, global, domainSnapshot);
            }
            if (pair.global().integrity() == SnapshotIntegrity.MALFORMED) {
                alertSink.alert("MALFORMED_GLOBAL_STATE", SwitchKey.GLOBAL_EXECUTE, "invalid Redis HASH v1");
            }
            if (pair.domain().integrity() == SnapshotIntegrity.MALFORMED) {
                alertSink.alert("MALFORMED_DOMAIN_STATE", domainKey, "invalid Redis HASH v1");
            }
            DecisionCode code = decide(pair);
            metrics.gate(domain, code);
            return new Decision(domain, code == DecisionCode.ENABLED, code, checkedAt, pair.global(), pair.domain());
        }

        @Override
        public Decision requireEnabled(RetentionDomain domain) {
            Decision decision = check(domain);
            if (!decision.enabled()) {
                throw new DynamicSwitchException(409, decision.code().name(),
                        "Data retention dynamic switch rejected execution: " + decision.code());
            }
            return decision;
        }

        static DecisionCode decide(SnapshotPair pair) {
            if (pair.global().integrity() == SnapshotIntegrity.UNAVAILABLE
                    || pair.domain().integrity() == SnapshotIntegrity.UNAVAILABLE) return DecisionCode.REDIS_UNAVAILABLE;
            if (pair.global().integrity() == SnapshotIntegrity.MALFORMED) return DecisionCode.MALFORMED_GLOBAL_STATE;
            if (pair.domain().integrity() == SnapshotIntegrity.MALFORMED) return DecisionCode.MALFORMED_DOMAIN_STATE;
            if (!pair.global().enabled()) return DecisionCode.GLOBAL_DISABLED;
            if (!pair.domain().enabled()) return DecisionCode.DOMAIN_DISABLED;
            return DecisionCode.ENABLED;
        }
    }
}
