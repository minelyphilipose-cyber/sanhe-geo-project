package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository.RequestedBacklog;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import com.huanjing.geo.module.system.service.SystemAlertService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DataRetentionDynamicSwitchObservabilityTest {

    @Test
    void redisLatencyAndRequestedBacklogMetricsAreRecorded() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DataRetentionDynamicSwitchMetrics metrics = new DataRetentionDynamicSwitchMetrics(registry);
        Instant now = Instant.parse("2026-08-13T03:00:00Z");

        metrics.redisLatency("read_pair", 2_000_000L);
        metrics.requestedBacklog(List.of(new RequestedBacklog(
                SwitchKey.POLL_RESULTS_EXECUTE, false, 3,
                now.minusSeconds(75))), now);

        assertEquals(1, registry.get("retention_dynamic_switch_redis_latency_ms")
                .tag("operation", "read_pair").summary().count());
        assertEquals(2.0, registry.get("retention_dynamic_switch_redis_latency_ms")
                .tag("operation", "read_pair").summary().totalAmount());
        assertEquals(3.0, registry.get("retention_dynamic_switch_requested_count")
                .tag("switch_key", "poll_results_execute").tag("target", "false").gauge().value());
        assertEquals(75.0, registry.get("retention_dynamic_switch_requested_oldest_age_seconds")
                .tag("switch_key", "poll_results_execute").tag("target", "false").gauge().value());
        assertEquals(0.0, registry.get("retention_dynamic_switch_requested_count")
                .tag("switch_key", "global_execute").tag("target", "true").gauge().value());
    }

    @Test
    void alertSinkPersistsSystemAlertAndIncrementsMetric() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DataRetentionDynamicSwitchMetrics metrics = new DataRetentionDynamicSwitchMetrics(registry);
        SystemAlertService systemAlerts = mock(SystemAlertService.class);
        var sink = new DataRetentionDynamicSwitchAlertSink.LoggingAlertSink(metrics, systemAlerts);

        sink.alert("VERSION_ROLLBACK", SwitchKey.POLL_RESULTS_EXECUTE, "audit version is newer");

        verify(systemAlerts).createAlert(
                eq("DATA_RETENTION_DYNAMIC_SWITCH"), eq("error"),
                eq("data_retention_dynamic_switch"), eq("VERSION_ROLLBACK"),
                eq(Map.of("code", "VERSION_ROLLBACK", "switchKey", "poll_results_execute",
                        "detail", "audit version is newer")));
        assertEquals(1.0, registry.get("retention_dynamic_switch_alert_total")
                .tag("code", "version_rollback").tag("switch_key", "poll_results_execute")
                .counter().count());
    }
}
