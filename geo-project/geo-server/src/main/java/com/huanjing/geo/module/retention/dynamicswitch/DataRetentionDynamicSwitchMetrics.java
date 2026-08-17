package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.CasResultCode;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DecisionCode;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RequestSource;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.DistributionSummary;
import org.springframework.stereotype.Component;

@Component
public class DataRetentionDynamicSwitchMetrics {

    private final MeterRegistry registry;
    private final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.atomic.AtomicLong> gauges =
            new java.util.concurrent.ConcurrentHashMap<>();

    public DataRetentionDynamicSwitchMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void gate(RetentionDomain domain, DecisionCode decision) {
        registry.counter("retention_dynamic_switch_gate_total",
                "domain", domain == null ? "unsupported" : domain.databaseValue(),
                "decision", decision.name().toLowerCase()).increment();
    }

    public void cas(SwitchKey key, boolean target, CasResultCode result) {
        registry.counter("retention_dynamic_switch_cas_total",
                "switch_key", key.databaseValue(), "target", Boolean.toString(target),
                "result", result.name().toLowerCase()).increment();
    }

    public void change(SwitchKey key, boolean target, String status, RequestSource source) {
        registry.counter("retention_dynamic_switch_change_total",
                "switch_key", key.databaseValue(), "target", Boolean.toString(target),
                "status", status, "source", source.databaseValue()).increment();
    }

    public void divergence(SwitchKey key, String type) {
        registry.counter("retention_dynamic_switch_divergence_total",
                "switch_key", key.databaseValue(), "type", type).increment();
    }

    public void reconcile(String action, String result) {
        registry.counter("retention_dynamic_switch_reconcile_total",
                "action", action, "result", result).increment();
    }

    public void redisLatency(String operation, long elapsedNanos) {
        DistributionSummary.builder("retention_dynamic_switch_redis_latency_ms")
                .baseUnit("milliseconds")
                .tag("operation", operation)
                .register(registry)
                .record(elapsedNanos / 1_000_000.0d);
    }

    public void alert(String code, SwitchKey key) {
        registry.counter("retention_dynamic_switch_alert_total",
                "code", code.toLowerCase(), "switch_key", key == null ? "none" : key.databaseValue()).increment();
    }

    public void requestedBacklog(java.util.List<DataRetentionDynamicSwitchChangeRepository.RequestedBacklog> rows,
                                 java.time.Instant now) {
        java.util.Map<String, DataRetentionDynamicSwitchChangeRepository.RequestedBacklog> values = new java.util.HashMap<>();
        for (var row : rows) values.put(gaugeKey(row.switchKey(), row.enabled()), row);
        for (SwitchKey key : SwitchKey.values()) {
            for (boolean enabled : new boolean[]{false, true}) {
                String identity = gaugeKey(key, enabled);
                var row = values.get(identity);
                setGauge("retention_dynamic_switch_requested_count", identity, key, enabled,
                        row == null ? 0 : row.count());
                long oldestAge = row == null ? 0 : Math.max(0, java.time.Duration.between(row.oldestAt(), now).toSeconds());
                setGauge("retention_dynamic_switch_requested_oldest_age_seconds", identity, key, enabled, oldestAge);
            }
        }
    }

    private void setGauge(String metric, String identity, SwitchKey key, boolean enabled, long value) {
        String mapKey = metric + ':' + identity;
        java.util.concurrent.atomic.AtomicLong holder = gauges.computeIfAbsent(mapKey, ignored -> {
            java.util.concurrent.atomic.AtomicLong created = new java.util.concurrent.atomic.AtomicLong();
            Gauge.builder(metric, created, java.util.concurrent.atomic.AtomicLong::get)
                    .tag("switch_key", key.databaseValue()).tag("target", Boolean.toString(enabled))
                    .register(registry);
            return created;
        });
        holder.set(value);
    }

    private static String gaugeKey(SwitchKey key, boolean enabled) {
        return key.databaseValue() + ':' + enabled;
    }
}
