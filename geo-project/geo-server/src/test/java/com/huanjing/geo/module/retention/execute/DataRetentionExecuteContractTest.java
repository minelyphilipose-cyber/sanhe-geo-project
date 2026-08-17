package com.huanjing.geo.module.retention.execute;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRetentionExecuteContractTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void safetyDefaultsRemainFalseAndExecuteRoutesUseCoordinator() throws Exception {
        String yaml = Files.readString(ROOT.resolve("src/main/resources/application.yml"));
        assertTrue(yaml.contains("GEO_RETENTION_SCHEDULER_ENABLED:false"));
        assertTrue(yaml.contains("GEO_RETENTION_SCHEDULER_EXECUTE_ENABLED:false"));
        assertTrue(yaml.contains("GEO_RETENTION_WEBSITE_PUBLISHED_CLEANUP_EXECUTE_ENABLED:false"));
        assertTrue(yaml.contains("GEO_RETENTION_POLL_RESULTS_EXECUTE_ENABLED:false"));
        assertTrue(yaml.contains("GEO_RETENTION_DYNAMIC_SWITCH_ENABLE_WRITE_ENABLED:false"));

        String controller = Files.readString(ROOT.resolve("src/main/java/com/huanjing/geo/module/retention/controller/DataRetentionController.java"));
        assertTrue(controller.contains("executeCoordinator.execute"));
        assertFalse(controller.contains("websitePublishedContentCleanupService.cleanup("));
        assertFalse(controller.contains("pollRetentionDryRunService.purge("));

        String scheduler = Files.readString(ROOT.resolve("src/main/java/com/huanjing/geo/module/retention/schedule/DataRetentionScheduler.java"));
        assertTrue(scheduler.contains("runExecuteSuite"));
        assertTrue(scheduler.contains("executeCoordinator.execute"));
        assertTrue(scheduler.contains("findRecoverableSchedulerRuns"));
        assertTrue(scheduler.contains("findRecoverableDeferredRetryRuns"));
        assertTrue(scheduler.contains("findDeferredRetryableOperations"));
        assertTrue(scheduler.indexOf("findRecoverableSchedulerRuns")
                < scheduler.indexOf("findRecoverableDeferredRetryRuns"));
        assertTrue(scheduler.indexOf("findRecoverableDeferredRetryRuns")
                < scheduler.indexOf("findDeferredRetryableOperations"));
        assertTrue(scheduler.indexOf("recoverDomainSafely(\"website_published_content\")")
                < scheduler.indexOf("newTriggerId()"));
        assertTrue(scheduler.indexOf("recoverDomainSafely(\"poll_results\")")
                < scheduler.indexOf("newTriggerId()"));
    }

    @Test
    void auditOnlyReconcileDoesNotAcquireExecuteLeaseOrGate() throws Exception {
        String coordinator = Files.readString(ROOT.resolve(
                "src/main/java/com/huanjing/geo/module/retention/execute/DataRetentionExecuteCoordinator.java"));
        int start = coordinator.indexOf("public ReconciliationResult reconcile");
        int end = coordinator.indexOf("private RetentionExecutionResult resume", start);
        String method = coordinator.substring(start, end);
        assertTrue(method.contains("requireReconcileOperator"));
        assertTrue(method.contains("recordAuditOnlyReconciliation"));
        assertFalse(method.contains("tryAcquire"));
        assertFalse(method.contains("requireEnabled"));
        assertFalse(method.contains("inMutationTransaction"));
    }

    @Test
    void trustedRecoveryReadRejectsTerminalMutationWithoutUsage() throws Exception {
        String audit = Files.readString(ROOT.resolve(
                "src/main/java/com/huanjing/geo/module/retention/audit/service/DataRetentionP0AuditService.java"));
        assertTrue(audit.contains("terminal mutation attempt is missing budget usage"));
        assertTrue(audit.contains("findRecoverableDeferredRetryRuns"));
        assertTrue(audit.contains("multiple running deferred retry runs reference one operation"));
    }

    @Test
    void permissionMigrationIsExactAndIdempotent() throws Exception {
        String migration = Files.readString(ROOT.resolve(
                "src/main/resources/db/migration/V364__data_retention_execute_permissions.sql"));
        for (String key : new String[]{"manual", "scheduler", "canary", "retry", "reconcile"}) {
            assertTrue(migration.contains("data.retention.execute." + key));
        }
        assertTrue(migration.contains("NOT EXISTS"));
        assertTrue(migration.contains("role_type = 'internal'"));
        assertTrue(migration.contains("V364 execute role grant mismatch"));
    }

    @Test
    void requiredCoordinatorProfileCannotSilentlySkipContainers() throws Exception {
        String pom = Files.readString(ROOT.resolve("pom.xml"));
        assertTrue(pom.contains("<id>retention-coordinator-it</id>"));
        assertTrue(pom.contains("<retention.mysql-it.required>true</retention.mysql-it.required>"));
        assertTrue(pom.contains("<retention.switch-it.required>true</retention.switch-it.required>"));
        assertTrue(pom.contains("<retention.coordinator-it.required>true</retention.coordinator-it.required>"));
        assertTrue(pom.contains("DataRetentionExecuteCoordinatorMysqlRedisIntegrationTest.java"));
        assertTrue(pom.contains("DataRetentionExecutePermissionMigrationMysqlIntegrationTest.java"));
        assertTrue(pom.contains("<failIfNoTests>true</failIfNoTests>"));
    }
}
