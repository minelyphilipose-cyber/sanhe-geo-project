package com.huanjing.geo.module.retention.website;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebsiteRetentionArchitectureTest {

    @Test
    void executeEntryPointsUseCoordinatorAndDryRunHasNoLegacyMutationPath() throws Exception {
        String controller = source("controller/DataRetentionController.java");
        String scheduler = source("schedule/DataRetentionScheduler.java");
        String dryRun = source("service/WebsitePublishedContentCleanupService.java");

        assertTrue(controller.contains("executeCoordinator.execute(executeCommandFactory.manualWebsite"));
        assertFalse(controller.contains("websitePublishedContentCleanupService.cleanup("));
        assertTrue(scheduler.contains("executeCoordinator.execute(executeCommandFactory.schedulerWebsite"));
        assertTrue(scheduler.contains("websitePublishedContentCleanupService.runScheduledDryRun("));
        assertTrue(dryRun.contains("DataRetentionP0AuditService audit"));
        assertTrue(dryRun.contains("createOperationlessAttempt"));
        assertFalse(dryRun.contains("DataRetentionRunAuditService"));
        assertFalse(dryRun.contains("TransactionTemplate"));
        assertFalse(dryRun.contains("JdbcTemplate"));
        assertFalse(dryRun.contains(" cleanup("));
        assertFalse(dryRun.contains("runScheduled("));
        assertFalse(dryRun.contains("UPDATE "));
    }

    @Test
    void mutationRepositoryIsOwnedOnlyByWebsiteExecutor() throws Exception {
        Path root = Path.of("src/main/java/com/huanjing/geo/module/retention");
        try (var files = Files.walk(root)) {
            var owners = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("WebsiteRetentionBusinessRepository business");
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    })
                    .toList();
            assertTrue(owners.size() == 1 && owners.get(0).endsWith("WebsiteRetentionDomainExecutor.java"));
        }
    }

    @Test
    void dryRunAndExecuteShareTheEligibilitySqlContract() throws Exception {
        String business = source("website/WebsiteRetentionBusinessRepository.java");
        String reader = source("website/WebsiteRetentionDryRunReader.java");

        assertTrue(business.contains("WebsiteRetentionEligibilitySql.candidateIdsSql()"));
        assertTrue(reader.contains("WebsiteRetentionEligibilitySql.candidateWhereSql(\"a\")"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/huanjing/geo/module/retention").resolve(relative));
    }
}
