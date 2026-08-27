package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository.RequestedBacklog;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DynamicSwitchException;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import com.huanjing.geo.module.system.service.SystemAlertService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void alertSinkRefreshesOneStableSystemAlertAndCanResolveIt() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DataRetentionDynamicSwitchMetrics metrics = new DataRetentionDynamicSwitchMetrics(registry);
        SystemAlertService systemAlerts = mock(SystemAlertService.class);
        var sink = new DataRetentionDynamicSwitchAlertSink.LoggingAlertSink(metrics, systemAlerts);

        sink.alert("VERSION_ROLLBACK", SwitchKey.POLL_RESULTS_EXECUTE, "audit version is newer");
        sink.alert("VERSION_ROLLBACK", SwitchKey.POLL_RESULTS_EXECUTE, "audit version is newer");
        sink.resolve("VERSION_ROLLBACK", SwitchKey.POLL_RESULTS_EXECUTE);

        verify(systemAlerts, times(2)).createOrRefreshRecipientAlert(
                eq("DATA_RETENTION_DYNAMIC_SWITCH"), eq("error"),
                eq("data_retention_dynamic_switch"), eq("VERSION_ROLLBACK"),
                eq(Map.of("code", "VERSION_ROLLBACK", "switchKey", "poll_results_execute",
                        "detail", "audit version is newer")),
                isNull(), eq("super_admin"),
                eq("data_retention_dynamic_switch:version_rollback:poll_results_execute"));
        verify(systemAlerts).resolveOpenByDedupeKey(
                eq("data_retention_dynamic_switch:version_rollback:poll_results_execute"), isNull());
        assertEquals(2.0, registry.get("retention_dynamic_switch_alert_total")
                .tag("code", "version_rollback").tag("switch_key", "poll_results_execute")
                .counter().count());
    }

    @Test
    void reconcilerPreservesSpecificOperatorFailureCode() {
        DataRetentionDynamicSwitchAuthorizationPolicy authorization =
                mock(DataRetentionDynamicSwitchAuthorizationPolicy.class);
        DataRetentionDynamicSwitchAlertSink alerts = mock(DataRetentionDynamicSwitchAlertSink.class);
        DataRetentionProperties properties = properties(2L);
        doThrow(new DynamicSwitchException(
                503, "RECONCILE_OPERATOR_MISSING", "operator is not configured"))
                .when(authorization).validateReconcileOperator(anyLong());
        DataRetentionDynamicSwitchReconciler reconciler = reconciler(authorization, alerts, properties);

        reconciler.reconcile();

        verify(alerts).resolve("RECONCILE_OPERATOR_INVALID", null);
        verify(alerts).alert(eq("RECONCILE_OPERATOR_MISSING"), isNull(), eq("DynamicSwitchException"));
        verify(alerts, never()).resolve(eq("RECONCILE_OPERATOR_MISSING"), isNull());
    }

    @Test
    void reconcilerResolvesOperatorAlertsAfterSuccessfulValidation() {
        DataRetentionDynamicSwitchAuthorizationPolicy authorization =
                mock(DataRetentionDynamicSwitchAuthorizationPolicy.class);
        DataRetentionDynamicSwitchAlertSink alerts = mock(DataRetentionDynamicSwitchAlertSink.class);
        DataRetentionProperties properties = properties(2L);
        DataRetentionDynamicSwitchReconciler reconciler = reconciler(authorization, alerts, properties);

        reconciler.reconcile();

        verify(authorization).validateReconcileOperator(2L);
        verify(alerts).resolve("RECONCILE_OPERATOR_MISSING", null);
        verify(alerts).resolve("RECONCILE_OPERATOR_INVALID", null);
    }

    private DataRetentionDynamicSwitchReconciler reconciler(
            DataRetentionDynamicSwitchAuthorizationPolicy authorization,
            DataRetentionDynamicSwitchAlertSink alerts,
            DataRetentionProperties properties) {
        return new DataRetentionDynamicSwitchReconciler(
                mock(DataRetentionDynamicSwitchChangeRepository.class),
                mock(DataRetentionDynamicSwitchRedisStore.class),
                mock(DataRetentionDynamicSwitchDisablePort.class),
                authorization,
                alerts,
                properties,
                mock(DataRetentionDynamicSwitchMetrics.class));
    }

    private DataRetentionProperties properties(long operatorId) {
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.getDynamicSwitch().setReconcileOperatorUserId(operatorId);
        return properties;
    }
}
