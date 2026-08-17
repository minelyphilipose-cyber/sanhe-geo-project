package com.huanjing.geo.module.retention.detailcleanup;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import com.huanjing.geo.module.retention.audit.support.DataRetentionApplicationVersionProvider;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class DataRetentionDetailCleanupMysqlIntegrationTest {
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration").toAbsolutePath();
    private static final String REQUIRED = "retention.detail-cleanup-it.required";
    private MySQLContainer<?> container;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private String serverUrl, databaseName, username, password;
    private boolean ready;

    @BeforeAll void configure() throws Exception { ready = configureDatabase(); }

    @BeforeEach void schema() throws Exception {
        Assumptions.assumeTrue(ready, "MySQL 8 is unavailable");
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        try (Connection connection = dataSource.getConnection()) {
            createTable(connection, "V228__data_lifecycle_summary_and_run.sql", "CREATE TABLE IF NOT EXISTS data_retention_run (");
            createTable(connection, "V228__data_lifecycle_summary_and_run.sql", "CREATE TABLE IF NOT EXISTS data_retention_purged_slice (");
            createTable(connection, "V228__data_lifecycle_summary_and_run.sql", "CREATE TABLE IF NOT EXISTS data_retention_recompute_slice_lock (");
            createTable(connection, "V313__web_search_question_poll_foundation.sql", "CREATE TABLE poll_audit_purge_runs (");
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(MIGRATIONS.resolve("V335__align_poll_retention_slice_collation.sql")));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(MIGRATIONS.resolve("V354__data_retention_execute_governance.sql")));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(MIGRATIONS.resolve("V367__poll_retention_execute_and_preserved_cleanup.sql")));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(MIGRATIONS.resolve("V368__data_retention_detail_cleanup_readiness.sql")));
        }
        jdbc = new JdbcTemplate(dataSource);
        transactions = new DataSourceTransactionManager(dataSource);
    }

    @AfterAll void release() throws Exception {
        if (container != null) container.stop();
        if (databaseName != null) {
            if (!databaseName.startsWith("geo_retention_wp8_it_")) throw new IllegalStateException("unsafe database name");
            try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + databaseName + "`");
            }
        }
    }

    @Test void v368IsRepairSafeAndEnforcesMarkerCountPair() throws Exception {
        assertThat(columnCount("data_retention_operation", "detail_purged_item_count")).isEqualTo(1);
        assertThat(columnCount("data_retention_operation_run", "detail_purged_attempt_count")).isEqualTo(1);
        assertThat(columnCount("data_retention_run", "operationless_attempt_purged_count")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='data_retention_detail_cleanup_run'", Integer.class)).isEqualTo(1);
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(MIGRATIONS.resolve("V368__data_retention_detail_cleanup_readiness.sql")));
        }
        insertCommittedWebsiteTree();
        assertThatThrownBy(() -> jdbc.update("UPDATE data_retention_operation SET detail_purged_at=UTC_TIMESTAMP(3) WHERE operation_id='90000000-0000-0000-0000-000000000001'"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void purgesCompleteOperationTreeAndPreservesPermanentAudit() {
        insertCommittedWebsiteTree();
        DataRetentionDetailCleanupRepository repository = new DataRetentionDetailCleanupRepository(jdbc);
        DataRetentionDetailCleanupModels.CandidateResult result = new TransactionTemplate(transactions).execute(
                ignored -> repository.purgeOperation("90000000-0000-0000-0000-000000000001",
                        Instant.parse("2025-08-15T00:00:00Z"), 1000));

        assertThat(result.outcome()).isEqualTo(DataRetentionDetailCleanupModels.CandidateOutcome.PURGED);
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.items()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM data_retention_batch_attempt")).isZero();
        assertThat(count("SELECT COUNT(*) FROM data_retention_batch_item")).isZero();
        assertThat(count("SELECT detail_purged_attempt_count FROM data_retention_operation_run")).isEqualTo(1);
        assertThat(count("SELECT detail_purged_item_count FROM data_retention_operation")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM data_retention_batch_commit")).isEqualTo(1);
        assertThat(repository.invariantViolationCount()).isZero();
    }

    @Test void zeroTreeWithoutMarkerIsP0AndNeverReceivesMarkerOnlyRepair() {
        insertCommittedWebsiteTree();
        jdbc.update("SET FOREIGN_KEY_CHECKS=0");
        try {
            jdbc.update("DELETE FROM data_retention_batch_item");
            jdbc.update("DELETE FROM data_retention_batch_attempt");
        } finally { jdbc.update("SET FOREIGN_KEY_CHECKS=1"); }
        DataRetentionDetailCleanupRepository repository = new DataRetentionDetailCleanupRepository(jdbc);
        assertThat(repository.invariantViolationCount()).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT detail_purged_at FROM data_retention_operation", Object.class)).isNull();
    }

    @Test void selectorUsesExact365DayMillisecondBoundary() {
        insertCommittedWebsiteTree();
        DataRetentionDetailCleanupRepository repository=new DataRetentionDetailCleanupRepository(jdbc);
        jdbc.update("UPDATE data_retention_operation SET detail_retention_anchor_at='2025-08-15 00:00:00.001' WHERE operation_id='90000000-0000-0000-0000-000000000001'");
        assertThat(repository.findOperationCandidates(Instant.parse("2025-08-15T00:00:00Z"),20,1000)).isEmpty();
        jdbc.update("UPDATE data_retention_operation SET detail_retention_anchor_at='2025-08-15 00:00:00.000' WHERE operation_id='90000000-0000-0000-0000-000000000001'");
        assertThat(repository.findOperationCandidates(Instant.parse("2025-08-15T00:00:00Z"),20,1000))
                .containsExactly("90000000-0000-0000-0000-000000000001");
    }

    @Test void purgesWholeOperationlessRunSubsetAndAdvancesConservationCount() {
        insertOperationlessRun();
        DataRetentionDetailCleanupRepository repository=new DataRetentionDetailCleanupRepository(jdbc);
        DataRetentionDetailCleanupModels.CandidateResult result=new TransactionTemplate(transactions).execute(
                ignored->repository.purgeOperationlessRun(301,Instant.parse("2025-08-15T00:00:00Z"),1000));
        assertThat(result.outcome()).isEqualTo(DataRetentionDetailCleanupModels.CandidateOutcome.PURGED);
        assertThat(count("SELECT operationless_attempt_purged_count FROM data_retention_run WHERE id=301")).isEqualTo(1);
        assertThat(Boolean.TRUE.equals(new TransactionTemplate(transactions).execute(
                ignored->repository.advanceRunWaterline(301,Instant.parse("2025-08-15T00:00:00Z"))))).isTrue();
        assertThat(jdbc.queryForObject("SELECT attempt_detail_purged_at FROM data_retention_run WHERE id=301",Object.class))
                .isNotNull();
        assertThat(repository.invariantViolationCount()).isZero();
    }

    @Test void purgesCommittedPollSliceOnlyWithPermanentMarkerProof() {
        insertCommittedPollSliceTree();
        DataRetentionDetailCleanupRepository repository=new DataRetentionDetailCleanupRepository(jdbc);
        DataRetentionDetailCleanupModels.UnknownCommitClassification rolledBack=
                new TransactionTemplate(transactions).execute(ignored->repository.classifyOperationUnknownCommit(
                        "b0000000-0000-0000-0000-000000000001"));
        assertThat(rolledBack.resolution()).isEqualTo(DataRetentionDetailCleanupModels.UnknownCommitResolution.ROLLED_BACK);
        assertThat(repository.eligibleAttemptBacklog(Instant.parse("2025-08-15T00:00:00Z"))).isEqualTo(1);
        assertThat(repository.eligibleItemBacklog(Instant.parse("2025-08-15T00:00:00Z"))).isZero();
        DataRetentionDetailCleanupModels.CandidateResult result=new TransactionTemplate(transactions).execute(
                ignored->repository.purgeOperation("b0000000-0000-0000-0000-000000000001",
                        Instant.parse("2025-08-15T00:00:00Z"),1000));
        assertThat(result.outcome()).isEqualTo(DataRetentionDetailCleanupModels.CandidateOutcome.PURGED);
        assertThat(result.items()).isZero();
        assertThat(count("SELECT COUNT(*) FROM data_retention_purged_slice WHERE operation_id='b0000000-0000-0000-0000-000000000001'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE operation_id='b0000000-0000-0000-0000-000000000001'")).isZero();
        assertThat(count("SELECT detail_purged_item_count FROM data_retention_operation WHERE operation_id='b0000000-0000-0000-0000-000000000001'")).isZero();
        DataRetentionDetailCleanupModels.UnknownCommitClassification committed=
                new TransactionTemplate(transactions).execute(ignored->repository.classifyOperationUnknownCommit(
                        "b0000000-0000-0000-0000-000000000001"));
        assertThat(committed.resolution()).isEqualTo(DataRetentionDetailCleanupModels.UnknownCommitResolution.COMMITTED);
        assertThat(committed.committedResult().items()).isZero();
        assertThat(repository.invariantViolationCount()).isZero();
    }

    @Test void eligibleBacklogUsesCompleteOperationQualificationAndExcludesOperationlessAttempts() {
        insertCommittedWebsiteTree();
        insertOperationlessRun();
        DataRetentionDetailCleanupRepository repository=new DataRetentionDetailCleanupRepository(jdbc);
        Instant cutoff=Instant.parse("2025-08-15T00:00:00Z");

        assertThat(repository.eligibleAttemptBacklog(cutoff)).isEqualTo(1);
        assertThat(repository.eligibleItemBacklog(cutoff)).isEqualTo(1);

        jdbc.update("UPDATE data_retention_batch_attempt SET status='prepared' WHERE operation_id IS NOT NULL");
        assertThat(repository.eligibleAttemptBacklog(cutoff)).isZero();
        assertThat(repository.eligibleItemBacklog(cutoff)).isZero();
        jdbc.update("UPDATE data_retention_batch_attempt SET status='succeeded' WHERE operation_id IS NOT NULL");

        jdbc.update("UPDATE data_retention_run SET status='running' WHERE id=101");
        assertThat(repository.eligibleAttemptBacklog(cutoff)).isZero();
        assertThat(repository.eligibleItemBacklog(cutoff)).isZero();
        jdbc.update("UPDATE data_retention_run SET status='succeeded' WHERE id=101");

        jdbc.update("UPDATE data_retention_run SET attempt_count=2 WHERE id=101");
        assertThat(repository.eligibleAttemptBacklog(cutoff)).isZero();
        assertThat(repository.eligibleItemBacklog(cutoff)).isZero();
        jdbc.update("UPDATE data_retention_run SET attempt_count=1 WHERE id=101");

        jdbc.update("""
                INSERT INTO data_retention_unknown_outcome_reconciliation
                  (reconciliation_id,operation_id,run_group_id,domain,marker_state,business_state,decision,status,
                   evidence_hash,requested_by)
                VALUES ('d0000000-0000-0000-0000-000000000002','90000000-0000-0000-0000-000000000001',101,
                        'website_published_content','present','mutated','committed','pending',UNHEX(REPEAT('89',32)),7)
                """);
        assertThat(repository.eligibleAttemptBacklog(cutoff)).isZero();
        assertThat(repository.eligibleItemBacklog(cutoff)).isZero();
        jdbc.update("""
                UPDATE data_retention_unknown_outcome_reconciliation
                   SET status='resolved',decision='closed_no_mutation',resolution_reason='conflict fixture',
                       resolved_by=7,resolved_at='2025-08-14 00:00:00.000'
                """);
        assertThat(repository.eligibleAttemptBacklog(cutoff)).isZero();
        assertThat(repository.eligibleItemBacklog(cutoff)).isZero();
        jdbc.update("DELETE FROM data_retention_unknown_outcome_reconciliation");

        jdbc.update("""
                INSERT INTO data_retention_failure_group
                  (domain,source_run_group_id,operation_id,status,item_count,failure_code,opened_at)
                VALUES ('website_published_content',101,'90000000-0000-0000-0000-000000000001',
                        'active',1,'TEST_ACTIVE','2025-08-14 00:00:00.000')
                """);
        assertThat(repository.eligibleAttemptBacklog(cutoff)).isZero();
        assertThat(repository.eligibleItemBacklog(cutoff)).isZero();
        jdbc.update("DELETE FROM data_retention_failure_group");

        jdbc.update("DELETE FROM data_retention_batch_commit");
        assertThat(repository.eligibleAttemptBacklog(cutoff)).isZero();
        assertThat(repository.eligibleItemBacklog(cutoff)).isZero();
    }

    @Test void operationlessCleanupDoesNotAdvanceRunWaterlineWhileEligibleOperationDetailRemains() {
        insertCommittedWebsiteTree();
        jdbc.update("UPDATE data_retention_run SET attempt_count=2,succeeded_attempt_count=2 WHERE id=101");
        jdbc.update("""
                INSERT INTO data_retention_batch_attempt
                  (id,run_group_id,operation_id,sequence_no,attempt_no,domain,attempt_type,status,created_at,finished_at)
                VALUES (202,101,NULL,2,1,'website_published_content','reconcile_only','succeeded',
                        '2025-08-14 00:00:00.000','2025-08-14 00:00:00.000')
                """);
        DataRetentionDetailCleanupRepository repository=new DataRetentionDetailCleanupRepository(jdbc);
        Instant cutoff=Instant.parse("2025-08-15T00:00:00Z");

        new TransactionTemplate(transactions).execute(ignored->repository.purgeOperationlessRun(101,cutoff,1000));
        assertThat(Boolean.TRUE.equals(new TransactionTemplate(transactions).execute(
                ignored->repository.advanceRunWaterline(101,cutoff)))).isFalse();
        assertThat(jdbc.queryForObject("SELECT attempt_detail_purged_at FROM data_retention_run WHERE id=101",Object.class))
                .isNull();

        DataRetentionDetailCleanupRepository serviceRepository=new DataRetentionDetailCleanupRepository(jdbc) {
            @Override public DataRetentionDetailCleanupModels.CandidateResult purgeOperation(
                    String cleanupRunId,String operationId,Instant operationCutoff,int maxAttempts) {
                return new TransactionTemplate(transactions).execute(ignored ->
                        super.purgeOperation(cleanupRunId,operationId,operationCutoff,maxAttempts));
            }
            @Override public boolean advanceRunWaterline(long runId,Instant operationCutoff) {
                return Boolean.TRUE.equals(new TransactionTemplate(transactions).execute(ignored ->
                        super.advanceRunWaterline(runId,operationCutoff)));
            }
        };
        DataRetentionDetailCleanupRunRepository runRepository=new DataRetentionDetailCleanupRunRepository(jdbc);
        DataRetentionDetailCleanupLease leaseProvider=mock(DataRetentionDetailCleanupLease.class);
        DataRetentionDetailCleanupLease.Lease lease=mock(DataRetentionDetailCleanupLease.Lease.class);
        when(leaseProvider.tryAcquire()).thenReturn(new DataRetentionDetailCleanupLease.AcquireResult(
                DataRetentionDetailCleanupLease.AcquireCode.ACQUIRED,lease,null));
        DataRetentionDetailCleanupReadinessService readiness=mock(DataRetentionDetailCleanupReadinessService.class);
        when(readiness.evaluate()).thenReturn(new DataRetentionDetailCleanupModels.ReadinessEvaluation(
                DataRetentionDetailCleanupModels.ReadinessCode.READY,
                DataRetentionDetailCleanupModels.Severity.READY,
                java.util.List.of(DataRetentionDetailCleanupModels.ReadinessCode.READY),
                0,0,null,0L,0L));
        DataRetentionApplicationVersionProvider version=mock(DataRetentionApplicationVersionProvider.class);
        when(version.currentVersion()).thenReturn("wp8-mysql-it");
        DataRetentionDetailCleanupService service=new DataRetentionDetailCleanupService(serviceRepository,runRepository,
                leaseProvider,readiness,mock(DataRetentionDetailCleanupSafetyConverger.class),
                mock(DataRetentionDetailCleanupAlertSink.class),mock(DataRetentionDetailCleanupCapacityService.class),
                mock(DataRetentionDetailCleanupMetrics.class),version,new DataRetentionProperties(),
                Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"),ZoneOffset.UTC));

        assertThat(service.runManualTest().status()).isEqualTo("succeeded");
        assertThat(jdbc.queryForObject("SELECT attempt_detail_purged_at FROM data_retention_run WHERE id=101",Object.class))
                .isNotNull();
    }

    @Test void purgesPollCleanupDetailWithoutDeletingPermanentOwnershipLedger() {
        insertCommittedPollCleanupTree();
        DataRetentionDetailCleanupRepository repository=new DataRetentionDetailCleanupRepository(jdbc);
        DataRetentionDetailCleanupModels.CandidateResult result=new TransactionTemplate(transactions).execute(
                ignored->repository.purgeOperation("c0000000-0000-0000-0000-000000000002",
                        Instant.parse("2025-08-15T00:00:00Z"),1000));
        assertThat(result.outcome()).isEqualTo(DataRetentionDetailCleanupModels.CandidateOutcome.PURGED);
        assertThat(count("SELECT COUNT(*) FROM data_retention_poll_preserved_result WHERE cleanup_operation_id='c0000000-0000-0000-0000-000000000002' AND cleanup_status='cleaned'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM data_retention_batch_item WHERE operation_id='c0000000-0000-0000-0000-000000000002'")).isZero();
        assertThat(repository.invariantViolationCount()).isZero();
    }

    @Test void purgesClosedNoMutationWithTerminalAttemptProof() {
        insertCommittedWebsiteTree();
        jdbc.update("DELETE FROM data_retention_batch_commit");
        jdbc.update("""
                UPDATE data_retention_operation SET outcome_status='closed_no_mutation',
                    outcome_finalization_source='terminal_attempt',outcome_finalization_reason='verified_no_mutation'
                 WHERE operation_id='90000000-0000-0000-0000-000000000001'
                """);
        DataRetentionDetailCleanupRepository repository=new DataRetentionDetailCleanupRepository(jdbc);
        DataRetentionDetailCleanupModels.CandidateResult result=new TransactionTemplate(transactions).execute(
                ignored->repository.purgeOperation("90000000-0000-0000-0000-000000000001",
                        Instant.parse("2025-08-15T00:00:00Z"),1000));
        assertThat(result.outcome()).isEqualTo(DataRetentionDetailCleanupModels.CandidateOutcome.PURGED);
    }

    @Test void purgesClosedNoMutationWithResolvedReconciliationProof() {
        insertCommittedWebsiteTree();
        jdbc.update("DELETE FROM data_retention_batch_commit");
        jdbc.update("""
                UPDATE data_retention_operation SET outcome_status='closed_no_mutation',
                    outcome_finalization_source='reconciliation',outcome_finalization_reason='verified_no_mutation'
                 WHERE operation_id='90000000-0000-0000-0000-000000000001'
                """);
        jdbc.update("""
                INSERT INTO data_retention_unknown_outcome_reconciliation
                  (reconciliation_id,operation_id,run_group_id,domain,marker_state,business_state,decision,status,
                   evidence_hash,requested_by,resolution_reason,resolved_by,resolved_at)
                VALUES ('d0000000-0000-0000-0000-000000000001','90000000-0000-0000-0000-000000000001',101,
                        'website_published_content','absent','no_mutation','closed_no_mutation','resolved',
                        UNHEX(REPEAT('88',32)),7,'verified no mutation',7,'2025-08-14 00:00:00.000')
                """);
        DataRetentionDetailCleanupRepository repository=new DataRetentionDetailCleanupRepository(jdbc);
        DataRetentionDetailCleanupModels.CandidateResult result=new TransactionTemplate(transactions).execute(
                ignored->repository.purgeOperation("90000000-0000-0000-0000-000000000001",
                        Instant.parse("2025-08-15T00:00:00Z"),1000));
        assertThat(result.outcome()).isEqualTo(DataRetentionDetailCleanupModels.CandidateOutcome.PURGED);
        assertThat(count("SELECT COUNT(*) FROM data_retention_unknown_outcome_reconciliation")).isEqualTo(1);
    }

    @Test void markerFailureRollsBackDeletesAndPurgeCounts() {
        insertCommittedWebsiteTree();
        jdbc.execute("""
                CREATE TRIGGER wp8_marker_failure BEFORE UPDATE ON data_retention_operation
                FOR EACH ROW
                BEGIN
                  IF NEW.detail_purged_at IS NOT NULL THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected marker failure';
                  END IF;
                END
                """);
        DataRetentionDetailCleanupRepository repository=new DataRetentionDetailCleanupRepository(jdbc);
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(
                ignored->repository.purgeOperation("90000000-0000-0000-0000-000000000001",
                        Instant.parse("2025-08-15T00:00:00Z"),1000))).isInstanceOf(RuntimeException.class);
        assertThat(count("SELECT COUNT(*) FROM data_retention_batch_attempt")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM data_retention_batch_item")).isEqualTo(1);
        assertThat(count("SELECT detail_purged_attempt_count FROM data_retention_operation_run")).isZero();
        assertThat(jdbc.queryForObject("SELECT detail_purged_at FROM data_retention_operation",Object.class)).isNull();
    }

    @Test void unknownCommitClassifierDistinguishesCommittedRolledBackAndMixedState() {
        insertCommittedWebsiteTree();
        DataRetentionDetailCleanupRepository repository = new DataRetentionDetailCleanupRepository(jdbc);

        DataRetentionDetailCleanupModels.UnknownCommitClassification rolledBack =
                new TransactionTemplate(transactions).execute(ignored ->
                        repository.classifyOperationUnknownCommit("90000000-0000-0000-0000-000000000001"));
        assertThat(rolledBack.resolution())
                .isEqualTo(DataRetentionDetailCleanupModels.UnknownCommitResolution.ROLLED_BACK);

        new TransactionTemplate(transactions).execute(ignored -> repository.purgeOperation(
                "90000000-0000-0000-0000-000000000001", Instant.parse("2025-08-15T00:00:00Z"), 1000));
        DataRetentionDetailCleanupModels.UnknownCommitClassification committed =
                new TransactionTemplate(transactions).execute(ignored ->
                        repository.classifyOperationUnknownCommit("90000000-0000-0000-0000-000000000001"));
        assertThat(committed.resolution())
                .isEqualTo(DataRetentionDetailCleanupModels.UnknownCommitResolution.COMMITTED);
        assertThat(committed.committedResult().attempts()).isEqualTo(1);
        assertThat(committed.committedResult().items()).isEqualTo(1);

        schemaResetForMixedUnknownCommit();
        jdbc.update("UPDATE data_retention_operation_run SET detail_purged_attempt_count=1");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(ignored ->
                repository.classifyOperationUnknownCommit("90000000-0000-0000-0000-000000000001")))
                .isInstanceOf(DataRetentionDetailCleanupException.class)
                .hasMessageContaining("UNKNOWN_COMMIT_MIXED_STATE");
    }

    @Test void cleanupRunActiveSlotIsExclusiveAndStaleOwnerCanBeFenced() {
        DataRetentionDetailCleanupRunRepository repository=new DataRetentionDetailCleanupRunRepository(jdbc);
        Instant now=Instant.parse("2026-08-15T00:00:00Z");
        TransactionTemplate transaction=new TransactionTemplate(transactions);
        assertThat(Boolean.TRUE.equals(transaction.execute(ignored->repository.start(
                "e0000000-0000-0000-0000-000000000001","manual_test","wp8-test",now,
                now.minusSeconds(365L*86400),3600)))).isTrue();
        assertThat(Boolean.TRUE.equals(transaction.execute(ignored->repository.start(
                "e0000000-0000-0000-0000-000000000002","manual_test","wp8-test",now.plusSeconds(1),
                now.minusSeconds(365L*86400),3600)))).isFalse();
        jdbc.update("UPDATE data_retention_detail_cleanup_run SET heartbeat_at='2026-08-14 22:58:59.000'");
        assertThat(Boolean.TRUE.equals(transaction.execute(ignored->repository.start(
                "e0000000-0000-0000-0000-000000000003","manual_test","wp8-test",now.plusSeconds(1),
                now.minusSeconds(365L*86400),3600)))).isTrue();
        assertThat(count("SELECT COUNT(*) FROM data_retention_detail_cleanup_run WHERE status='running'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM data_retention_detail_cleanup_run WHERE status='failed' AND last_error_code='STALE_RUN_TAKEOVER'")).isEqualTo(1);
    }

    @Test void scheduledServiceRunsTheRealMysqlMutationAndPersistsHeartbeatEvidence() {
        insertCommittedWebsiteTree();
        DataRetentionDetailCleanupRepository repository=new DataRetentionDetailCleanupRepository(jdbc) {
            @Override public DataRetentionDetailCleanupModels.CandidateResult purgeOperation(
                    String cleanupRunId,String operationId,Instant cutoff,int maxAttempts) {
                return new TransactionTemplate(transactions).execute(ignored ->
                        super.purgeOperation(cleanupRunId,operationId,cutoff,maxAttempts));
            }
        };
        DataRetentionDetailCleanupRunRepository runRepository=new DataRetentionDetailCleanupRunRepository(jdbc);
        DataRetentionDetailCleanupLease leaseProvider=mock(DataRetentionDetailCleanupLease.class);
        DataRetentionDetailCleanupLease.Lease lease=mock(DataRetentionDetailCleanupLease.Lease.class);
        when(leaseProvider.tryAcquire()).thenReturn(new DataRetentionDetailCleanupLease.AcquireResult(
                DataRetentionDetailCleanupLease.AcquireCode.ACQUIRED,lease,null));
        DataRetentionDetailCleanupReadinessService readiness=mock(DataRetentionDetailCleanupReadinessService.class);
        when(readiness.evaluate()).thenReturn(new DataRetentionDetailCleanupModels.ReadinessEvaluation(
                DataRetentionDetailCleanupModels.ReadinessCode.READY,
                DataRetentionDetailCleanupModels.Severity.READY,
                java.util.List.of(DataRetentionDetailCleanupModels.ReadinessCode.READY),
                0,0,null,0L,0L));
        DataRetentionDetailCleanupCapacityService capacity=mock(DataRetentionDetailCleanupCapacityService.class);
        DataRetentionApplicationVersionProvider version=mock(DataRetentionApplicationVersionProvider.class);
        when(version.currentVersion()).thenReturn("wp8-mysql-it");
        DataRetentionProperties properties=new DataRetentionProperties();
        properties.getDetailCleanup().setEnabled(true);
        DataRetentionDetailCleanupService service=new DataRetentionDetailCleanupService(repository,runRepository,
                leaseProvider,readiness,mock(DataRetentionDetailCleanupSafetyConverger.class),
                mock(DataRetentionDetailCleanupAlertSink.class),capacity,
                mock(DataRetentionDetailCleanupMetrics.class),version,properties,
                Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"),ZoneOffset.UTC));

        assertThat(service.runScheduled().status()).isEqualTo("succeeded");
        assertThat(count("SELECT COUNT(*) FROM data_retention_batch_attempt")).isZero();
        assertThat(count("SELECT COUNT(*) FROM data_retention_batch_item")).isZero();
        assertThat(count("SELECT COUNT(*) FROM data_retention_detail_cleanup_run WHERE status='succeeded' AND purged_operation_count=1 AND purged_attempt_count=1 AND purged_item_count=1")).isEqualTo(1);
    }

    private void insertCommittedWebsiteTree() {
        jdbc.update("""
                INSERT INTO data_retention_run
                  (id,domain,mode,status,retention_window_start,retention_window_end,candidate_count,
                   affected_count,skipped_count,warning_count,trigger_id,trigger_source,requested_by,
                   request_reason,scope_json,config_fingerprint,config_fingerprint_schema_version,
                   policy_version,application_version,attempt_count,succeeded_attempt_count,
                   failed_attempt_count,skipped_attempt_count,unknown_attempt_count,
                   attempt_rollup_completed_at,started_at,finished_at)
                VALUES (101,'website_published_content','execute','succeeded','2025-01-01','2025-01-01',1,
                        1,0,0,'80000000-0000-0000-0000-000000000001','manual',7,'fixture',JSON_OBJECT(),
                        UNHEX(REPEAT('11',32)),1,'retention-p0-v1','wp8-test',1,1,0,0,0,
                        '2025-08-14 00:00:00.000','2025-08-14 00:00:00.000','2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_operation
                  (operation_id,origin_run_group_id,domain,operation_type,batch_key_hash,hash_schema_version,
                   item_count,cutoff_at,upper_bound_business_key,config_fingerprint_schema_version,
                   config_fingerprint,policy_version,created_application_version,outcome_status,
                   outcome_finalized_at,outcome_finalization_source,outcome_finalization_reason,
                   detail_retention_anchor_at,created_at)
                VALUES ('90000000-0000-0000-0000-000000000001',101,'website_published_content','website_batch',
                        UNHEX(REPEAT('22',32)),1,1,'2025-08-13 00:00:00.000',501,1,
                        UNHEX(REPEAT('11',32)),'retention-p0-v1','wp8-test','committed',
                        '2025-08-14 00:00:00.000','website_commit','committed',
                        '2025-08-14 00:00:00.000','2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_operation_run
                  (operation_id,run_group_id,domain,config_fingerprint_schema_version,config_fingerprint,
                   policy_version,relation_type,linked_at)
                VALUES ('90000000-0000-0000-0000-000000000001',101,'website_published_content',1,
                        UNHEX(REPEAT('11',32)),'retention-p0-v1','origin','2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_batch_attempt
                  (id,run_group_id,operation_id,sequence_no,attempt_no,domain,attempt_type,status,
                   started_at,finished_at,created_at)
                VALUES (201,101,'90000000-0000-0000-0000-000000000001',1,1,
                        'website_published_content','normal_batch','succeeded',
                        '2025-08-14 00:00:00.000','2025-08-14 00:00:00.000','2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_batch_item
                  (run_group_id,first_attempt_id,operation_id,domain,business_key,sequence_no,created_at)
                VALUES (101,201,'90000000-0000-0000-0000-000000000001','website_published_content',501,1,
                        '2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_batch_commit
                  (operation_id,run_group_id,domain,batch_key_hash,hash_schema_version,item_count,cutoff_at,
                   upper_bound_business_key,config_fingerprint_schema_version,config_fingerprint,policy_version,
                   committed_application_version,committed_at)
                VALUES ('90000000-0000-0000-0000-000000000001',101,'website_published_content',
                        UNHEX(REPEAT('22',32)),1,1,'2025-08-13 00:00:00.000',501,1,
                        UNHEX(REPEAT('11',32)),'retention-p0-v1','wp8-test','2025-08-14 00:00:00.000')
                """);
    }

    private void schemaResetForMixedUnknownCommit() {
        jdbc.update("DELETE FROM data_retention_detail_cleanup_run");
        jdbc.update("DELETE FROM data_retention_batch_item");
        jdbc.update("DELETE FROM data_retention_batch_attempt");
        jdbc.update("DELETE FROM data_retention_batch_commit");
        jdbc.update("DELETE FROM data_retention_operation_run");
        jdbc.update("DELETE FROM data_retention_operation");
        jdbc.update("DELETE FROM data_retention_run");
        insertCommittedWebsiteTree();
    }

    private void insertCommittedPollSliceTree() {
        jdbc.update("""
                INSERT INTO data_retention_run
                  (id,domain,mode,status,retention_window_start,retention_window_end,candidate_count,
                   affected_count,skipped_count,warning_count,trigger_id,trigger_source,requested_by,
                   request_reason,scope_json,config_fingerprint,config_fingerprint_schema_version,
                   policy_version,application_version,attempt_count,succeeded_attempt_count,
                   failed_attempt_count,skipped_attempt_count,unknown_attempt_count,
                   attempt_rollup_completed_at,started_at,finished_at)
                VALUES (401,'poll_results','execute','succeeded','2025-01-01','2025-01-01',1,1,0,0,
                        'b1000000-0000-0000-0000-000000000001','manual',7,'poll fixture',JSON_OBJECT(),
                        UNHEX(REPEAT('44',32)),1,'retention-p0-v1','wp8-test',1,1,0,0,0,
                        '2025-08-14 00:00:00.000','2025-08-14 00:00:00.000','2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_operation
                  (operation_id,origin_run_group_id,domain,operation_type,batch_key_hash,hash_schema_version,
                   item_count,poll_project_id,poll_batch_date,poll_question_tier,poll_retention_cutoff_date,
                   config_fingerprint_schema_version,config_fingerprint,policy_version,
                   created_application_version,outcome_status,outcome_finalized_at,
                   outcome_finalization_source,outcome_finalization_reason,detail_retention_anchor_at,created_at)
                VALUES ('b0000000-0000-0000-0000-000000000001',401,'poll_results','poll_slice',
                        UNHEX(REPEAT('55',32)),1,1,71,'2025-01-01','default','2025-08-13',1,
                        UNHEX(REPEAT('44',32)),'retention-p0-v1','wp8-test','committed',
                        '2025-08-14 00:00:00.000','poll_marker','purged',
                        '2025-08-14 00:00:00.000','2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_operation_run
                  (operation_id,run_group_id,domain,config_fingerprint_schema_version,config_fingerprint,
                   policy_version,relation_type,linked_at)
                VALUES ('b0000000-0000-0000-0000-000000000001',401,'poll_results',1,
                        UNHEX(REPEAT('44',32)),'retention-p0-v1','origin','2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_batch_attempt
                  (id,run_group_id,operation_id,sequence_no,attempt_no,domain,attempt_type,status,
                   started_at,finished_at,created_at)
                VALUES (402,401,'b0000000-0000-0000-0000-000000000001',1,1,'poll_results',
                        'poll_slice','succeeded','2025-08-14 00:00:00.000','2025-08-14 00:00:00.000',
                        '2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_purged_slice
                  (domain,project_id,batch_date,question_tier,status,purged_at,retention_run_id,operation_id)
                VALUES ('poll_results',71,'2025-01-01','default','purged','2025-08-14 00:00:00.000',401,
                        'b0000000-0000-0000-0000-000000000001')
                """);
    }

    private void insertCommittedPollCleanupTree() {
        jdbc.update("""
                INSERT INTO data_retention_run
                  (id,domain,mode,status,candidate_count,affected_count,skipped_count,warning_count,
                   trigger_id,trigger_source,requested_by,request_reason,scope_json,config_fingerprint,
                   config_fingerprint_schema_version,policy_version,application_version,attempt_count,
                   succeeded_attempt_count,failed_attempt_count,skipped_attempt_count,unknown_attempt_count,
                   attempt_rollup_completed_at,started_at,finished_at)
                VALUES
                  (501,'poll_results','execute','succeeded',0,0,0,0,'c1000000-0000-0000-0000-000000000001',
                   'manual',7,'origin marker',JSON_OBJECT(),UNHEX(REPEAT('66',32)),1,'retention-p0-v1','wp8-test',
                   0,0,0,0,0,'2024-08-01 00:00:00.000','2024-08-01 00:00:00.000','2024-08-01 00:00:00.000'),
                  (502,'poll_results','execute','succeeded',1,1,0,0,'c1000000-0000-0000-0000-000000000002',
                   'manual',7,'cleanup marker',JSON_OBJECT(),UNHEX(REPEAT('77',32)),1,'retention-p0-v1','wp8-test',
                   1,1,0,0,0,'2025-08-14 00:00:00.000','2025-08-14 00:00:00.000','2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_operation
                  (operation_id,origin_run_group_id,domain,operation_type,batch_key_hash,hash_schema_version,item_count,
                   poll_project_id,poll_batch_date,poll_question_tier,poll_retention_cutoff_date,
                   config_fingerprint_schema_version,config_fingerprint,policy_version,created_application_version,
                   outcome_status,outcome_finalized_at,outcome_finalization_source,outcome_finalization_reason,
                   detail_retention_anchor_at,detail_purged_at,detail_purged_item_count,created_at)
                VALUES
                  ('c0000000-0000-0000-0000-000000000001',501,'poll_results','poll_slice',UNHEX(REPEAT('68',32)),1,1,
                   81,'2024-01-01','default','2024-07-31',1,UNHEX(REPEAT('66',32)),'retention-p0-v1','wp8-test',
                   'committed','2024-08-01 00:00:00.000','poll_marker','purged','2024-08-01 00:00:00.000',
                   '2025-08-02 00:00:00.000',0,'2024-08-01 00:00:00.000'),
                  ('c0000000-0000-0000-0000-000000000002',502,'poll_results','poll_preserved_cleanup',UNHEX(REPEAT('78',32)),1,1,
                   81,'2024-01-01','default','2024-07-31',1,UNHEX(REPEAT('77',32)),'retention-p0-v1','wp8-test',
                   'committed','2025-08-14 00:00:00.000','poll_cleanup','cleaned','2025-08-14 00:00:00.000',
                   NULL,NULL,'2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_operation_run
                  (operation_id,run_group_id,domain,config_fingerprint_schema_version,config_fingerprint,policy_version,
                   relation_type,detail_purged_attempt_count,linked_at)
                VALUES
                  ('c0000000-0000-0000-0000-000000000001',501,'poll_results',1,UNHEX(REPEAT('66',32)),
                   'retention-p0-v1','origin',0,'2024-08-01 00:00:00.000'),
                  ('c0000000-0000-0000-0000-000000000002',502,'poll_results',1,UNHEX(REPEAT('77',32)),
                   'retention-p0-v1','origin',0,'2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_purged_slice
                  (domain,project_id,batch_date,question_tier,status,purged_at,retention_run_id,operation_id)
                VALUES ('poll_results',81,'2024-01-01','default','purged','2024-08-01 00:00:00.000',501,
                        'c0000000-0000-0000-0000-000000000001')
                """);
        jdbc.update("""
                INSERT INTO data_retention_batch_attempt
                  (id,run_group_id,operation_id,sequence_no,attempt_no,domain,attempt_type,status,created_at,finished_at)
                VALUES (503,502,'c0000000-0000-0000-0000-000000000002',1,1,'poll_results',
                        'poll_preserved_cleanup','succeeded','2025-08-14 00:00:00.000','2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_batch_item
                  (run_group_id,first_attempt_id,operation_id,domain,business_key,sequence_no,created_at)
                VALUES (502,503,'c0000000-0000-0000-0000-000000000002','poll_results',701,1,
                        '2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_poll_preserved_result
                  (origin_operation_id,poll_result_id,domain,project_id,batch_date,question_tier,preserved_at,
                   cleanup_status,cleanup_operation_id,cleaned_at)
                VALUES ('c0000000-0000-0000-0000-000000000001',701,'poll_results',81,'2024-01-01','default',
                        '2024-08-01 00:00:00.000','cleaned','c0000000-0000-0000-0000-000000000002',
                        '2025-08-14 00:00:00.000')
                """);
    }

    private void insertOperationlessRun() {
        jdbc.update("""
                INSERT INTO data_retention_run
                  (id,domain,mode,status,candidate_count,affected_count,skipped_count,warning_count,
                   trigger_id,trigger_source,requested_by,request_reason,scope_json,config_fingerprint,
                   config_fingerprint_schema_version,policy_version,application_version,attempt_count,
                   succeeded_attempt_count,failed_attempt_count,skipped_attempt_count,unknown_attempt_count,
                   attempt_rollup_completed_at,started_at,finished_at)
                VALUES (301,'website_published_content','execute','failed',0,0,0,1,
                        'a0000000-0000-0000-0000-000000000001','manual',7,'operationless fixture',JSON_OBJECT(),
                        UNHEX(REPEAT('33',32)),1,'retention-p0-v1','wp8-test',1,0,1,0,0,
                        '2025-08-14 00:00:00.000','2025-08-14 00:00:00.000','2025-08-14 00:00:00.000')
                """);
        jdbc.update("""
                INSERT INTO data_retention_batch_attempt
                  (id,run_group_id,operation_id,sequence_no,attempt_no,domain,attempt_type,status,created_at,finished_at)
                VALUES (302,301,NULL,1,1,'website_published_content','reconcile_only','failed',
                        '2025-08-14 00:00:00.000','2025-08-14 00:00:00.000')
                """);
    }

    private int columnCount(String table, String column) {
        return count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='" + table + "' AND column_name='" + column + "'");
    }
    private int count(String sql) { Integer value = jdbc.queryForObject(sql, Integer.class); return value == null ? 0 : value; }

    private boolean configureDatabase() throws Exception {
        String external = System.getenv("RETENTION_MYSQL_IT_SERVER_URL");
        if (external != null && !external.isBlank()) {
            serverUrl=external; username=env("RETENTION_MYSQL_IT_USER","root"); password=env("RETENTION_MYSQL_IT_PASSWORD","");
            databaseName="geo_retention_wp8_it_"+UUID.randomUUID().toString().replace("-","");
            try (Connection c=DriverManager.getConnection(serverUrl,username,password); Statement s=c.createStatement()) {
                s.execute("CREATE DATABASE `"+databaseName+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            String url=serverUrl.replaceFirst("/\\?","/"+databaseName+"?");
            if (url.equals(serverUrl)) url=(serverUrl.endsWith("/")?serverUrl:serverUrl+"/")+databaseName+(serverUrl.contains("?")?"&":"?")+"useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            dataSource=new DriverManagerDataSource(url,username,password); return true;
        }
        boolean docker;
        try { docker= DockerClientFactory.instance().isDockerAvailable(); } catch (RuntimeException error) { docker=false; }
        if (!docker) {
            if (Boolean.getBoolean(REQUIRED)) throw new IllegalStateException("Required WP8 MySQL 8 is unavailable");
            return false;
        }
        container=new MySQLContainer<>("mysql:8.0.36").withDatabaseName("geo_retention_wp8_it")
                .withUsername("retention").withPassword(UUID.randomUUID().toString());
        container.start(); dataSource=new DriverManagerDataSource(container.getJdbcUrl(),container.getUsername(),container.getPassword()); return true;
    }

    private void createTable(Connection connection,String migration,String prefix) throws Exception {
        String text= Files.readString(MIGRATIONS.resolve(migration)); int start=text.indexOf(prefix); int end=text.indexOf(';',start);
        if(start<0||end<0) throw new IllegalStateException("missing DDL "+prefix);
        try(Statement statement=connection.createStatement()){ statement.execute(text.substring(start,end)); }
    }
    private static String env(String name,String fallback){String value=System.getenv(name);return value==null||value.isBlank()?fallback:value;}
}
