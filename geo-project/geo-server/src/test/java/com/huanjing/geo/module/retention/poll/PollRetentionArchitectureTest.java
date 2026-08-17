package com.huanjing.geo.module.retention.poll;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollRetentionArchitectureTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    void typedDryRunCannotReachBusinessMutationOrWriteSliceFence() throws Exception {
        String source = read("src/main/java/com/huanjing/geo/module/retention/service/PollRetentionDryRunService.java");
        assertTrue(source.contains("createOperationlessAttempt"));
        assertTrue(source.contains("AttemptType.PAGE"));
        assertFalse(source.contains("DELETE "));
        assertFalse(source.contains("PollAuditPurgeService"));
        assertFalse(source.contains("DataRetentionRunAuditService"));
        assertFalse(source.contains("PollRetentionSliceLockService"));
        assertFalse(source.contains("data_retention_purged_slice ("));
    }

    @Test
    void idOnlyAndShardOnlyWritersUseSliceGuardedIdentityRecheck() throws Exception {
        String writer = read("src/main/java/com/huanjing/geo/module/retention/service/PollRetentionSliceWriteService.java");
        assertTrue(writer.contains("executeByAttemptId"));
        assertTrue(writer.contains("executeByShardId"));
        assertTrue(writer.contains("executeByProviderCallId"));
        assertTrue(writer.contains("executeByBatchId"));
        assertTrue(writer.contains("FOR UPDATE"));
        assertTrue(writer.contains("identity changed while acquiring retention fence"));

        String shard = read("src/main/java/com/huanjing/geo/module/dispatch/service/DispatchPollShardPersistenceService.java");
        assertTrue(shard.contains("executeByShardId"));
        String planning = read("src/main/java/com/huanjing/geo/module/dispatch/service/DispatchQuestionPollPlanningService.java");
        assertTrue(planning.indexOf("retentionSliceGuardService.lockAndRequireWritable")
                < planning.indexOf("pollBatchMapper.insert"));
    }

    @Test
    void resultIdentityColumnsAreExcludedFromMutableProjectionUpdates() throws Exception {
        String mapper = read("src/main/java/com/huanjing/geo/module/dispatch/mapper/PollResultMapper.java");
        int businessUpdate = mapper.indexOf("updateBusinessProjection");
        String mutableSql = mapper.substring(businessUpdate, mapper.indexOf("@Select", businessUpdate));
        assertFalse(mutableSql.contains("project_id ="));
        assertFalse(mutableSql.contains("batch_date ="));
        assertFalse(mutableSql.contains("question_tier ="));
        assertFalse(mutableSql.contains("keyword_result_id ="));
        assertFalse(mutableSql.contains("channel_code ="));
        assertFalse(mutableSql.contains("platform_code ="));
    }

    @Test
    void forwardMigrationOwnsCleanupLedgerWithoutEnablingRuntimeSwitches() throws Exception {
        String migration = read("src/main/resources/db/migration/V367__poll_retention_execute_and_preserved_cleanup.sql");
        assertTrue(migration.contains("poll_preserved_cleanup"));
        assertTrue(migration.contains("data_retention_poll_preserved_result"));
        assertTrue(migration.contains("fk_retention_poll_preserved_marker"));
        assertTrue(migration.contains("fk_retention_poll_preserved_cleanup_operation"));
        assertTrue(migration.contains("chk_retention_poll_preserved_status"));
        assertFalse(migration.toLowerCase().contains("execute_enabled"));
        assertFalse(migration.toLowerCase().contains("enable-write"));
    }

    @Test
    void requiredProfileIncludesRealCoordinatorRepositoryAndFailsClosed() throws Exception {
        String pom = read("pom.xml");
        assertTrue(pom.contains("<id>retention-poll-it</id>"));
        assertTrue(pom.contains("**/retention/poll/*Test.java"));
        assertTrue(pom.contains("<retention.poll-it.required>true</retention.poll-it.required>"));
        assertTrue(pom.contains("<failIfNoTests>true</failIfNoTests>"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(ROOT.resolve(path));
    }
}
