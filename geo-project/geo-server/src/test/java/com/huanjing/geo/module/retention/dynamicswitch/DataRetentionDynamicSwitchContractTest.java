package com.huanjing.geo.module.retention.dynamicswitch;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRetentionDynamicSwitchContractTest {

    private static final Path MAIN = Path.of("src/main");

    @Test
    void luaScriptsPreserveProtocolSafety() throws Exception {
        String read = read("resources/redis/data-retention-dynamic-switch-read-pair-v1.lua");
        String enable = read("resources/redis/data-retention-dynamic-switch-enable-cas-v1.lua");
        String disable = read("resources/redis/data-retention-dynamic-switch-disable-cas-v1.lua");
        assertFalse(read.toUpperCase().contains("HSET"));
        assertFalse(read.toUpperCase().contains("SET "));
        assertFalse(enable.contains("tonumber(ARGV[2])"));
        assertFalse(disable.contains("tonumber(ARGV[2])"));
        assertFalse(enable.toUpperCase().contains("REDIS.CALL('EXPIRE'"));
        assertFalse(disable.toUpperCase().contains("REDIS.CALL('EXPIRE'"));
        assertTrue(enable.contains("redis.call('TIME')"));
        assertTrue(enable.contains("ENABLE_CAS_GRACE_MILLIS = 60000"));
        assertEquals(1, count(enable, "redis.call('HSET'"));
        assertEquals(1, count(disable, "redis.call('HSET'"));
    }

    @Test
    void permissionMigrationHasExactGrantMatrix() throws Exception {
        String migration = read("resources/db/migration/V359__data_retention_dynamic_switch_permissions.sql");
        assertEquals(6, count(migration, "SELECT 'data.retention.switch."));
        assertTrue(migration.contains("COUNT(*) = 11"));
        assertTrue(migration.contains("r.role_key NOT IN ('super_admin', 'manager', 'delivery_manager')"));
        assertFalse(migration.contains("LIKE 'data.retention.switch.%'"));
        assertTrue(migration.contains("SIGNAL SQLSTATE '45000'"));
        assertFalse(migration.toUpperCase().contains("INSERT IGNORE"));
    }

    @Test
    void defaultsStayFailClosedAndReconcilerCannotWriteEnable() throws Exception {
        String yaml = read("resources/application.yml");
        assertTrue(yaml.contains("GEO_RETENTION_DYNAMIC_SWITCH_ENABLE_WRITE_ENABLED:false"));
        assertTrue(yaml.contains("GEO_RETENTION_SCHEDULER_EXECUTE_ENABLED:false"));
        assertTrue(yaml.contains("GEO_RETENTION_WEBSITE_PUBLISHED_CLEANUP_EXECUTE_ENABLED:false"));
        assertTrue(yaml.contains("GEO_RETENTION_POLL_RESULTS_EXECUTE_ENABLED:false"));

        String reconciler = read("java/com/huanjing/geo/module/retention/dynamicswitch/DataRetentionDynamicSwitchReconciler.java");
        assertFalse(reconciler.contains("enableCas("));
        assertFalse(reconciler.contains("DataRetentionDynamicSwitchManagementService"));
        String repository = read("java/com/huanjing/geo/module/retention/dynamicswitch/DataRetentionDynamicSwitchChangeRepository.java");
        assertFalse(repository.contains("StringRedisTemplate"));
        String redisStore = read("java/com/huanjing/geo/module/retention/dynamicswitch/DataRetentionDynamicSwitchRedisStore.java");
        assertFalse(redisStore.contains("JdbcTemplate"));
    }

    @Test
    void wp4DoesNotWireExistingCleanupPaths() throws Exception {
        String controller = read("java/com/huanjing/geo/module/retention/controller/DataRetentionController.java");
        String scheduler = read("java/com/huanjing/geo/module/retention/schedule/DataRetentionScheduler.java");
        assertFalse(controller.contains("DataRetentionDynamicSwitchGate"));
        assertFalse(scheduler.contains("DataRetentionDynamicSwitchGate"));
    }

    @Test
    void observabilityContractIncludesLatencyBacklogAndPersistentAlerts() throws Exception {
        String metrics = read("java/com/huanjing/geo/module/retention/dynamicswitch/DataRetentionDynamicSwitchMetrics.java");
        String alert = read("java/com/huanjing/geo/module/retention/dynamicswitch/DataRetentionDynamicSwitchAlertSink.java");
        assertTrue(metrics.contains("retention_dynamic_switch_redis_latency_ms"));
        assertTrue(metrics.contains("retention_dynamic_switch_requested_count"));
        assertTrue(metrics.contains("retention_dynamic_switch_requested_oldest_age_seconds"));
        assertTrue(alert.contains("systemAlertService.createAlert"));
        assertTrue(alert.contains("DATA_RETENTION_DYNAMIC_SWITCH"));
    }

    @Test
    void reconciliationGuardsCannotRegress() throws Exception {
        String management = read("java/com/huanjing/geo/module/retention/dynamicswitch/DataRetentionDynamicSwitchManagementService.java");
        String repository = read("java/com/huanjing/geo/module/retention/dynamicswitch/DataRetentionDynamicSwitchChangeRepository.java");
        String authorization = read("java/com/huanjing/geo/module/retention/dynamicswitch/DataRetentionDynamicSwitchAuthorizationPolicy.java");
        String reconciler = read("java/com/huanjing/geo/module/retention/dynamicswitch/DataRetentionDynamicSwitchReconciler.java");
        assertTrue(management.contains("observed.integrity() == SnapshotIntegrity.UNAVAILABLE"));
        assertTrue(management.contains("observed.integrity() == SnapshotIntegrity.MALFORMED"));
        String requestedReconcile = management.substring(management.indexOf("ChangeView reconcileRequested"));
        assertTrue(requestedReconcile.indexOf("observed.integrity() == SnapshotIntegrity.MALFORMED")
                < requestedReconcile.indexOf("repository.expireApprovedEnable"));
        assertTrue(repository.contains("replaceStaleDisableAtomically"));
        assertTrue(repository.contains("last_error = 'stale_target'"));
        assertTrue(authorization.contains("JOIN sys_role r ON r.id = ur.role_id"));
        assertTrue(authorization.contains("r.status = 'active'"));
        assertTrue(authorization.contains("r.role_type = 'internal'"));
        assertTrue(reconciler.contains("repository.recordUncertain(row, null, \"redis_unavailable:"));
        assertTrue(reconciler.contains("alertSink.alert(\"REDIS_UNAVAILABLE\""));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }

    private static int count(String source, String needle) {
        int count = 0;
        for (int at = 0; (at = source.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }
}
