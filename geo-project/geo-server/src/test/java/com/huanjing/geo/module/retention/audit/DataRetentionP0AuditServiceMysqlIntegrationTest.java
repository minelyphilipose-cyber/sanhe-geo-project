package com.huanjing.geo.module.retention.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AuditOnlyReconciliationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.EvidenceDocument;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollCommitCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollCleanupCommitCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollCleanupOperationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.PollOperationCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.OperationReplayStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.ReconciliationPrepareCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RelationType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptResult;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionOperationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionRunStartCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunReplayStatus;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RunBusinessSummary;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteCommitCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteOperationCommand;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.audit.support.DataRetentionApplicationVersionProvider;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditClock;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditErrors;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditException;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditJsonCodec;
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
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WEBSITE_DOMAIN;
import static com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.POLL_DOMAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class DataRetentionP0AuditServiceMysqlIntegrationTest {

    private static final String MYSQL_IMAGE = "mysql:8.0.36";
    private static final String REQUIRED_GATE_PROPERTY = "retention.mysql-it.required";
    private static final String UNAVAILABLE_REASON =
            "Docker is unavailable and RETENTION_MYSQL_IT_SERVER_URL is not configured";
    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration").toAbsolutePath();
    private static final Instant NOW = Instant.parse("2026-08-12T05:00:00Z");

    private MySQLContainer<?> container;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;
    private boolean databaseReady;
    private String externalServerUrl;
    private String externalDatabaseName;
    private String externalUsername;
    private String externalPassword;

    @BeforeAll
    void configureMysql8() throws Exception {
        databaseReady = configureDatabase();
    }

    @BeforeEach
    void installV354Fixture() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        try (var connection = dataSource.getConnection()) {
            executeCreateTable(connection, "V228__data_lifecycle_summary_and_run.sql",
                    "CREATE TABLE IF NOT EXISTS data_retention_run (");
            executeCreateTable(connection, "V228__data_lifecycle_summary_and_run.sql",
                    "CREATE TABLE IF NOT EXISTS data_retention_purged_slice (");
            executeCreateTable(connection, "V228__data_lifecycle_summary_and_run.sql",
                    "CREATE TABLE IF NOT EXISTS data_retention_recompute_slice_lock (");
            executeCreateTable(connection, "V313__web_search_question_poll_foundation.sql",
                    "CREATE TABLE poll_audit_purge_runs (");
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    MIGRATION_DIRECTORY.resolve("V335__align_poll_retention_slice_collation.sql")));
        }
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + MIGRATION_DIRECTORY.toString().replace('\\', '/'))
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("353"))
                .target(MigrationVersion.fromVersion("354"))
                .load()
                .migrate();
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    MIGRATION_DIRECTORY.resolve("V367__poll_retention_execute_and_preserved_cleanup.sql")));
        }
        jdbc = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
    }

    @AfterAll
    void releaseMysql8() throws Exception {
        if (container != null) {
            container.stop();
        }
        if (externalDatabaseName != null) {
            try (var connection = java.sql.DriverManager.getConnection(
                    externalServerUrl, externalUsername, externalPassword);
                 var statement = connection.createStatement()) {
                if (!externalDatabaseName.startsWith("geo_retention_wp3_it_")) {
                    throw new IllegalStateException("Refusing to drop unexpected database");
                }
                statement.execute("DROP DATABASE IF EXISTS `" + externalDatabaseName + "`");
            }
        }
    }

    @Test
    void supportsCrossDeploymentReplayRetryAndMandatoryMarkerTransaction() throws Exception {
        RetentionCompatibilityContract contract = websiteContract();
        RetentionRunStartCommand originCommand = runCommand(
                "10000000-0000-0000-0000-000000000001", "manual", contract);

        DataRetentionP0AuditService v1 = service("v1");
        RetentionRunContext created = v1.startRun(originCommand);
        assertEquals(RunReplayStatus.CREATED, created.replayStatus());

        DataRetentionP0AuditService v2 = service("v2");
        RetentionRunContext replay = v2.startRun(originCommand);
        assertEquals(created.runId(), replay.runId());
        assertEquals("v1", replay.runCreatedByVersion());
        assertEquals("v2", replay.currentWriterVersion());
        assertEquals(RunReplayStatus.RUNNING_REPLAY, replay.replayStatus());

        RetentionAttemptCommand firstCommand = attempt(
                "20000000-0000-0000-0000-000000000001", AttemptType.NORMAL_BATCH, null, null);
        RetentionOperationContext operation = v2.prepareWebsiteOrigin(replay,
                new WebsiteOperationCommand(List.of(17L, 11L), NOW.minusSeconds(3600), 17L), firstCommand);
        assertEquals(OperationReplayStatus.CREATED, operation.replayStatus());
        assertEquals(List.of(11L, 17L), operation.websiteArticleIds());
        v2.markAttemptRunning(operation.firstAttempt().attemptId());
        v2.completeAttempt(operation.firstAttempt().attemptId(), new RetentionAttemptResult(
                AttemptStatus.FAILED, 2, 0, 0, 1, Map.of("deleted", 0),
                "DELETE_ROLLED_BACK", "fixture rollback", NOW));

        DataRetentionP0AuditService v3 = service("v3");
        RetentionRunContext retryRun = v3.startRun(runCommand(
                "10000000-0000-0000-0000-000000000002", "retry", contract));
        RetentionAttemptContext retry = v3.prepareRelatedAttempt(retryRun, operation.operationId(),
                RelationType.RETRY, attempt("20000000-0000-0000-0000-000000000002", AttemptType.RETRY,
                        operation.firstAttempt().attemptId(), operation.firstAttempt().attemptId()));
        v3.markAttemptRunning(retry.attemptId());

        DataRetentionAuditException noTransaction = assertThrows(DataRetentionAuditException.class,
                () -> v3.commitWebsiteMutation(new WebsiteCommitCommand(
                        operation, retryRun.runId(), retry.attemptId(), NOW.plusSeconds(1))));
        assertEquals(DataRetentionAuditErrors.MARKER_TRANSACTION_REQUIRED, noTransaction.getErrorCode());
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_batch_commit"));

        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        DataRetentionAuditException readOnlyFailure = assertThrows(DataRetentionAuditException.class,
                () -> readOnly.executeWithoutResult(status ->
                        v3.commitWebsiteMutation(new WebsiteCommitCommand(
                                operation, retryRun.runId(), retry.attemptId(), NOW.plusSeconds(1)))));
        assertEquals(DataRetentionAuditErrors.MARKER_TRANSACTION_REQUIRED, readOnlyFailure.getErrorCode());
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_batch_commit"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                v3.commitWebsiteMutation(new WebsiteCommitCommand(
                        operation, retryRun.runId(), retry.attemptId(), NOW.plusSeconds(1))));

        assertEquals("v1", scalar("SELECT application_version FROM data_retention_run WHERE id = ?", created.runId()));
        assertEquals("v2", scalar("SELECT created_application_version FROM data_retention_operation WHERE operation_id = ?",
                operation.operationId()));
        assertEquals("v3", scalar("SELECT application_version FROM data_retention_run WHERE id = ?", retryRun.runId()));
        assertEquals("v3", scalar("SELECT committed_application_version FROM data_retention_batch_commit WHERE operation_id = ?",
                operation.operationId()));
        assertEquals("committed", scalar("SELECT outcome_status FROM data_retention_operation WHERE operation_id = ?",
                operation.operationId()));
        assertEquals("succeeded", scalar("SELECT status FROM data_retention_batch_attempt WHERE id = ?", retry.attemptId()));
        assertEquals(2, count("SELECT COUNT(*) FROM data_retention_batch_item WHERE operation_id = ?", operation.operationId()));

        RetentionOperationContext terminalOperation = v2.prepareWebsiteOrigin(replay,
                new WebsiteOperationCommand(List.of(17L, 11L), NOW.minusSeconds(3600), 17L), firstCommand);
        assertEquals(OperationReplayStatus.TERMINAL_REPLAY, terminalOperation.replayStatus());
        assertEquals("committed", terminalOperation.outcomeStatus());
        assertEquals(operation.operationId(), terminalOperation.operationId());

        v2.finishRun(replay.runId(), new RunBusinessSummary(2, 0, 0, 1, Map.of("origin", true)));
        RetentionRunContext terminalOriginRun = service("v4").startRun(originCommand);
        assertEquals(RunReplayStatus.TERMINAL_REPLAY, terminalOriginRun.replayStatus());
        RetentionOperationContext terminalAfterRunFinish = service("v4").prepareWebsiteOrigin(terminalOriginRun,
                new WebsiteOperationCommand(List.of(17L, 11L), NOW.minusSeconds(3600), 17L), firstCommand);
        assertEquals(OperationReplayStatus.TERMINAL_REPLAY, terminalAfterRunFinish.replayStatus());
        assertThrows(DataRetentionAuditException.class, () -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> service("v4").commitWebsiteMutation(new WebsiteCommitCommand(
                        terminalAfterRunFinish, retryRun.runId(), retry.attemptId(), NOW.plusSeconds(2)))));

        v3.finishRun(retryRun.runId(), new RunBusinessSummary(2, 2, 0, 0, Map.of("batches", 1)));
        assertEquals("succeeded", scalar("SELECT status FROM data_retention_run WHERE id = ?", retryRun.runId()));
        assertEquals(1, count("SELECT succeeded_attempt_count FROM data_retention_run WHERE id = ?", retryRun.runId()));

        RetentionRunContext terminalReplay = service("v4").startRun(runCommand(
                "10000000-0000-0000-0000-000000000002", "retry", contract));
        assertEquals(RunReplayStatus.TERMINAL_REPLAY, terminalReplay.replayStatus());
        assertThrows(DataRetentionAuditException.class,
                () -> service("v4").prepareRelatedAttempt(terminalReplay, operation.operationId(), RelationType.RETRY,
                        attempt("20000000-0000-0000-0000-000000000003", AttemptType.RETRY,
                                retry.attemptId(), retry.attemptId())));
    }

    @Test
    void createsFirstAuditOnlyReconciliationAfterDetailPurgeWithoutAttemptsOrAnchorChange() throws Exception {
        DataRetentionP0AuditService service = service("audit-only-v1");
        RetentionCompatibilityContract contract = websiteContract();
        RetentionRunContext origin = service.startRun(runCommand(
                "60000000-0000-0000-0000-000000000001", "manual", contract));
        RetentionOperationContext operation = service.prepareWebsiteOrigin(origin,
                new WebsiteOperationCommand(List.of(41L), NOW.minusSeconds(370L * 86400), 41L),
                attempt("70000000-0000-0000-0000-000000000001", AttemptType.NORMAL_BATCH, null, null));
        service.markAttemptRunning(operation.firstAttempt().attemptId());
        Instant historicalCommit = NOW.minusSeconds(366L * 86400);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                service.commitWebsiteMutation(new WebsiteCommitCommand(
                        operation, origin.runId(), operation.firstAttempt().attemptId(), historicalCommit)));
        Object anchor = scalar("SELECT detail_retention_anchor_at FROM data_retention_operation WHERE operation_id = ?",
                operation.operationId());

        jdbc.update("DELETE FROM data_retention_batch_item WHERE operation_id = ?", operation.operationId());
        jdbc.update("DELETE FROM data_retention_batch_attempt WHERE operation_id = ?", operation.operationId());
        jdbc.update("UPDATE data_retention_operation SET detail_purged_at = ? WHERE operation_id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)), operation.operationId());

        String evidenceJson = "{\"marker\":\"present\",\"outcome\":\"committed\"}";
        byte[] evidenceBytes = evidenceJson.getBytes(StandardCharsets.UTF_8);
        RetentionRunStartCommand auditRunCommand = runCommand(
                "60000000-0000-0000-0000-000000000002", "reconcile", contract);
        var reconciliation = service("audit-only-v2").recordAuditOnlyReconciliation(
                new AuditOnlyReconciliationCommand(auditRunCommand, operation.operationId(),
                        "70000000-0000-0000-0000-000000000002", "present", "committed", "committed",
                        new EvidenceDocument(evidenceBytes, sha256(evidenceBytes)), 7L,
                        "marker and durable business state agree", 7L, NOW));

        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE run_group_id = ?",
                reconciliation.runId()));
        assertEquals("reconcile", scalar("""
                SELECT relation_type FROM data_retention_operation_run
                 WHERE operation_id = ? AND run_group_id = ?
                """, operation.operationId(), reconciliation.runId()));
        assertEquals(anchor, scalar("SELECT detail_retention_anchor_at FROM data_retention_operation WHERE operation_id = ?",
                operation.operationId()));

        service("audit-only-v2").finishRun(reconciliation.runId(),
                new RunBusinessSummary(0, 0, 0, 0, Map.of("auditOnly", true)));
        assertEquals("succeeded", scalar("SELECT status FROM data_retention_run WHERE id = ?", reconciliation.runId()));
        assertEquals(0, count("SELECT attempt_count FROM data_retention_run WHERE id = ?", reconciliation.runId()));

        var terminalReplay = service("audit-only-v3").recordAuditOnlyReconciliation(
                new AuditOnlyReconciliationCommand(auditRunCommand, operation.operationId(),
                        "70000000-0000-0000-0000-000000000002", "present", "committed", "committed",
                        new EvidenceDocument(evidenceBytes, sha256(evidenceBytes)), 7L,
                        "marker and durable business state agree", 7L, NOW));
        assertEquals(RunReplayStatus.TERMINAL_REPLAY, terminalReplay.runContext().replayStatus());
    }

    @Test
    void rejectsRunWindowConflictAndAuditOnlyHalfOrFailedRuns() throws Exception {
        DataRetentionP0AuditService service = service("replay-guard-v1");
        RetentionCompatibilityContract contract = websiteContract();
        RetentionRunStartCommand original = runCommand(
                "6a000000-0000-0000-0000-000000000001", "manual", contract);
        service.startRun(original);
        RetentionRunStartCommand changedWindow = new RetentionRunStartCommand(
                original.triggerId(), original.triggerSource(), original.mode(), original.requestedBy(),
                original.requestReason(), original.approvalRef(), original.ticketRef(), original.compatibilityContract(),
                original.scope(), original.retentionWindowStart().minusDays(1), original.retentionWindowEnd(),
                original.startedAt());
        DataRetentionAuditException windowConflict = assertThrows(DataRetentionAuditException.class,
                () -> service.startRun(changedWindow));
        assertEquals(DataRetentionAuditErrors.RUN_IDEMPOTENCY_CONFLICT, windowConflict.getErrorCode());

        RetentionOperationContext operation = createDetailPurgedCommittedOperation(service, contract,
                "6a000000-0000-0000-0000-000000000002",
                "6b000000-0000-0000-0000-000000000001", 451L);
        byte[] evidenceBytes = "{\"marker\":\"present\"}".getBytes(StandardCharsets.UTF_8);

        RetentionRunStartCommand orphanRunCommand = runCommand(
                "6a000000-0000-0000-0000-000000000003", "reconcile", contract);
        service.startRun(orphanRunCommand);
        AuditOnlyReconciliationCommand orphanCommand = new AuditOnlyReconciliationCommand(orphanRunCommand,
                operation.operationId(), "6b000000-0000-0000-0000-000000000002", "present", "committed",
                "committed", new EvidenceDocument(evidenceBytes, sha256(evidenceBytes)), 7L,
                "complete evidence", 7L, NOW);
        DataRetentionAuditException orphan = assertThrows(DataRetentionAuditException.class,
                () -> service.recordAuditOnlyReconciliation(orphanCommand));
        assertEquals(DataRetentionAuditErrors.RECONCILIATION_CONFLICT, orphan.getErrorCode());
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_operation_run WHERE run_group_id = (SELECT id FROM data_retention_run WHERE trigger_id = ?)",
                orphanRunCommand.triggerId()));

        RetentionRunStartCommand failedRunCommand = runCommand(
                "6a000000-0000-0000-0000-000000000004", "reconcile", contract);
        RetentionRunContext failedRun = service.startRun(failedRunCommand);
        jdbc.update("UPDATE data_retention_run SET status = 'failed', finished_at = ?, attempt_rollup_completed_at = ? WHERE id = ?",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), failedRun.runId());
        DataRetentionAuditException failed = assertThrows(DataRetentionAuditException.class,
                () -> service.recordAuditOnlyReconciliation(new AuditOnlyReconciliationCommand(failedRunCommand,
                        operation.operationId(), "6b000000-0000-0000-0000-000000000003", "present", "committed",
                        "committed", new EvidenceDocument(evidenceBytes, sha256(evidenceBytes)), 7L,
                        "complete evidence", 7L, NOW)));
        assertEquals(DataRetentionAuditErrors.RECONCILIATION_CONFLICT, failed.getErrorCode());
    }

    @Test
    void enforcesRawAndMysqlEvidenceJsonLengthsIndependently() throws Exception {
        PreparedUnknown prepared = prepareUnknownOperation(service("evidence-v1"));

        String whitespacePadded = evidenceJson(8, 8192);
        prepared.service().prepareReconciliation(reconciliation(prepared, whitespacePadded,
                "30000000-0000-0000-0000-000000000001"));
        int storedLength = count("""
                SELECT OCTET_LENGTH(CAST(evidence_json AS CHAR))
                  FROM data_retention_unknown_outcome_reconciliation
                 WHERE reconciliation_id = ?
                """, "30000000-0000-0000-0000-000000000001");
        assertEquals(true, storedLength <= 8192);

        String compact = evidenceJson(0, 8192);
        DataRetentionAuditException rejected = assertThrows(DataRetentionAuditException.class,
                () -> prepared.service().prepareReconciliation(reconciliation(prepared, compact,
                        "30000000-0000-0000-0000-000000000002")));
        assertEquals(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID, rejected.getErrorCode());
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_unknown_outcome_reconciliation WHERE reconciliation_id = ?",
                "30000000-0000-0000-0000-000000000002"));

        String tooLarge = evidenceJson(0, 8193);
        DataRetentionAuditException rawRejected = assertThrows(DataRetentionAuditException.class,
                () -> prepared.service().prepareReconciliation(reconciliation(prepared, tooLarge,
                        "30000000-0000-0000-0000-000000000003")));
        assertEquals(DataRetentionAuditErrors.RECONCILIATION_EVIDENCE_INVALID, rawRejected.getErrorCode());
    }

    @Test
    void commitsPollMarkerAtomicallyAndRejectsTupleOrAttemptMismatch() {
        DataRetentionP0AuditService service = service("poll-v1");
        RetentionCompatibilityContract contract = pollContract();
        RetentionRunContext run = service.startRun(runCommand(
                "81000000-0000-0000-0000-000000000001", "manual", contract));
        RetentionOperationContext operation = service.preparePollOrigin(run,
                new PollOperationCommand(71L, LocalDate.of(2026, 7, 1), "a", LocalDate.of(2026, 7, 15)),
                attempt("82000000-0000-0000-0000-000000000001", AttemptType.NORMAL_BATCH, null, null));
        service.markAttemptRunning(operation.firstAttempt().attemptId());

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        DataRetentionAuditException mismatch = assertThrows(DataRetentionAuditException.class,
                () -> transaction.executeWithoutResult(status -> service.commitPollMutation(new PollCommitCommand(
                        operation, run.runId(), operation.firstAttempt().attemptId(), 71L,
                        LocalDate.of(2026, 7, 1), "B", Map.of(), NOW))));
        assertEquals(DataRetentionAuditErrors.MARKER_CONTRACT_MISMATCH, mismatch.getErrorCode());
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_purged_slice"));

        assertThrows(RuntimeException.class, () -> transaction.executeWithoutResult(status -> {
            service.commitPollMutation(new PollCommitCommand(operation, run.runId(),
                    operation.firstAttempt().attemptId(), 71L, LocalDate.of(2026, 7, 1), "A",
                    Map.of("sourceRows", 1), NOW));
            throw new IllegalStateException("injected after poll marker");
        }));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_purged_slice"));
        assertEquals("pending", scalar("SELECT outcome_status FROM data_retention_operation WHERE operation_id = ?",
                operation.operationId()));
        assertEquals("running", scalar("SELECT status FROM data_retention_batch_attempt WHERE id = ?",
                operation.firstAttempt().attemptId()));

        transaction.executeWithoutResult(status -> service.commitPollMutation(new PollCommitCommand(operation,
                run.runId(), operation.firstAttempt().attemptId(), 71L, LocalDate.of(2026, 7, 1), "A",
                Map.of("sourceRows", 1), NOW)));
        assertEquals("purged", scalar("SELECT status FROM data_retention_purged_slice WHERE operation_id = ?",
                operation.operationId()));
        assertEquals("committed", scalar("SELECT outcome_status FROM data_retention_operation WHERE operation_id = ?",
                operation.operationId()));
        assertEquals("succeeded", scalar("SELECT status FROM data_retention_batch_attempt WHERE id = ?",
                operation.firstAttempt().attemptId()));
    }

    @Test
    void atomicallyOwnsAndCommitsPreservedPollCleanupItems() {
        DataRetentionP0AuditService service = service("poll-v2");
        RetentionCompatibilityContract contract = pollContract();
        RetentionRunContext primaryRun = service.startRun(runCommand(
                "81000000-0000-0000-0000-000000000011", "manual", contract));
        RetentionOperationContext primary = service.preparePollOrigin(primaryRun,
                new PollOperationCommand(71L, LocalDate.of(2026, 7, 1), "A", LocalDate.of(2026, 7, 15)),
                attempt("82000000-0000-0000-0000-000000000011", AttemptType.NORMAL_BATCH, null, null));
        service.markAttemptRunning(primary.firstAttempt().attemptId());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> service.commitPollMutation(new PollCommitCommand(
                primary, primaryRun.runId(), primary.firstAttempt().attemptId(), 71L,
                LocalDate.of(2026, 7, 1), "A", List.of(501L, 502L), Map.of("sourceRows", 2), NOW)));

        RetentionRunContext cleanupRun = service.startRun(runCommand(
                "81000000-0000-0000-0000-000000000012", "manual", contract));
        RetentionOperationContext cleanup = service.preparePollCleanupOrigin(cleanupRun,
                new PollCleanupOperationCommand(primary.operationId(), 71L, LocalDate.of(2026, 7, 1),
                        "A", LocalDate.of(2026, 7, 15), List.of(501L, 502L)),
                attempt("82000000-0000-0000-0000-000000000012", AttemptType.NORMAL_BATCH, null, null));
        assertEquals(2, count("SELECT COUNT(*) FROM data_retention_batch_item WHERE operation_id = ?",
                cleanup.operationId()));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_poll_preserved_result "
                + "WHERE cleanup_operation_id IS NOT NULL"));

        service.markAttemptRunning(cleanup.firstAttempt().attemptId());
        transaction.executeWithoutResult(status -> service.commitPollCleanup(new PollCleanupCommitCommand(
                cleanup, cleanupRun.runId(), cleanup.firstAttempt().attemptId(), primary.operationId(),
                List.of(501L, 502L), Map.of("deletedRows", 2), NOW)));
        assertEquals(2, count("SELECT COUNT(*) FROM data_retention_poll_preserved_result "
                + "WHERE cleanup_status = 'cleaned' AND cleanup_operation_id = ?", cleanup.operationId()));
        assertEquals("committed", scalar("SELECT outcome_status FROM data_retention_operation WHERE operation_id = ?",
                cleanup.operationId()));
        assertEquals("poll_cleanup", scalar("SELECT outcome_finalization_source "
                + "FROM data_retention_operation WHERE operation_id = ?", cleanup.operationId()));
    }

    @Test
    void rejectsCorruptedOriginReplayAndRelatedAttemptContractMatrix() {
        DataRetentionP0AuditService service = service("contract-v1");
        RetentionCompatibilityContract contract = websiteContract();
        RetentionRunContext origin = service.startRun(runCommand(
                "83000000-0000-0000-0000-000000000001", "manual", contract));
        WebsiteOperationCommand scope = new WebsiteOperationCommand(List.of(101L, 102L), NOW.minusSeconds(30), 102L);
        RetentionAttemptCommand first = attempt("84000000-0000-0000-0000-000000000001",
                AttemptType.NORMAL_BATCH, null, null);
        RetentionOperationContext operation = service.prepareWebsiteOrigin(origin, scope, first);

        jdbc.update("UPDATE data_retention_batch_item SET business_key = 999 WHERE operation_id = ? AND sequence_no = 2",
                operation.operationId());
        DataRetentionAuditException corruptedTree = assertThrows(DataRetentionAuditException.class,
                () -> service.prepareWebsiteOrigin(origin, scope, first));
        assertEquals(DataRetentionAuditErrors.OPERATION_SCOPE_CONFLICT, corruptedTree.getErrorCode());

        service.markAttemptRunning(operation.firstAttempt().attemptId());
        service.completeAttempt(operation.firstAttempt().attemptId(), new RetentionAttemptResult(
                AttemptStatus.FAILED, 2, 0, 0, 1, Map.of(), "ROLLBACK", "rolled back", NOW));
        RetentionRunContext manualRun = service.startRun(runCommand(
                "83000000-0000-0000-0000-000000000002", "manual", contract));
        DataRetentionAuditException wrongSource = assertThrows(DataRetentionAuditException.class,
                () -> service.prepareRelatedAttempt(manualRun, operation.operationId(), RelationType.RETRY,
                        attempt("84000000-0000-0000-0000-000000000002", AttemptType.RETRY,
                                operation.firstAttempt().attemptId(), operation.firstAttempt().attemptId())));
        assertEquals(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT, wrongSource.getErrorCode());

        RetentionRunContext retryRun = service.startRun(runCommand(
                "83000000-0000-0000-0000-000000000003", "retry", contract));
        DataRetentionAuditException wrongType = assertThrows(DataRetentionAuditException.class,
                () -> service.prepareRelatedAttempt(retryRun, operation.operationId(), RelationType.RETRY,
                        attempt("84000000-0000-0000-0000-000000000003", AttemptType.RECONCILE,
                                operation.firstAttempt().attemptId(), operation.firstAttempt().attemptId())));
        assertEquals(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT, wrongType.getErrorCode());

        jdbc.update("UPDATE data_retention_run SET scope_json = JSON_SET(scope_json, '$.effectiveConfig.retentionHours', 25) WHERE id = ?",
                origin.runId());
        DataRetentionAuditException changedConfig = assertThrows(DataRetentionAuditException.class,
                () -> service.prepareRelatedAttempt(retryRun, operation.operationId(), RelationType.RETRY,
                        attempt("84000000-0000-0000-0000-000000000004", AttemptType.RETRY,
                                operation.firstAttempt().attemptId(), operation.firstAttempt().attemptId())));
        assertEquals(DataRetentionAuditErrors.COMPATIBILITY_UNSUPPORTED, changedConfig.getErrorCode());
        assertEquals("rolled_back", scalar("SELECT outcome_status FROM data_retention_operation WHERE operation_id = ?",
                operation.operationId()));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_operation_run WHERE run_group_id = ?", retryRun.runId()));
    }

    @Test
    void rollsBackOriginAndRetryFaultsAndRejectsRetryHalfStates() {
        DataRetentionP0AuditService service = service("fault-v1");
        RetentionCompatibilityContract contract = websiteContract();
        RetentionRunContext origin = service.startRun(runCommand(
                "8a000000-0000-0000-0000-000000000001", "manual", contract));

        jdbc.execute("""
                CREATE TRIGGER trg_wp3_inject_origin_relation
                BEFORE INSERT ON data_retention_operation_run FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected origin relation failure'
                """);
        try {
            assertThrows(RuntimeException.class, () -> service.prepareWebsiteOrigin(origin,
                    new WebsiteOperationCommand(List.of(301L), NOW.minusSeconds(30), 301L),
                    attempt("8b000000-0000-0000-0000-000000000001",
                            AttemptType.NORMAL_BATCH, null, null)));
        } finally {
            jdbc.execute("DROP TRIGGER trg_wp3_inject_origin_relation");
        }
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_operation WHERE origin_run_group_id = ?",
                origin.runId()));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_operation_run WHERE run_group_id = ?",
                origin.runId()));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE run_group_id = ?",
                origin.runId()));

        jdbc.execute("""
                CREATE TRIGGER trg_wp3_inject_origin_attempt
                BEFORE INSERT ON data_retention_batch_attempt FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected origin attempt failure'
                """);
        try {
            assertThrows(RuntimeException.class, () -> service.prepareWebsiteOrigin(origin,
                    new WebsiteOperationCommand(List.of(303L), NOW.minusSeconds(30), 303L),
                    attempt("8b000000-0000-0000-0000-000000000004",
                            AttemptType.NORMAL_BATCH, null, null)));
        } finally {
            jdbc.execute("DROP TRIGGER trg_wp3_inject_origin_attempt");
        }
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_operation WHERE origin_run_group_id = ?",
                origin.runId()));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_operation_run WHERE run_group_id = ?",
                origin.runId()));

        jdbc.execute("""
                CREATE TRIGGER trg_wp3_inject_origin_second_item
                BEFORE INSERT ON data_retention_batch_item FOR EACH ROW
                BEGIN
                  IF NEW.sequence_no = 2 THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected origin item failure';
                  END IF;
                END
                """);
        try {
            assertThrows(RuntimeException.class, () -> service.prepareWebsiteOrigin(origin,
                    new WebsiteOperationCommand(List.of(304L, 305L), NOW.minusSeconds(30), 305L),
                    attempt("8b000000-0000-0000-0000-000000000005",
                            AttemptType.NORMAL_BATCH, null, null)));
        } finally {
            jdbc.execute("DROP TRIGGER trg_wp3_inject_origin_second_item");
        }
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_operation WHERE origin_run_group_id = ?",
                origin.runId()));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE run_group_id = ?",
                origin.runId()));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_batch_item WHERE run_group_id = ?",
                origin.runId()));

        RetentionOperationContext operation = service.prepareWebsiteOrigin(origin,
                new WebsiteOperationCommand(List.of(302L), NOW.minusSeconds(30), 302L),
                attempt("8b000000-0000-0000-0000-000000000002", AttemptType.NORMAL_BATCH, null, null));
        service.markAttemptRunning(operation.firstAttempt().attemptId());
        service.completeAttempt(operation.firstAttempt().attemptId(), new RetentionAttemptResult(
                AttemptStatus.FAILED, 1, 0, 0, 1, Map.of(), "ROLLBACK", "fixture", NOW));
        RetentionRunContext retry = service.startRun(runCommand(
                "8a000000-0000-0000-0000-000000000002", "retry", contract));
        RetentionAttemptCommand retryAttempt = attempt("8b000000-0000-0000-0000-000000000003",
                AttemptType.RETRY, operation.firstAttempt().attemptId(), operation.firstAttempt().attemptId());

        jdbc.execute("""
                CREATE TRIGGER trg_wp3_inject_retry_relation
                BEFORE INSERT ON data_retention_operation_run FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected retry relation failure'
                """);
        try {
            assertThrows(RuntimeException.class, () -> service.prepareRelatedAttempt(
                    retry, operation.operationId(), RelationType.RETRY, retryAttempt));
        } finally {
            jdbc.execute("DROP TRIGGER trg_wp3_inject_retry_relation");
        }
        assertEquals("rolled_back", scalar(
                "SELECT outcome_status FROM data_retention_operation WHERE operation_id = ?", operation.operationId()));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_operation_run WHERE run_group_id = ?", retry.runId()));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE run_group_id = ?", retry.runId()));

        jdbc.execute("""
                CREATE TRIGGER trg_wp3_inject_retry_attempt
                AFTER INSERT ON data_retention_batch_attempt FOR EACH ROW
                BEGIN
                  IF NEW.attempt_type = 'retry' THEN
                    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'injected retry attempt failure';
                  END IF;
                END
                """);
        try {
            assertThrows(RuntimeException.class, () -> service.prepareRelatedAttempt(
                    retry, operation.operationId(), RelationType.RETRY, retryAttempt));
        } finally {
            jdbc.execute("DROP TRIGGER trg_wp3_inject_retry_attempt");
        }
        assertEquals("rolled_back", scalar(
                "SELECT outcome_status FROM data_retention_operation WHERE operation_id = ?", operation.operationId()));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_operation_run WHERE run_group_id = ?", retry.runId()));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE run_group_id = ?", retry.runId()));

        jdbc.update("""
                INSERT INTO data_retention_operation_run (
                  operation_id, run_group_id, domain, config_fingerprint_schema_version,
                  config_fingerprint, policy_version, relation_type, linked_at, created_at
                )
                SELECT operation_id, ?, domain, config_fingerprint_schema_version,
                       config_fingerprint, policy_version, 'retry', ?, ?
                  FROM data_retention_operation WHERE operation_id = ?
                """, retry.runId(), LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), operation.operationId());
        DataRetentionAuditException relationOnly = assertThrows(DataRetentionAuditException.class,
                () -> service.prepareRelatedAttempt(retry, operation.operationId(), RelationType.RETRY, retryAttempt));
        assertEquals(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT, relationOnly.getErrorCode());
        assertEquals("rolled_back", scalar(
                "SELECT outcome_status FROM data_retention_operation WHERE operation_id = ?", operation.operationId()));

        jdbc.update("UPDATE data_retention_operation SET outcome_status = 'pending' WHERE operation_id = ?",
                operation.operationId());
        DataRetentionAuditException pendingWithoutAttempt = assertThrows(DataRetentionAuditException.class,
                () -> service.prepareRelatedAttempt(retry, operation.operationId(), RelationType.RETRY, retryAttempt));
        assertEquals(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT, pendingWithoutAttempt.getErrorCode());
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE run_group_id = ?", retry.runId()));

        jdbc.update("DELETE FROM data_retention_operation_run WHERE operation_id = ? AND run_group_id = ?",
                operation.operationId(), retry.runId());
        jdbc.update("UPDATE data_retention_operation SET outcome_status = 'rolled_back' WHERE operation_id = ?",
                operation.operationId());
        RetentionAttemptContext preparedRetry = service.prepareRelatedAttempt(
                retry, operation.operationId(), RelationType.RETRY, retryAttempt);
        jdbc.update("UPDATE data_retention_batch_attempt SET status = 'failed', finished_at = ? WHERE id = ?",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), preparedRetry.attemptId());
        DataRetentionAuditException terminalAttemptReplay = assertThrows(DataRetentionAuditException.class,
                () -> service.prepareRelatedAttempt(retry, operation.operationId(), RelationType.RETRY, retryAttempt));
        assertEquals(DataRetentionAuditErrors.RETRY_PREPARE_INCONSISTENT, terminalAttemptReplay.getErrorCode());
    }

    @Test
    void enforcesAttemptCasTerminalReplayAndRunSummaryProof() {
        DataRetentionP0AuditService service = service("attempt-v1");
        RetentionCompatibilityContract contract = websiteContract();
        RetentionRunContext run = service.startRun(runCommand(
                "85000000-0000-0000-0000-000000000001", "manual", contract));
        RetentionAttemptContext page = service.createOperationlessAttempt(run,
                attempt("86000000-0000-0000-0000-000000000001", AttemptType.PAGE, null, null));
        assertEquals(page.attemptId(), service.createOperationlessAttempt(run,
                attempt("86000000-0000-0000-0000-000000000001", AttemptType.PAGE, null, null)).attemptId());
        assertThrows(DataRetentionAuditException.class, () -> service.createOperationlessAttempt(run,
                attempt("86000000-0000-0000-0000-000000000002", AttemptType.PAGE, null, null)));

        RetentionAttemptResult implicitTime = new RetentionAttemptResult(
                AttemptStatus.FAILED, 5, 0, 0, 1, Map.of("page", 1), "READ_FAILED", "network", null);
        assertThrows(DataRetentionAuditException.class, () -> service.completeAttempt(page.attemptId(), implicitTime));
        service.markAttemptRunning(page.attemptId());
        service.completeAttempt(page.attemptId(), implicitTime);
        service.completeAttempt(page.attemptId(), implicitTime);
        assertNotNull(scalar("SELECT finished_at FROM data_retention_batch_attempt WHERE id = ?", page.attemptId()));
        assertThrows(DataRetentionAuditException.class, () -> service.completeAttempt(page.attemptId(),
                new RetentionAttemptResult(AttemptStatus.UNKNOWN, 5, 0, 0, 1,
                        Map.of("page", 1), "READ_FAILED", "network", null)));

        RetentionAttemptContext leaseBusy = service.createOperationlessAttempt(run,
                attempt("86000000-0000-0000-0000-000000000003", AttemptType.LEASE_BUSY,
                        page.attemptId(), page.attemptId()));
        service.completeAttempt(leaseBusy.attemptId(), new RetentionAttemptResult(
                AttemptStatus.SKIPPED, 0, 0, 1, 0, Map.of("reason", "lease_busy"), null, null, NOW));
        RetentionAttemptContext manual = service.createOperationlessAttempt(run,
                attempt("86000000-0000-0000-0000-000000000004", AttemptType.PAGE,
                        leaseBusy.attemptId(), leaseBusy.attemptId()));
        service.markAttemptRunning(manual.attemptId());
        service.completeAttempt(manual.attemptId(), new RetentionAttemptResult(
                AttemptStatus.MANUAL_REVIEW, 1, 0, 0, 1, Map.of("reason", "ambiguous"),
                "MANUAL_REVIEW", "operator review required", NOW));

        assertThrows(DataRetentionAuditException.class,
                () -> service.finishRun(run.runId(), new RunBusinessSummary(7, 0, 1, 2, Map.of())));
        service.finishRun(run.runId(), new RunBusinessSummary(6, 0, 1, 2, Map.of()));
        assertEquals(1, count("SELECT failed_attempt_count FROM data_retention_run WHERE id = ?", run.runId()));
        assertEquals(1, count("SELECT skipped_attempt_count FROM data_retention_run WHERE id = ?", run.runId()));
        assertEquals(1, count("SELECT unknown_attempt_count FROM data_retention_run WHERE id = ?", run.runId()));
        assertEquals(6, count("SELECT candidate_count FROM data_retention_run WHERE id = ?", run.runId()));
    }

    @Test
    void allocatesConcurrentRunSequenceAndSerializesFinish() throws Exception {
        DataRetentionP0AuditService service = service("concurrency-v1");
        RetentionRunContext run = service.startRun(runCommand(
                "87000000-0000-0000-0000-000000000001", "manual", websiteContract()));
        int workers = 6;
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workers);
        List<java.util.concurrent.Future<RetentionOperationContext>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            int index = i + 1;
            futures.add(executor.submit(() -> {
                start.await();
                long articleId = 1000L + index;
                return service.prepareWebsiteOrigin(run,
                        new WebsiteOperationCommand(List.of(articleId), NOW.minusSeconds(index), articleId),
                        attempt("88000000-0000-0000-0000-%012d".formatted(index),
                                AttemptType.NORMAL_BATCH, null, null));
            }));
        }
        start.countDown();
        for (var future : futures) {
            RetentionOperationContext operation = future.get(10, TimeUnit.SECONDS);
            service.markAttemptRunning(operation.firstAttempt().attemptId());
            service.completeAttempt(operation.firstAttempt().attemptId(), new RetentionAttemptResult(
                    AttemptStatus.FAILED, 1, 0, 0, 1, Map.of(), "ROLLBACK", "fixture", NOW));
        }
        assertEquals(workers, count("SELECT COUNT(DISTINCT sequence_no) FROM data_retention_batch_attempt WHERE run_group_id = ?",
                run.runId()));
        CountDownLatch finishStart = new CountDownLatch(1);
        var finishOne = executor.submit(() -> finish(service, run, workers, finishStart));
        var finishTwo = executor.submit(() -> finish(service, run, workers, finishStart));
        finishStart.countDown();
        int finishSuccesses = (finishOne.get(10, TimeUnit.SECONDS) ? 1 : 0)
                + (finishTwo.get(10, TimeUnit.SECONDS) ? 1 : 0);
        executor.shutdownNow();
        assertEquals(1, finishSuccesses);
        assertEquals("failed", scalar("SELECT status FROM data_retention_run WHERE id = ?", run.runId()));
    }

    @Test
    void persistsUtcWallTimeIndependentlyOfMysqlSessionTimezone() {
        DriverManagerDataSource base = (DriverManagerDataSource) dataSource;
        String separator = base.getUrl().contains("?") ? "&" : "?";
        DriverManagerDataSource timezoneSource = (DriverManagerDataSource) dataSource(
                base.getUrl() + separator + "sessionVariables=time_zone='%2B08:00'", base.getUsername(), base.getPassword());
        try {
            DataRetentionP0AuditService service = service("utc-v1", NOW, timezoneSource);
            RetentionRunContext run = service.startRun(runCommand(
                    "89000000-0000-0000-0000-000000000001", "manual", websiteContract()));
            Object stored = new JdbcTemplate(timezoneSource).queryForObject(
                    "SELECT DATE_FORMAT(started_at, '%Y-%m-%d %H:%i:%s.%f') FROM data_retention_run WHERE id = ?",
                    Object.class, run.runId());
            assertTrue(String.valueOf(stored).startsWith("2026-08-12 05:00:00.000"));
            RetentionRunContext replay = service.startRun(runCommand(
                    "89000000-0000-0000-0000-000000000001", "manual", websiteContract()));
            assertEquals(NOW, replay.startedAt());
        } finally {
            // Each DriverManagerDataSource call creates a fresh session; no global timezone state is changed.
        }
    }

    private boolean finish(DataRetentionP0AuditService service, RetentionRunContext run, int workers,
                           CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.finishRun(run.runId(), new RunBusinessSummary(workers, 0, 0, workers, Map.of()));
            return true;
        } catch (DataRetentionAuditException conflict) {
            return false;
        }
    }

    private PreparedUnknown prepareUnknownOperation(DataRetentionP0AuditService service) {
        RetentionCompatibilityContract contract = websiteContract();
        RetentionRunContext origin = service.startRun(runCommand(
                "40000000-0000-0000-0000-000000000001", "manual", contract));
        RetentionOperationContext operation = service.prepareWebsiteOrigin(origin,
                new WebsiteOperationCommand(List.of(31L), NOW.minusSeconds(7200), 31L),
                attempt("50000000-0000-0000-0000-000000000001", AttemptType.NORMAL_BATCH, null, null));
        service.markAttemptRunning(operation.firstAttempt().attemptId());
        service.completeAttempt(operation.firstAttempt().attemptId(), new RetentionAttemptResult(
                AttemptStatus.UNKNOWN, 1, 0, 0, 1, Map.of(),
                "CONNECTION_LOST", "unknown commit result", NOW));

        RetentionRunContext reconcile = service.startRun(runCommand(
                "40000000-0000-0000-0000-000000000002", "reconcile", contract));
        service.prepareRelatedAttempt(reconcile, operation.operationId(), RelationType.RECONCILE,
                attempt("50000000-0000-0000-0000-000000000002", AttemptType.RECONCILE,
                        operation.firstAttempt().attemptId(), operation.firstAttempt().attemptId()));
        return new PreparedUnknown(service, operation, reconcile);
    }

    private RetentionOperationContext createDetailPurgedCommittedOperation(DataRetentionP0AuditService service,
                                                                            RetentionCompatibilityContract contract,
                                                                            String triggerId,
                                                                            String attemptRequestId,
                                                                            long articleId) {
        RetentionRunContext origin = service.startRun(runCommand(triggerId, "manual", contract));
        Instant historicalCommit = NOW.minusSeconds(366L * 86400);
        RetentionOperationContext operation = service.prepareWebsiteOrigin(origin,
                new WebsiteOperationCommand(List.of(articleId), historicalCommit.minusSeconds(3600), articleId),
                attempt(attemptRequestId, AttemptType.NORMAL_BATCH, null, null));
        service.markAttemptRunning(operation.firstAttempt().attemptId());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                service.commitWebsiteMutation(new WebsiteCommitCommand(
                        operation, origin.runId(), operation.firstAttempt().attemptId(), historicalCommit)));
        jdbc.update("DELETE FROM data_retention_batch_item WHERE operation_id = ?", operation.operationId());
        jdbc.update("DELETE FROM data_retention_batch_attempt WHERE operation_id = ?", operation.operationId());
        jdbc.update("UPDATE data_retention_operation SET detail_purged_at = ? WHERE operation_id = ?",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), operation.operationId());
        return operation;
    }

    private ReconciliationPrepareCommand reconciliation(PreparedUnknown prepared, String json, String id) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new ReconciliationPrepareCommand(id, prepared.run(), prepared.operation().operationId(),
                "absent", "unknown", "manual_review", new EvidenceDocument(bytes, sha256(bytes)), 7L);
    }

    private DataRetentionP0AuditService service(String version) {
        return service(version, NOW, dataSource);
    }

    private DataRetentionP0AuditService service(String version, Instant now, DataSource source) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("geo.retention.audit.application-version", version);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        return new DataRetentionP0AuditService(new JdbcTemplate(source), new DataRetentionCanonicalEncoder(),
                new DataRetentionAuditJsonCodec(new ObjectMapper()),
                new DataRetentionApplicationVersionProvider(environment),
                new DataRetentionAuditClock(Clock.fixed(now, ZoneOffset.UTC)), manager);
    }

    private RetentionCompatibilityContract websiteContract() {
        byte[] fingerprint = new DataRetentionCanonicalEncoder().websiteFingerprint(null).sha256();
        return new RetentionCompatibilityContract(WEBSITE_DOMAIN, 1, fingerprint,
                DataRetentionCanonicalEncoder.WEBSITE_POLICY_VERSION);
    }

    private RetentionCompatibilityContract pollContract() {
        byte[] fingerprint = new DataRetentionCanonicalEncoder().pollFingerprint(null, null).sha256();
        return new RetentionCompatibilityContract(POLL_DOMAIN, 1, fingerprint,
                DataRetentionCanonicalEncoder.POLL_POLICY_VERSION);
    }

    private RetentionRunStartCommand runCommand(String triggerId, String source,
                                                RetentionCompatibilityContract contract) {
        return new RetentionRunStartCommand(triggerId, source, "execute", 7L, "WP3 integration fixture",
                "approved-WP3", null, contract,
                runScope(contract),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 12), NOW);
    }

    private Map<String, Object> runScope(RetentionCompatibilityContract contract) {
        DataRetentionCanonicalEncoder canonical = new DataRetentionCanonicalEncoder();
        Map<String, Object> effective = WEBSITE_DOMAIN.equals(contract.domain())
                ? canonical.websiteEffectiveConfig(null) : canonical.pollEffectiveConfig(null, null);
        return Map.of("effectiveConfig", effective,
                "configFingerprintSchemaVersion", contract.configFingerprintSchemaVersion(),
                "configFingerprint", DataRetentionCanonicalEncoder.hex(contract.configFingerprint()));
    }

    private RetentionAttemptCommand attempt(String requestId, AttemptType type,
                                             Long expectedLatest, Long lineageParent) {
        return new RetentionAttemptCommand(requestId, type, expectedLatest, lineageParent,
                Map.of("worker", "mysql-it"));
    }

    private String evidenceJson(int leadingSpaces, int totalBytes) {
        int valueLength = totalBytes - leadingSpaces - 8;
        if (valueLength < 0) {
            throw new IllegalArgumentException("evidence size is too small");
        }
        return " ".repeat(leadingSpaces) + "{\"v\":\"" + "a".repeat(valueLength) + "\"}";
    }

    private byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private Object scalar(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Object.class, arguments);
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private boolean configureDatabase() throws Exception {
        String serverUrl = System.getenv("RETENTION_MYSQL_IT_SERVER_URL");
        if (serverUrl != null && !serverUrl.isBlank()) {
            configureExternalDatabase(serverUrl);
            return true;
        }
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException unavailable) {
            dockerAvailable = false;
        }
        if (!dockerAvailable) {
            if (Boolean.getBoolean(REQUIRED_GATE_PROPERTY)) {
                throw new IllegalStateException("Required retention-mysql-it gate cannot run: " + UNAVAILABLE_REASON);
            }
            return false;
        }
        container = new MySQLContainer<>(MYSQL_IMAGE)
                .withDatabaseName("geo_retention_wp3_it")
                .withUsername("retention")
                .withPassword(UUID.randomUUID().toString());
        container.start();
        dataSource = dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        return true;
    }

    private void configureExternalDatabase(String serverUrl) throws Exception {
        externalServerUrl = serverUrl;
        externalUsername = environmentOrDefault("RETENTION_MYSQL_IT_USER", "root");
        externalPassword = environmentOrDefault("RETENTION_MYSQL_IT_PASSWORD", "");
        externalDatabaseName = "geo_retention_wp3_it_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = java.sql.DriverManager.getConnection(serverUrl, externalUsername, externalPassword);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + externalDatabaseName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        String separator = serverUrl.contains("?") ? "&" : "?";
        String databaseUrl = serverUrl.replaceFirst("/\\?", "/" + externalDatabaseName + "?");
        if (databaseUrl.equals(serverUrl)) {
            databaseUrl = serverUrl.endsWith("/") ? serverUrl + externalDatabaseName : serverUrl + "/" + externalDatabaseName;
            databaseUrl += separator + "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        }
        dataSource = dataSource(databaseUrl, externalUsername, externalPassword);
    }

    private DataSource dataSource(String url, String username, String password) {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(url);
        source.setUsername(username);
        source.setPassword(password);
        return source;
    }

    private void executeCreateTable(java.sql.Connection connection, String migrationName,
                                    String statementPrefix) throws Exception {
        String migration = Files.readString(MIGRATION_DIRECTORY.resolve(migrationName));
        int start = migration.indexOf(statementPrefix);
        int end = migration.indexOf(';', start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("Missing historical DDL: " + statementPrefix);
        }
        try (var statement = connection.createStatement()) {
            statement.execute(migration.substring(start, end));
        }
    }

    private String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record PreparedUnknown(DataRetentionP0AuditService service,
                                   RetentionOperationContext operation,
                                   RetentionRunContext run) {
    }
}
