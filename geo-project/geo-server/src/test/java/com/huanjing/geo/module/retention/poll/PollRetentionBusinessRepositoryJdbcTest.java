package com.huanjing.geo.module.retention.poll;

import com.huanjing.geo.module.dispatch.service.PollSummaryRecomputeService;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardEntityJudgeService;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.LockedPollMutationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.OperationReplayStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionOperationContext;
import com.huanjing.geo.module.retention.execute.DataRetentionExecutionException;
import com.huanjing.geo.module.retention.service.PollRetentionSliceLockService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class PollRetentionBusinessRepositoryJdbcTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private DataSourceTransactionManager transactionManager;
    private PollRetentionBusinessRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:wp7_poll_business;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        createSchema();
        PollSummaryRecomputeService summary = mock(PollSummaryRecomputeService.class);
        when(summary.verifySlice(any(), any(), any())).thenReturn(
                new PollSummaryRecomputeService.SummaryVerification(2, 1, 2, true, 1, 2, true));
        MobileDashboardEntityJudgeService entity = mock(MobileDashboardEntityJudgeService.class);
        when(entity.verifySummarySlice(any(), any(), any())).thenReturn(
                new MobileDashboardEntityJudgeService.EntityJudgeSummaryVerification(0, 0, true));
        transactionManager = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(transactionManager);
        repository = new PollRetentionBusinessRepository(jdbc, mock(PollRetentionSliceLockService.class),
                summary, entity, transactionManager);
        seedPrimarySlice();
    }

    @Test
    void primaryPreservesLatestProjectionAndCompleteChildrenWhileDeletingOldDetailsAndShards() {
        PollRetentionBusinessRepository.PrimaryMutationResult result = transaction.execute(status ->
                repository.mutatePrimary(new LockedPollMutationContext(primaryOperation(), 1, 11, List.of()),
                        7, LocalDate.of(2026, 8, 14)));

        assertEquals(List.of(2L), result.preservedResultIds());
        assertEquals(0, count("SELECT COUNT(*) FROM poll_results WHERE id=1"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_results WHERE id=2"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_invocation_attempts WHERE poll_result_id=1"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_provider_calls WHERE id=21"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_search_sources WHERE id=31"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_citations WHERE id=41"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_result_entity_judge WHERE id=51"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_invocation_attempts WHERE poll_result_id=2"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_batch_shards"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_keyword_daily_summary"));
    }

    @Test
    void cleanupDeletesOnlyLedgerOwnedResultAfterNewerProjectionAppears() {
        transaction.executeWithoutResult(status -> repository.mutatePrimary(
                new LockedPollMutationContext(primaryOperation(), 1, 11, List.of()),
                7, LocalDate.of(2026, 8, 14)));
        jdbc.update("INSERT INTO data_retention_purged_slice "
                        + "(operation_id,domain,project_id,batch_date,question_tier,status) VALUES (?,?,?,?,?,'purged')",
                "00000000-0000-0000-0000-000000000001", "poll_results", 71L,
                java.sql.Date.valueOf("2026-07-01"), "A");
        jdbc.update("INSERT INTO data_retention_poll_preserved_result "
                        + "(origin_operation_id,poll_result_id,project_id,batch_date,question_tier,cleanup_status) "
                        + "VALUES (?,?,?,?,?,'preserved')",
                "00000000-0000-0000-0000-000000000001", 2L, 71L,
                java.sql.Date.valueOf("2026-07-01"), "A");
        insertResult(4, "2026-07-03", 202, "doubao", "completed");

        PollRetentionBusinessRepository.CleanupMutationResult result = transaction.execute(status ->
                repository.mutateCleanup(new LockedPollMutationContext(cleanupOperation(), 2, 21, List.of(2L))));

        assertEquals(List.of(2L), result.pollResultIds());
        assertEquals(0, count("SELECT COUNT(*) FROM poll_results WHERE id=2"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_invocation_attempts WHERE id=12"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_provider_calls WHERE id=22"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_search_sources WHERE id=32"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_citations WHERE id=42"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_result_entity_judge WHERE id=52"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_results WHERE id=4"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_poll_preserved_result "
                + "WHERE poll_result_id=2 AND cleanup_status='preserved'"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_keyword_daily_summary"));
    }

    @Test
    void primaryPostCheckRejectsAnOmittedLockedChildDeletion() {
        PollRetentionBusinessRepository faulty = repositoryWithOmittedDelete("DELETE FROM poll_citations");

        DataRetentionExecutionException failure = assertThrows(DataRetentionExecutionException.class,
                () -> transaction.execute(status -> faulty.mutatePrimary(
                        new LockedPollMutationContext(primaryOperation(), 1, 11, List.of()),
                        7, LocalDate.of(2026, 8, 14))));

        assertEquals("POLL_POST_CHECK_FAILED", failure.resultCode());
        assertEquals(1, count("SELECT COUNT(*) FROM poll_results WHERE id=1"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_citations WHERE id=41"));
    }

    @Test
    void cleanupPostCheckRejectsAnOmittedLockedChildDeletion() {
        transaction.executeWithoutResult(status -> repository.mutatePrimary(
                new LockedPollMutationContext(primaryOperation(), 1, 11, List.of()),
                7, LocalDate.of(2026, 8, 14)));
        jdbc.update("INSERT INTO data_retention_purged_slice "
                        + "(operation_id,domain,project_id,batch_date,question_tier,status) VALUES (?,?,?,?,?,'purged')",
                "00000000-0000-0000-0000-000000000001", "poll_results", 71L,
                java.sql.Date.valueOf("2026-07-01"), "A");
        jdbc.update("INSERT INTO data_retention_poll_preserved_result "
                        + "(origin_operation_id,poll_result_id,project_id,batch_date,question_tier,cleanup_status) "
                        + "VALUES (?,?,?,?,?,'preserved')",
                "00000000-0000-0000-0000-000000000001", 2L, 71L,
                java.sql.Date.valueOf("2026-07-01"), "A");
        insertResult(4, "2026-07-03", 202, "doubao", "completed");
        PollRetentionBusinessRepository faulty = repositoryWithOmittedDelete("DELETE FROM poll_provider_calls");

        DataRetentionExecutionException failure = assertThrows(DataRetentionExecutionException.class,
                () -> transaction.execute(status -> faulty.mutateCleanup(
                        new LockedPollMutationContext(cleanupOperation(), 2, 21, List.of(2L)))));

        assertEquals("POLL_POST_CHECK_FAILED", failure.resultCode());
        assertEquals(1, count("SELECT COUNT(*) FROM poll_results WHERE id=2"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_provider_calls WHERE id=22"));
    }

    private PollRetentionBusinessRepository repositoryWithOmittedDelete(String sqlPrefix) {
        JdbcTemplate faultyJdbc = spy(jdbc);
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.startsWith(sqlPrefix) ? 0 : invocation.callRealMethod();
        }).when(faultyJdbc).update(anyString(), any(Object[].class));
        PollSummaryRecomputeService summary = mock(PollSummaryRecomputeService.class);
        when(summary.verifySlice(any(), any(), any())).thenReturn(
                new PollSummaryRecomputeService.SummaryVerification(2, 1, 2, true, 1, 2, true));
        MobileDashboardEntityJudgeService entity = mock(MobileDashboardEntityJudgeService.class);
        when(entity.verifySummarySlice(any(), any(), any())).thenReturn(
                new MobileDashboardEntityJudgeService.EntityJudgeSummaryVerification(0, 0, true));
        return new PollRetentionBusinessRepository(faultyJdbc, mock(PollRetentionSliceLockService.class),
                summary, entity, transactionManager);
    }

    private RetentionOperationContext primaryOperation() {
        return operation("00000000-0000-0000-0000-000000000001", "poll_slice", 1, List.of());
    }

    private RetentionOperationContext cleanupOperation() {
        return operation("00000000-0000-0000-0000-000000000002", "poll_preserved_cleanup", 1, List.of(2L));
    }

    private RetentionOperationContext operation(String id, String type, int itemCount, List<Long> ids) {
        return new RetentionOperationContext(id, 1, type, new byte[32], 1, itemCount,
                null, null, 71L, LocalDate.of(2026, 7, 1), "A", LocalDate.of(2026, 7, 31),
                new RetentionCompatibilityContract("poll_results", 1, new byte[32], "poll-results-v2"),
                "test", "test", OperationReplayStatus.CREATED, "pending", null, ids);
    }

    private void seedPrimarySlice() {
        jdbc.update("INSERT INTO poll_batches VALUES (1,71,DATE '2026-07-01','A','finished')");
        insertResult(1, "2026-07-01", 101, "doubao", "completed");
        insertResult(2, "2026-07-01", 202, "doubao", "completed");
        insertResult(3, "2026-07-02", 101, "doubao", "completed");
        jdbc.update("INSERT INTO poll_invocation_attempts VALUES (11,1,'SUCCEEDED')");
        jdbc.update("INSERT INTO poll_invocation_attempts VALUES (12,2,'SUCCEEDED')");
        jdbc.update("INSERT INTO poll_provider_calls VALUES (21,11)");
        jdbc.update("INSERT INTO poll_provider_calls VALUES (22,12)");
        jdbc.update("INSERT INTO poll_search_sources VALUES (31,11)");
        jdbc.update("INSERT INTO poll_search_sources VALUES (32,12)");
        jdbc.update("INSERT INTO poll_citations VALUES (41,11)");
        jdbc.update("INSERT INTO poll_citations VALUES (42,12)");
        jdbc.update("INSERT INTO poll_result_entity_judge VALUES (51,1)");
        jdbc.update("INSERT INTO poll_result_entity_judge VALUES (52,2)");
        jdbc.update("INSERT INTO poll_batch_shards VALUES (61,71,DATE '2026-07-01','A')");
        jdbc.update("INSERT INTO poll_batch_shard_items VALUES (71,61)");
        jdbc.update("INSERT INTO poll_keyword_daily_summary VALUES (81,71,DATE '2026-07-01','A','stable')");
    }

    private void insertResult(long id, String date, long keywordId, String channel, String status) {
        jdbc.update("INSERT INTO poll_results "
                        + "(id,project_id,batch_date,question_tier,status,keyword_result_id,keyword_text_snapshot,"
                        + "channel_code,platform_code,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id, 71L, java.sql.Date.valueOf(date), "A", status, keywordId, "question-" + keywordId,
                channel, channel);
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE data_retention_recompute_slice_lock (id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                " domain VARCHAR(64), project_id BIGINT, batch_date DATE, question_tier VARCHAR(16)," +
                " UNIQUE(domain,project_id,batch_date,question_tier))");
        jdbc.execute("CREATE TABLE data_retention_purged_slice (operation_id VARCHAR(36), domain VARCHAR(64)," +
                " project_id BIGINT,batch_date DATE,question_tier VARCHAR(16),status VARCHAR(24))");
        jdbc.execute("CREATE TABLE data_retention_operation (operation_id VARCHAR(36),domain VARCHAR(64)," +
                " operation_type VARCHAR(32),poll_project_id BIGINT,poll_batch_date DATE," +
                " poll_question_tier VARCHAR(16),outcome_status VARCHAR(24))");
        jdbc.execute("CREATE TABLE data_retention_batch_item (operation_id VARCHAR(36),domain VARCHAR(64),business_key BIGINT)");
        jdbc.execute("CREATE TABLE data_retention_poll_preserved_result (origin_operation_id VARCHAR(36)," +
                " poll_result_id BIGINT,project_id BIGINT,batch_date DATE,question_tier VARCHAR(16)," +
                " cleanup_status VARCHAR(16),cleanup_operation_id VARCHAR(36),cleaned_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE poll_batches (id BIGINT PRIMARY KEY,project_id BIGINT,batch_date DATE," +
                " question_tier VARCHAR(16),status VARCHAR(32))");
        jdbc.execute("CREATE TABLE poll_results (id BIGINT PRIMARY KEY,project_id BIGINT,batch_date DATE," +
                " question_tier VARCHAR(16),status VARCHAR(32),keyword_result_id BIGINT,keyword_text_snapshot VARCHAR(500)," +
                " channel_code VARCHAR(64),platform_code VARCHAR(64),created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE poll_invocation_attempts (id BIGINT PRIMARY KEY,poll_result_id BIGINT,status VARCHAR(32))");
        jdbc.execute("CREATE TABLE poll_provider_calls (id BIGINT PRIMARY KEY,attempt_id BIGINT)");
        jdbc.execute("CREATE TABLE poll_search_sources (id BIGINT PRIMARY KEY,attempt_id BIGINT)");
        jdbc.execute("CREATE TABLE poll_citations (id BIGINT PRIMARY KEY,attempt_id BIGINT)");
        jdbc.execute("CREATE TABLE poll_result_entity_judge (id BIGINT PRIMARY KEY,poll_result_id BIGINT)");
        jdbc.execute("CREATE TABLE poll_batch_shards (id BIGINT PRIMARY KEY,project_id BIGINT,batch_date DATE,question_tier VARCHAR(16))");
        jdbc.execute("CREATE TABLE poll_batch_shard_items (id BIGINT PRIMARY KEY,shard_id BIGINT)");
        jdbc.execute("CREATE TABLE poll_keyword_daily_summary (id BIGINT PRIMARY KEY,project_id BIGINT,batch_date DATE," +
                " question_tier VARCHAR(16),metric_value VARCHAR(64))");
        jdbc.execute("CREATE TABLE poll_platform_daily_summary (id BIGINT PRIMARY KEY,project_id BIGINT,batch_date DATE," +
                " question_tier VARCHAR(16),metric_value VARCHAR(64))");
        jdbc.execute("CREATE TABLE poll_entity_judge_daily_summary (id BIGINT PRIMARY KEY,project_id BIGINT,batch_date DATE," +
                " question_tier VARCHAR(16),metric_value VARCHAR(64))");
    }
}
