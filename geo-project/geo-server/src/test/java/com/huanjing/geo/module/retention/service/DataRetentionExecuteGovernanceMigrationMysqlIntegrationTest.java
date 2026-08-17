package com.huanjing.geo.module.retention.service;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class DataRetentionExecuteGovernanceMigrationMysqlIntegrationTest {

    private static final String MYSQL_IMAGE = "mysql:8.0.36";
    private static final String REQUIRED_GATE_PROPERTY = "retention.mysql-it.required";
    private static final String CAPACITY_GATE_PROPERTY = "retention.mysql-it.capacity";
    private static final String UNAVAILABLE_REASON =
            "Docker is unavailable and RETENTION_MYSQL_IT_SERVER_URL is not configured";
    private static final Path MIGRATION_DIRECTORY = Path.of("src/main/resources/db/migration")
            .toAbsolutePath();

    private MySQLContainer<?> container;
    private DataSource dataSource;
    private boolean databaseReady;
    private String externalServerUrl;
    private String externalDatabaseName;
    private String externalUsername;
    private String externalPassword;

    @BeforeAll
    void configureMysql8() throws Exception {
        databaseReady = configureDatabase();
    }

    @AfterAll
    void releaseMysql8() throws Exception {
        if (container != null) {
            container.stop();
        }
        if (externalDatabaseName != null) {
            try (Connection connection = DriverManager.getConnection(
                    externalServerUrl, externalUsername, externalPassword);
                 Statement statement = connection.createStatement()) {
                if (!externalDatabaseName.startsWith("geo_retention_it_")) {
                    throw new IllegalStateException("Refusing to drop unexpected database");
                }
                statement.execute("DROP DATABASE IF EXISTS `" + externalDatabaseName + "`");
            }
        }
    }

    @Test
    void migratesExtractedRetentionBaselineAndPreservesRows() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);
        resetToExtractedRetentionBaseline();
        long historicalRunId = insertHistoricalRows();

        MigrateResult first = governanceFlyway().migrate();
        assertEquals(1, first.migrationsExecuted);
        assertEquals(8, mysqlMajorVersion());
        assertEquals(11, scalarInt("""
                SELECT COUNT(*)
                  FROM information_schema.tables
                 WHERE table_schema = DATABASE()
                   AND table_name IN (
                       'data_retention_operation', 'data_retention_operation_run',
                       'data_retention_batch_attempt', 'data_retention_batch_item',
                       'data_retention_batch_commit', 'data_retention_failure_group',
                       'data_retention_failure_group_item', 'data_retention_failure_active_key',
                       'data_retention_failure_regroup_history',
                       'data_retention_dynamic_switch_change',
                       'data_retention_unknown_outcome_reconciliation')
                """));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM data_retention_run WHERE id = " + historicalRunId));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM data_retention_purged_slice"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM poll_audit_purge_runs"));
        assertNull(scalar("SELECT trigger_id FROM data_retention_run WHERE id = " + historicalRunId));
        assertNull(scalar("SELECT operation_id FROM data_retention_purged_slice LIMIT 1"));
        assertNull(scalar("SELECT operation_id FROM poll_audit_purge_runs LIMIT 1"));
        assertEquals("utf8mb4_0900_ai_ci", scalar("""
                SELECT collation_name
                  FROM information_schema.columns
                 WHERE table_schema = DATABASE()
                   AND table_name = 'data_retention_operation'
                   AND column_name = 'poll_question_tier'
                """));
        assertEquals(18, scalarInt("""
                SELECT COUNT(*)
                  FROM information_schema.referential_constraints
                 WHERE constraint_schema = DATABASE()
                   AND constraint_name IN (
                       'fk_retention_operation_origin_run_domain',
                       'fk_retention_operation_run_operation_contract',
                       'fk_retention_operation_run_run_contract',
                       'fk_retention_attempt_run_domain',
                       'fk_retention_attempt_operation_identity',
                       'fk_retention_batch_item_operation_identity',
                       'fk_retention_batch_item_attempt',
                       'fk_retention_batch_commit_operation_run',
                       'fk_retention_batch_commit_contract',
                       'fk_retention_failure_group_operation',
                       'fk_retention_failure_item_group_domain',
                       'fk_retention_failure_active_ref',
                       'fk_retention_regroup_old_group',
                       'fk_retention_regroup_new_group',
                       'fk_retention_reconciliation_identity',
                       'fk_retention_purged_slice_operation_run',
                       'fk_retention_purged_slice_operation_scope',
                       'fk_poll_audit_purge_operation')
                   AND delete_rule = 'RESTRICT'
                """));
        assertEquals(0, scalarInt("""
                SELECT COUNT(*)
                  FROM information_schema.referential_constraints
                 WHERE constraint_schema = DATABASE()
                   AND delete_rule = 'CASCADE'
                   AND table_name LIKE 'data_retention%'
                """));
        assertEquals(0, scalarInt("""
                SELECT COUNT(*)
                  FROM information_schema.key_column_usage
                 WHERE constraint_schema = DATABASE()
                   AND referenced_table_name = 'data_retention_batch_attempt'
                   AND table_name <> 'data_retention_batch_item'
                """));

        MigrateResult second = governanceFlyway().migrate();
        assertEquals(0, second.migrationsExecuted);
    }

    @Test
    void recoversFromMysqlImplicitCommitWithFlywayRepair() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);
        resetToExtractedRetentionBaseline();
        execute("""
                CREATE TABLE data_retention_operation (
                    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        Flyway flyway = governanceFlyway();
        FlywayException failure = assertThrows(FlywayException.class, flyway::migrate);
        assertTrue(failure.getMessage().contains("V354")
                || failure.getMessage().contains("data_retention_operation"));
        assertTrue(columnExists("data_retention_run", "trigger_id"));
        assertEquals(1, scalarInt("""
                SELECT COUNT(*)
                  FROM flyway_schema_history
                 WHERE version = '354' AND success = 0
                """));

        execute("DROP TABLE data_retention_operation");
        flyway.repair();
        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(1, scalarInt("""
                SELECT COUNT(*)
                  FROM flyway_schema_history
                 WHERE version = '354' AND success = 1
                """));
    }

    @Test
    void rejectsSameShapeWrongColumnIndexAndCaseSensitiveCheckDefinitions() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);

        assertSameShapeDefinitionRejectedAndRepairable(
                """
                ALTER TABLE data_retention_operation
                  MODIFY created_application_version VARCHAR(63)
                    CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                """);

        assertSameShapeDefinitionRejectedAndRepairable(
                """
                ALTER TABLE data_retention_operation
                  DROP INDEX idx_retention_operation_status,
                  ADD KEY idx_retention_operation_status
                    (outcome_status, domain, created_at)
                """);

        assertSameShapeDefinitionRejectedAndRepairable(
                """
                ALTER TABLE data_retention_operation
                  DROP CHECK chk_retention_operation_hash_version,
                  ADD CONSTRAINT chk_retention_operation_hash_version
                    CHECK (hash_schema_version >= 0)
                """);

        assertSameShapeDefinitionRejectedAndRepairable(
                """
                ALTER TABLE data_retention_operation_run
                  DROP CHECK chk_retention_operation_run_relation_type,
                  ADD CONSTRAINT chk_retention_operation_run_relation_type
                    CHECK (relation_type IN ('ORIGIN','RETRY','RECONCILE'))
                """);
    }

    @Test
    void enforcesRunFingerprintPairWithoutMysqlUnknownLoophole() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);
        resetToExtractedRetentionBaseline();
        assertEquals(1, governanceFlyway().migrate().migrationsExecuted);

        execute("""
                INSERT INTO data_retention_run (domain, mode, status, started_at)
                VALUES ('poll_results', 'dry_run', 'succeeded', UTC_TIMESTAMP())
                """);
        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO data_retention_run
                    (domain, mode, status, config_fingerprint_schema_version, started_at)
                VALUES ('poll_results', 'dry_run', 'succeeded', 1, UTC_TIMESTAMP())
                """));
        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO data_retention_run
                    (domain, mode, status, config_fingerprint, started_at)
                VALUES ('poll_results', 'dry_run', 'succeeded',
                        UNHEX(REPEAT('ab', 32)), UTC_TIMESTAMP())
                """));
        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO data_retention_run
                    (domain, mode, status, config_fingerprint_schema_version,
                     config_fingerprint, started_at)
                VALUES ('poll_results', 'dry_run', 'succeeded', 0,
                        UNHEX(REPEAT('cd', 32)), UTC_TIMESTAMP())
                """));
        execute("""
                INSERT INTO data_retention_run
                    (domain, mode, status, config_fingerprint_schema_version,
                     config_fingerprint, started_at)
                VALUES ('poll_results', 'dry_run', 'succeeded', 1,
                        UNHEX(REPEAT('ef', 32)), UTC_TIMESTAMP())
                """);

        assertEquals(2, scalarInt("SELECT COUNT(*) FROM data_retention_run"));
    }

    @Test
    void documentsEmptySchemaHistoricalChainBlockAtV133() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);
        Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load()
                .clean();

        FlywayException failure = assertThrows(FlywayException.class,
                () -> fullHistoricalFlyway().migrate());
        String failureChain = exceptionChain(failure);
        assertTrue(failureChain.contains("V133")
                        || failureChain.contains("article_generation_tasks_ibfk_2"),
                failureChain);
        assertEquals(1, scalarInt("""
                SELECT COUNT(*)
                  FROM flyway_schema_history
                 WHERE version = '133' AND success = 0
                """));
    }

    @Test
    void enforcesCrossRunScopeAndAttemptBeforeItemContracts() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);
        resetToExtractedRetentionBaseline();
        assertEquals(1, governanceFlyway().migrate().migrationsExecuted);

        WebsiteFixture fixture = insertWebsiteFixture();
        assertEquals("created-v1", scalar("""
                SELECT created_application_version
                  FROM data_retention_operation
                 WHERE operation_id = '00000000-0000-0000-0000-000000000101'
                """));
        assertEquals("committed-v2", scalar("""
                SELECT committed_application_version
                  FROM data_retention_batch_commit
                 WHERE operation_id = '00000000-0000-0000-0000-000000000101'
                """));
        assertEquals(32, ((byte[]) scalar("""
                SELECT batch_key_hash
                  FROM data_retention_operation
                 WHERE operation_id = '00000000-0000-0000-0000-000000000101'
                """)).length);

        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO data_retention_operation_run
                    (operation_id, run_group_id, domain,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, relation_type, linked_at)
                VALUES
                    ('00000000-0000-0000-0000-000000000101', %d,
                     'website_published_content', 1,
                     UNHEX('5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd'),
                     'website-published-v1', 'origin', UTC_TIMESTAMP(3))
                """.formatted(fixture.retryRunId())));

        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO data_retention_batch_item
                    (run_group_id, first_attempt_id, operation_id, domain,
                     business_key, sequence_no)
                VALUES
                    (%d, 999999, '00000000-0000-0000-0000-000000000101',
                     'website_published_content', 205, 2)
                """.formatted(fixture.originRunId())));

        long retryAttemptId = insertAttempt(fixture.retryRunId(), 2, 2, "retry");
        assertTrue(retryAttemptId > fixture.firstAttemptId());
        assertThrows(SQLException.class,
                () -> execute("DELETE FROM data_retention_batch_attempt WHERE id = "
                        + fixture.firstAttemptId()));

        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO data_retention_batch_attempt
                    (run_group_id, operation_id, sequence_no, attempt_no, domain,
                     attempt_type, status, scope_json)
                VALUES
                    (%d, '00000000-0000-0000-0000-000000000101', 3, 3,
                     'website_published_content', 'retry', 'failed',
                     JSON_OBJECT('oversized', REPEAT('x', 8200)))
                """.formatted(fixture.retryRunId())));

        assertOperationLockSerializesCleanupAndWriter(fixture);
    }

    @Test
    void enforcesPollTupleFingerprintAndLifecycleChecks() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);
        resetToExtractedRetentionBaseline();
        assertEquals(1, governanceFlyway().migrate().migrationsExecuted);

        long runId = insertAndReturnId("""
                INSERT INTO data_retention_run
                    (domain, mode, status, trigger_id, trigger_source, requested_by,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, application_version, started_at)
                VALUES
                    ('poll_results', 'execute', 'succeeded',
                     '00000000-0000-0000-0000-000000000011', 'scheduler', 1,
                     1, UNHEX('a3d60a41c13633f7f40c1fbaf90e8086e19401b05efc4d658cc52b25b23d1b7c'),
                     'poll-results-v1', 'poll-v1', UTC_TIMESTAMP())
                """);
        execute("""
                INSERT INTO data_retention_operation
                    (operation_id, origin_run_group_id, domain, operation_type,
                     batch_key_hash, hash_schema_version, item_count,
                     poll_project_id, poll_batch_date, poll_question_tier,
                     poll_retention_cutoff_date, config_fingerprint_schema_version,
                     config_fingerprint, policy_version, created_application_version,
                     outcome_status, outcome_finalized_at,
                     outcome_finalization_source, outcome_finalization_reason,
                     detail_retention_anchor_at)
                VALUES
                    ('00000000-0000-0000-0000-000000000201', %d, 'poll_results',
                     'poll_slice',
                     UNHEX('2d6d6c4ccf5609e29a14773efc97125dcd7746e192be7bc1f163b274c06eedad'),
                     1, 1, 1, '2026-05-26', 'A', '2026-07-28', 1,
                     UNHEX('a3d60a41c13633f7f40c1fbaf90e8086e19401b05efc4d658cc52b25b23d1b7c'),
                     'poll-results-v1', 'poll-v1', 'committed', '2025-01-01 00:00:00.000',
                     'poll_marker', 'committed', '2025-01-01 00:00:00.000')
                """.formatted(runId));
        execute("""
                INSERT INTO data_retention_operation_run
                    (operation_id, run_group_id, domain,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, relation_type, linked_at)
                VALUES
                    ('00000000-0000-0000-0000-000000000201', %d, 'poll_results', 1,
                     UNHEX('a3d60a41c13633f7f40c1fbaf90e8086e19401b05efc4d658cc52b25b23d1b7c'),
                     'poll-results-v1', 'origin', UTC_TIMESTAMP(3))
                """.formatted(runId));

        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO data_retention_purged_slice
                    (domain, project_id, batch_date, question_tier,
                     retention_run_id, operation_id)
                VALUES
                    ('poll_results', 1, '2026-05-26', 'B', %d,
                     '00000000-0000-0000-0000-000000000201')
                """.formatted(runId)));
        execute("""
                INSERT INTO data_retention_purged_slice
                    (domain, project_id, batch_date, question_tier,
                     retention_run_id, operation_id)
                VALUES
                    ('poll_results', 1, '2026-05-26', 'A', %d,
                     '00000000-0000-0000-0000-000000000201')
                """.formatted(runId));

        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO poll_audit_purge_runs
                    (project_id, requested_by, purge_reason, scope_json,
                     audit_committed_at, retention_run_id)
                VALUES (1, 1, 'partial identity', JSON_OBJECT(), UTC_TIMESTAMP(), %d)
                """.formatted(runId)));
        execute("""
                INSERT INTO poll_audit_purge_runs
                    (project_id, requested_by, purge_reason, scope_json,
                     audit_committed_at, retention_run_id, operation_id,
                     retention_domain)
                VALUES
                    (1, 1, 'linked identity', JSON_OBJECT(), UTC_TIMESTAMP(), %d,
                     '00000000-0000-0000-0000-000000000201', 'poll_results')
                """.formatted(runId));

        assertThrows(SQLException.class, () -> execute("""
                UPDATE data_retention_operation
                   SET detail_purged_at = '2025-12-31 00:00:00.000'
                 WHERE operation_id = '00000000-0000-0000-0000-000000000201'
                """));
        execute("""
                UPDATE data_retention_operation
                   SET detail_purged_at = '2026-01-01 00:00:00.000'
                 WHERE operation_id = '00000000-0000-0000-0000-000000000201'
                """);

        long mismatchedRunId = insertAndReturnId("""
                INSERT INTO data_retention_run
                    (domain, mode, status, trigger_id, trigger_source, requested_by,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, application_version, started_at)
                VALUES
                    ('poll_results', 'execute', 'running',
                     '00000000-0000-0000-0000-000000000012', 'retry', 1,
                     1, UNHEX(SHA2('different-config', 256)),
                     'poll-results-v1', 'poll-v2', UTC_TIMESTAMP())
                """);
        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO data_retention_operation_run
                    (operation_id, run_group_id, domain,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, relation_type, linked_at)
                VALUES
                    ('00000000-0000-0000-0000-000000000201', %d, 'poll_results', 1,
                     UNHEX(SHA2('different-config', 256)),
                     'poll-results-v1', 'retry', UTC_TIMESTAMP(3))
                """.formatted(mismatchedRunId)));

        assertEquals(0, scalarInt("""
                SELECT COUNT(*)
                  FROM (
                      SELECT o.operation_id
                        FROM data_retention_operation o
                        LEFT JOIN data_retention_operation_run r
                          ON r.operation_id = o.operation_id
                         AND r.relation_type = 'origin'
                       GROUP BY o.operation_id, o.origin_run_group_id
                      HAVING COUNT(r.operation_id) <> 1
                          OR MAX(r.run_group_id) <> o.origin_run_group_id
                  ) anomalies
                """));
    }

    @Test
    void measuresTypicalAndNearLimitCapacityAtAllFrozenScalePoints() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);
        Assumptions.assumeTrue(Boolean.getBoolean(CAPACITY_GATE_PROPERTY),
                "capacity load runs only in the retention-mysql-it profile");

        List<CapacityMeasurement> measurements = new ArrayList<>();
        for (int attemptCount : List.of(10_000, 50_000, 100_000)) {
            measurements.add(measureCapacity(attemptCount, false));
        }
        for (int attemptCount : List.of(1_000, 5_000, 10_000)) {
            measurements.add(measureCapacity(attemptCount, true));
        }

        List<CapacityMeasurement> typical = measurements.stream()
                .filter(measurement -> !measurement.nearLimit())
                .toList();
        List<CapacityMeasurement> nearLimit = measurements.stream()
                .filter(CapacityMeasurement::nearLimit)
                .toList();
        assertStrictlyIncreasing(typical);
        assertStrictlyIncreasing(nearLimit);

        CapacityMeasurement steadyFiveYear = typical.get(typical.size() - 1);
        CapacityProjection projection = capacityProjection(steadyFiveYear);
        assertTrue(projection.fiveYearWithReserveBytes() <= 5L * 1024 * 1024 * 1024,
                "normal-cleanup steady five-year model plus 30% reserve exceeded 5 GiB: "
                        + projection.fiveYearWithReserveBytes());

        CapacityMeasurement nearLimitUpper = nearLimit.get(nearLimit.size() - 1);
        assertTrue(nearLimitUpper.bytesPerAttempt() > steadyFiveYear.bytesPerAttempt(),
                "near-limit JSON fixture must be wider than the typical fixture");

        writeCapacityReport(measurements, projection);
    }

    private CapacityMeasurement measureCapacity(int attemptCount, boolean nearLimit)
            throws Exception {
        resetToExtractedRetentionBaseline();
        assertEquals(1, governanceFlyway().migrate().migrationsExecuted);
        createCapacityNumbers();

        long emptyBytes = retentionBytes();
        loadCapacityFixture(attemptCount, nearLimit);
        analyzeRetentionTables();
        long loadedBytes = retentionBytes();
        long deltaBytes = loadedBytes - emptyBytes;
        assertTrue(deltaBytes > 0, "capacity fixture must allocate retention pages");
        return new CapacityMeasurement(
                nearLimit ? "near-limit" : "typical",
                attemptCount,
                emptyBytes,
                loadedBytes,
                deltaBytes,
                (double) deltaBytes / attemptCount,
                tableCapacityRows());
    }

    private void createCapacityNumbers() throws Exception {
        execute("CREATE TABLE wp2_capacity_numbers (n INT UNSIGNED NOT NULL PRIMARY KEY) ENGINE=InnoDB");
        execute("""
                INSERT INTO wp2_capacity_numbers (n)
                SELECT ones.n
                     + tens.n * 10
                     + hundreds.n * 100
                     + thousands.n * 1000
                     + ten_thousands.n * 10000
                     + hundred_thousands.n * 100000
                  FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
                        UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
                        UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
                  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
                              UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
                              UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
                  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
                              UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
                              UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
                  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
                              UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
                              UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) thousands
                  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
                              UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6
                              UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ten_thousands
                  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) hundred_thousands
                """);
        assertEquals(400_000, scalarInt("SELECT COUNT(*) FROM wp2_capacity_numbers"));
    }

    private void loadCapacityFixture(int attemptCount, boolean nearLimit) throws Exception {
        int runCount = ratio(attemptCount, 0.365d);
        int websiteOperationCount = ratio(attemptCount, 1.46d);
        int pollOperationCount = ratio(attemptCount, 1.825d);
        int unresolvedOperationCount = Math.max(1, ratio(attemptCount, 0.01d));
        int operationCount = websiteOperationCount + pollOperationCount;
        int websiteRunCount = Math.max(2,
                (int) Math.round((double) runCount * websiteOperationCount / operationCount));
        int pollRunCount = runCount - websiteRunCount;
        int retryWebsiteCount = ratio(websiteOperationCount, 0.10d);
        int retryPollCount = ratio(pollOperationCount, 0.10d);
        int failureGroupCount = ratio(attemptCount, 0.05d);
        int switchCount = ratio(attemptCount, 0.1825d);
        int reconciliationCount = ratio(attemptCount, 0.0365d);
        int fourthItemOperationCount = ratio(attemptCount, 0.25d);
        int scopePayloadLength = nearLimit ? 7_700 : 700;
        int metricsPayloadLength = nearLimit ? 7_700 : 1_400;
        int pollAuditPayloadLength = nearLimit ? 7_700 : 500;
        int errorLength = nearLimit ? 1_900 : 256;

        execute("""
                INSERT INTO data_retention_run
                    (id, domain, mode, status, trigger_id, trigger_source, requested_by,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, application_version, started_at,
                     attempt_rollup_completed_at)
                SELECT n + 1,
                       IF(n < %d, 'website_published_content', 'poll_results'),
                       'execute', 'succeeded',
                       CONCAT('30000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       'scheduler', 1, 1,
                       IF(n < %d,
                          UNHEX('5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd'),
                          UNHEX('a3d60a41c13633f7f40c1fbaf90e8086e19401b05efc4d658cc52b25b23d1b7c')),
                       IF(n < %d, 'website-published-v1', 'poll-results-v1'),
                       'capacity-v1', UTC_TIMESTAMP(), UTC_TIMESTAMP()
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(
                websiteRunCount, websiteRunCount, websiteRunCount, runCount));

        execute("""
                INSERT INTO data_retention_operation
                    (operation_id, origin_run_group_id, domain, operation_type,
                     batch_key_hash, hash_schema_version, item_count, cutoff_at,
                     upper_bound_business_key, config_fingerprint_schema_version,
                     config_fingerprint, policy_version, created_application_version,
                     outcome_status, outcome_finalized_at, outcome_finalization_source,
                     outcome_finalization_reason, detail_retention_anchor_at,
                     detail_purged_at)
                SELECT CONCAT('10000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       MOD(n, %d) + 1,
                       'website_published_content', 'website_batch',
                       UNHEX(SHA2(CONCAT('website-operation-', n), 256)), 1,
                       CASE WHEN n < %d THEN IF(n < %d, 4, 3) ELSE 8 END,
                       '2026-08-10 03:30:00.000', n + 1, 1,
                       UNHEX('5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd'),
                       'website-published-v1', 'capacity-v1', 'committed',
                       IF(n < %d, UTC_TIMESTAMP(3), '2024-01-01 00:00:00.000'),
                       'website_commit', 'committed',
                       IF(n < %d, UTC_TIMESTAMP(3), '2024-01-01 00:00:00.000'),
                       IF(n < %d, NULL, '2025-01-01 00:00:00.000')
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(
                websiteRunCount, attemptCount, fourthItemOperationCount,
                attemptCount, attemptCount, attemptCount, websiteOperationCount));

        execute("""
                INSERT INTO data_retention_operation
                    (operation_id, origin_run_group_id, domain, operation_type,
                     batch_key_hash, hash_schema_version, item_count,
                     poll_project_id, poll_batch_date, poll_question_tier,
                     poll_retention_cutoff_date, config_fingerprint_schema_version,
                     config_fingerprint, policy_version, created_application_version,
                     outcome_status, outcome_finalized_at, outcome_finalization_source,
                     outcome_finalization_reason, detail_retention_anchor_at,
                     detail_purged_at)
                SELECT CONCAT('20000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       %d + MOD(n, %d) + 1,
                       'poll_results', 'poll_slice',
                       UNHEX(SHA2(CONCAT('poll-operation-', n), 256)), 1, 1,
                       n + 1, DATE_ADD('2021-01-01', INTERVAL MOD(n, 1825) DAY),
                       'A', '2026-07-28', 1,
                       UNHEX('a3d60a41c13633f7f40c1fbaf90e8086e19401b05efc4d658cc52b25b23d1b7c'),
                       'poll-results-v1', 'capacity-v1', 'committed',
                       '2024-01-01 00:00:00.000', 'poll_marker', 'committed',
                       '2024-01-01 00:00:00.000', '2025-01-01 00:00:00.000'
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(websiteRunCount, pollRunCount, pollOperationCount));

        execute("""
                INSERT INTO data_retention_operation
                    (operation_id, origin_run_group_id, domain, operation_type,
                     batch_key_hash, hash_schema_version, item_count, cutoff_at,
                     upper_bound_business_key, config_fingerprint_schema_version,
                     config_fingerprint, policy_version, created_application_version,
                     outcome_status)
                SELECT CONCAT('11000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       MOD(n, %d) + 1,
                       'website_published_content', 'website_batch',
                       UNHEX(SHA2(CONCAT('unresolved-operation-', n), 256)),
                       1, 1, '2026-08-10 03:30:00.000', %d + n + 1, 1,
                       UNHEX('5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd'),
                       'website-published-v1', 'capacity-v1', 'unknown'
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(websiteRunCount, websiteOperationCount, unresolvedOperationCount));

        insertCapacityOperationRuns(
                websiteOperationCount, pollOperationCount, unresolvedOperationCount,
                websiteRunCount, pollRunCount, retryWebsiteCount, retryPollCount);

        execute("""
                INSERT INTO data_retention_batch_attempt
                    (id, run_group_id, operation_id, sequence_no, attempt_no, domain,
                     attempt_type, status, scope_json, metrics_json, last_error,
                     started_at, finished_at)
                SELECT n + 1, MOD(n, %d) + 1,
                       CONCAT('10000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       FLOOR(n / %d) + 1, 1, 'website_published_content',
                       'normal_batch', 'succeeded',
                       JSON_OBJECT('scope', REPEAT('s', %d)),
                       JSON_OBJECT('metrics', REPEAT('m', %d)),
                       %s,
                       UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(
                websiteRunCount, websiteRunCount, scopePayloadLength,
                metricsPayloadLength,
                nearLimit
                        ? "REPEAT('e', " + errorLength + ")"
                        : "IF(MOD(n, 100) = 0, REPEAT('e', " + errorLength + "), NULL)",
                attemptCount));

        execute("""
                INSERT INTO data_retention_batch_item
                    (run_group_id, first_attempt_id, operation_id, domain,
                     business_key, sequence_no)
                SELECT MOD(numbers.n, %d) + 1, numbers.n + 1,
                       CONCAT('10000000-0000-0000-0000-', LPAD(numbers.n + 1, 12, '0')),
                       'website_published_content', numbers.n * 10 + item_sequence.sequence_no,
                       item_sequence.sequence_no
                  FROM wp2_capacity_numbers numbers
                  CROSS JOIN (
                      SELECT 1 sequence_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                  ) item_sequence
                 WHERE numbers.n < %d
                   AND (item_sequence.sequence_no <= 3
                        OR numbers.n < %d)
                """.formatted(websiteRunCount, attemptCount, fourthItemOperationCount));

        execute("""
                INSERT INTO data_retention_batch_commit
                    (operation_id, run_group_id, domain, batch_key_hash,
                     hash_schema_version, item_count, cutoff_at,
                     upper_bound_business_key, config_fingerprint_schema_version,
                     config_fingerprint, policy_version,
                     committed_application_version, committed_at)
                SELECT CONCAT('10000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       MOD(n, %d) + 1, 'website_published_content',
                       UNHEX(SHA2(CONCAT('website-operation-', n), 256)), 1,
                       CASE WHEN n < %d THEN IF(n < %d, 4, 3) ELSE 8 END,
                       '2026-08-10 03:30:00.000', n + 1, 1,
                       UNHEX('5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd'),
                       'website-published-v1', 'capacity-v2', UTC_TIMESTAMP(3)
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(
                websiteRunCount, attemptCount, fourthItemOperationCount,
                websiteOperationCount));

        execute("""
                INSERT INTO data_retention_purged_slice
                    (domain, project_id, batch_date, question_tier,
                     retention_run_id, operation_id)
                SELECT 'poll_results', n + 1,
                       DATE_ADD('2021-01-01', INTERVAL MOD(n, 1825) DAY), 'A',
                       %d + MOD(n, %d) + 1,
                       CONCAT('20000000-0000-0000-0000-', LPAD(n + 1, 12, '0'))
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(websiteRunCount, pollRunCount, pollOperationCount));

        execute("""
                INSERT INTO poll_audit_purge_runs
                    (project_id, requested_by, purge_reason, scope_json, status,
                     affected_rows_json, audit_committed_at, retention_run_id,
                     operation_id, retention_domain)
                SELECT n + 1, 1, 'capacity fixture',
                       JSON_OBJECT('scope', REPEAT('p', %d)), 'SUCCEEDED',
                       JSON_OBJECT('affected', 1), UTC_TIMESTAMP(),
                       %d + MOD(n, %d) + 1,
                       CONCAT('20000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       'poll_results'
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(
                pollAuditPayloadLength, websiteRunCount, pollRunCount,
                pollOperationCount));

        insertCapacityFailureAudit(
                attemptCount, websiteRunCount, failureGroupCount,
                switchCount, reconciliationCount, unresolvedOperationCount,
                scopePayloadLength, errorLength, nearLimit);
    }

    private void insertCapacityOperationRuns(int websiteOperationCount,
                                             int pollOperationCount,
                                             int unresolvedOperationCount,
                                             int websiteRunCount,
                                             int pollRunCount,
                                             int retryWebsiteCount,
                                             int retryPollCount) throws Exception {
        execute("""
                INSERT INTO data_retention_operation_run
                    (operation_id, run_group_id, domain,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, relation_type, linked_at)
                SELECT CONCAT('10000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       MOD(n, %d) + 1, 'website_published_content', 1,
                       UNHEX('5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd'),
                       'website-published-v1', 'origin', UTC_TIMESTAMP(3)
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(websiteRunCount, websiteOperationCount));
        execute("""
                INSERT INTO data_retention_operation_run
                    (operation_id, run_group_id, domain,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, relation_type, linked_at)
                SELECT CONCAT('20000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       %d + MOD(n, %d) + 1, 'poll_results', 1,
                       UNHEX('a3d60a41c13633f7f40c1fbaf90e8086e19401b05efc4d658cc52b25b23d1b7c'),
                       'poll-results-v1', 'origin', UTC_TIMESTAMP(3)
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(websiteRunCount, pollRunCount, pollOperationCount));
        execute("""
                INSERT INTO data_retention_operation_run
                    (operation_id, run_group_id, domain,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, relation_type, linked_at)
                SELECT CONCAT('11000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       MOD(n, %d) + 1, 'website_published_content', 1,
                       UNHEX('5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd'),
                       'website-published-v1', 'origin', UTC_TIMESTAMP(3)
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(websiteRunCount, unresolvedOperationCount));
        execute("""
                INSERT INTO data_retention_operation_run
                    (operation_id, run_group_id, domain,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, relation_type, linked_at)
                SELECT CONCAT('10000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       MOD(n + 1, %d) + 1, 'website_published_content', 1,
                       UNHEX('5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd'),
                       'website-published-v1', 'retry', UTC_TIMESTAMP(3)
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(websiteRunCount, retryWebsiteCount));
        execute("""
                INSERT INTO data_retention_operation_run
                    (operation_id, run_group_id, domain,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, relation_type, linked_at)
                SELECT CONCAT('20000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       %d + MOD(n + 1, %d) + 1, 'poll_results', 1,
                       UNHEX('a3d60a41c13633f7f40c1fbaf90e8086e19401b05efc4d658cc52b25b23d1b7c'),
                       'poll-results-v1', 'retry', UTC_TIMESTAMP(3)
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(websiteRunCount, pollRunCount, retryPollCount));
    }

    private void insertCapacityFailureAudit(int attemptCount,
                                            int websiteRunCount,
                                            int failureGroupCount,
                                            int switchCount,
                                            int reconciliationCount,
                                            int unresolvedOperationCount,
                                            int evidencePayloadLength,
                                            int errorLength,
                                            boolean nearLimit) throws Exception {
        execute("""
                INSERT INTO data_retention_failure_group
                    (id, domain, source_run_group_id, operation_id, status,
                     item_count, failure_code, last_error, opened_at)
                SELECT n + 1, 'website_published_content', MOD(n, %d) + 1,
                       CONCAT('10000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       'active', 5, 'POST_CHECK_FAILED', %s, UTC_TIMESTAMP(3)
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(
                websiteRunCount,
                nearLimit ? "REPEAT('e', " + errorLength + ")" : "NULL",
                failureGroupCount));
        execute("""
                INSERT INTO data_retention_failure_group_item
                    (id, group_id, domain, business_key, sequence_no,
                     item_status)
                SELECT numbers.n * 5 + item_sequence.sequence_no,
                       numbers.n + 1, 'website_published_content',
                       CAST(numbers.n * 10 + item_sequence.sequence_no AS CHAR),
                       item_sequence.sequence_no, 'active'
                  FROM wp2_capacity_numbers numbers
                  CROSS JOIN (
                      SELECT 1 sequence_no UNION ALL SELECT 2 UNION ALL SELECT 3
                      UNION ALL SELECT 4 UNION ALL SELECT 5
                  ) item_sequence
                 WHERE numbers.n < %d
                """.formatted(failureGroupCount));
        execute("""
                INSERT INTO data_retention_failure_active_key
                    (domain, business_key, group_id, group_item_id)
                SELECT 'website_published_content', CAST(n * 10 + 1 AS CHAR),
                       n + 1, n * 5 + 1
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(failureGroupCount));
        execute("""
                INSERT INTO data_retention_failure_regroup_history
                    (regroup_operation_id, domain, business_key, old_group_id,
                     new_group_id, operator_user_id, reason, regrouped_at)
                SELECT CONCAT('60000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       'website_published_content', CAST(n * 20 + 1 AS CHAR),
                       n * 2 + 1, n * 2 + 2, 1, 'capacity fixture', UTC_TIMESTAMP(3)
                  FROM wp2_capacity_numbers
                 WHERE n < FLOOR(%d / 2)
                """.formatted(failureGroupCount));
        execute("""
                INSERT INTO data_retention_dynamic_switch_change
                    (change_id, switch_key, version, enabled, status, changed_at,
                     requested_by, request_source, reason)
                SELECT CONCAT('50000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       CONCAT('retention.capacity.', MOD(n, 16)),
                       FLOOR(n / 16) + 1, 0, 'applied', UTC_TIMESTAMP(3),
                       1, 'reconcile', 'capacity fixture'
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(switchCount));
        execute("""
                INSERT INTO data_retention_unknown_outcome_reconciliation
                    (reconciliation_id, operation_id, run_group_id, domain,
                     marker_state, business_state, decision, status,
                     evidence_hash, evidence_json, requested_by, last_error)
                SELECT CONCAT('40000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
                       CONCAT('11000000-0000-0000-0000-',
                              LPAD(MOD(n, %d) + 1, 12, '0')),
                       MOD(MOD(n, %d), %d) + 1,
                       'website_published_content', 'absent', 'unreadable',
                       'manual_review', 'pending',
                       UNHEX(SHA2(CONCAT('evidence-', n), 256)),
                       JSON_OBJECT('evidence', REPEAT('v', %d)), 1, %s
                  FROM wp2_capacity_numbers
                 WHERE n < %d
                """.formatted(
                unresolvedOperationCount, unresolvedOperationCount, websiteRunCount,
                evidencePayloadLength,
                nearLimit ? "REPEAT('e', " + errorLength + ")" : "NULL",
                reconciliationCount));
        assertEquals(attemptCount, scalarInt("SELECT COUNT(*) FROM data_retention_batch_attempt"));
    }

    private void analyzeRetentionTables() throws Exception {
        for (String table : retentionTableNames()) {
            execute("ANALYZE TABLE `" + table + "`");
        }
    }

    private long retentionBytes() throws Exception {
        return ((Number) scalar("""
                SELECT COALESCE(SUM(data_length + index_length), 0)
                  FROM information_schema.tables
                 WHERE table_schema = DATABASE()
                   AND (table_name LIKE 'data_retention_%'
                        OR table_name = 'poll_audit_purge_runs')
                """)).longValue();
    }

    private List<TableCapacity> tableCapacityRows() throws Exception {
        List<TableCapacity> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT table_name, table_rows, data_length, index_length
                       FROM information_schema.tables
                      WHERE table_schema = DATABASE()
                        AND (table_name LIKE 'data_retention_%'
                             OR table_name = 'poll_audit_purge_runs')
                      ORDER BY table_name
                     """)) {
            while (result.next()) {
                rows.add(new TableCapacity(
                        result.getString(1),
                        result.getLong(2),
                        result.getLong(3),
                        result.getLong(4)));
            }
        }
        return rows;
    }

    private List<String> retentionTableNames() throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT table_name
                       FROM information_schema.tables
                      WHERE table_schema = DATABASE()
                        AND (table_name LIKE 'data_retention_%'
                             OR table_name = 'poll_audit_purge_runs')
                      ORDER BY table_name
                     """)) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }
        return names;
    }

    private void assertStrictlyIncreasing(List<CapacityMeasurement> measurements) {
        assertEquals(3, measurements.size());
        for (int index = 1; index < measurements.size(); index++) {
            assertTrue(measurements.get(index).deltaBytes()
                            > measurements.get(index - 1).deltaBytes(),
                    measurements.toString());
        }
    }

    private CapacityProjection capacityProjection(CapacityMeasurement fiveYear) {
        long detailBytes = fiveYear.tables().stream()
                .filter(table -> table.tableName().equals("data_retention_batch_attempt")
                        || table.tableName().equals("data_retention_batch_item"))
                .mapToLong(table -> table.dataBytes() + table.indexBytes())
                .sum();
        long attemptBytes = tableBytes(fiveYear, "data_retention_batch_attempt");
        long itemBytes = tableBytes(fiveYear, "data_retention_batch_item");
        long permanentBytes = Math.max(0, fiveYear.deltaBytes() - detailBytes);
        long oneYear = permanentBytes / 5 + detailBytes;
        long threeYear = permanentBytes * 3 / 5 + detailBytes;
        long fiveYearWithReserve = Math.round(fiveYear.deltaBytes() * 1.30d);
        long failClosedFiveYear = Math.round(
                permanentBytes
                        + attemptBytes * (365_000d / 100_000d)
                        + itemBytes * (1_168_000d / 325_000d));
        return new CapacityProjection(
                oneYear,
                threeYear,
                fiveYear.deltaBytes(),
                fiveYearWithReserve,
                failClosedFiveYear,
                Math.round(failClosedFiveYear * 1.30d));
    }

    private long tableBytes(CapacityMeasurement measurement, String tableName) {
        return measurement.tables().stream()
                .filter(table -> table.tableName().equals(tableName))
                .mapToLong(table -> table.dataBytes() + table.indexBytes())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing capacity table " + tableName));
    }

    private void writeCapacityReport(List<CapacityMeasurement> measurements,
                                     CapacityProjection projection) throws Exception {
        Path reportDirectory = Path.of("target/retention-capacity");
        Files.createDirectories(reportDirectory);
        StringBuilder summary = new StringBuilder(
                "dataset,attempt_count,empty_bytes,loaded_bytes,delta_bytes,bytes_per_attempt\n");
        StringBuilder tables = new StringBuilder(
                "dataset,attempt_count,table_name,estimated_rows,data_bytes,index_bytes\n");
        for (CapacityMeasurement measurement : measurements) {
            summary.append(measurement.dataset()).append(',')
                    .append(measurement.attemptCount()).append(',')
                    .append(measurement.emptyBytes()).append(',')
                    .append(measurement.loadedBytes()).append(',')
                    .append(measurement.deltaBytes()).append(',')
                    .append(Math.round(measurement.bytesPerAttempt())).append('\n');
            for (TableCapacity table : measurement.tables()) {
                tables.append(measurement.dataset()).append(',')
                        .append(measurement.attemptCount()).append(',')
                        .append(table.tableName()).append(',')
                        .append(table.estimatedRows()).append(',')
                        .append(table.dataBytes()).append(',')
                        .append(table.indexBytes()).append('\n');
            }
        }
        summary.append("steady_1_year_prediction,,,,")
                .append(projection.oneYearBytes()).append(",\n");
        summary.append("steady_3_year_prediction,,,,")
                .append(projection.threeYearBytes()).append(",\n");
        summary.append("steady_5_year_prediction,,,,")
                .append(projection.fiveYearBytes()).append(",\n");
        summary.append("steady_5_year_plus_30_percent,,,,")
                .append(projection.fiveYearWithReserveBytes()).append(",\n");
        summary.append("fail_closed_5_year_prediction,,,,")
                .append(projection.failClosedFiveYearBytes()).append(",\n");
        summary.append("fail_closed_5_year_plus_30_percent,,,,")
                .append(projection.failClosedFiveYearWithReserveBytes()).append(",\n");
        Files.writeString(reportDirectory.resolve("summary.csv"), summary);
        Files.writeString(reportDirectory.resolve("tables.csv"), tables);
    }

    private int ratio(int base, double ratio) {
        return Math.max(1, (int) Math.ceil(base * ratio));
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
        } catch (RuntimeException ex) {
            dockerAvailable = false;
        }
        if (!dockerAvailable) {
            if (Boolean.getBoolean(REQUIRED_GATE_PROPERTY)) {
                throw new IllegalStateException(
                        "Required retention-mysql-it gate cannot run: " + UNAVAILABLE_REASON);
            }
            return false;
        }

        container = new MySQLContainer<>(MYSQL_IMAGE)
                .withDatabaseName("geo_retention_it")
                .withUsername("retention")
                .withPassword(UUID.randomUUID().toString());
        container.start();
        dataSource = dataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        return true;
    }

    private void configureExternalDatabase(String serverUrl) throws Exception {
        externalServerUrl = serverUrl;
        externalUsername = environmentOrDefault("RETENTION_MYSQL_IT_USER", "root");
        externalPassword = environmentOrDefault("RETENTION_MYSQL_IT_PASSWORD", "");
        externalDatabaseName = "geo_retention_it_"
                + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(
                externalServerUrl, externalUsername, externalPassword);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + externalDatabaseName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        String separator = serverUrl.contains("?") ? "&" : "?";
        String databaseUrl = serverUrl.replaceFirst("/\\?", "/" + externalDatabaseName + "?");
        if (databaseUrl.equals(serverUrl)) {
            databaseUrl = serverUrl.endsWith("/")
                    ? serverUrl + externalDatabaseName
                    : serverUrl + "/" + externalDatabaseName;
            databaseUrl += separator + "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        }
        dataSource = dataSource(databaseUrl, externalUsername, externalPassword);
    }

    private void resetToExtractedRetentionBaseline() throws Exception {
        Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load()
                .clean();
        try (Connection connection = dataSource.getConnection()) {
            executeCreateTableFromMigration(connection, "V228__data_lifecycle_summary_and_run.sql",
                    "CREATE TABLE IF NOT EXISTS data_retention_run (");
            executeCreateTableFromMigration(connection, "V228__data_lifecycle_summary_and_run.sql",
                    "CREATE TABLE IF NOT EXISTS data_retention_purged_slice (");
            executeCreateTableFromMigration(connection, "V228__data_lifecycle_summary_and_run.sql",
                    "CREATE TABLE IF NOT EXISTS data_retention_recompute_slice_lock (");
            executeCreateTableFromMigration(connection,
                    "V313__web_search_question_poll_foundation.sql",
                    "CREATE TABLE poll_audit_purge_runs (");
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    MIGRATION_DIRECTORY.resolve(
                            "V335__align_poll_retention_slice_collation.sql")));
        }
    }

    private void executeCreateTableFromMigration(Connection connection,
                                                 String migrationName,
                                                 String statementPrefix) throws Exception {
        String ddl = createTableDdlFromMigration(migrationName, statementPrefix);
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private String createTableDdlFromMigration(String migrationName,
                                                String statementPrefix) throws Exception {
        String migration = Files.readString(MIGRATION_DIRECTORY.resolve(migrationName));
        int start = migration.indexOf(statementPrefix);
        if (start < 0) {
            throw new IllegalStateException("Missing historical DDL: " + statementPrefix);
        }
        int end = migration.indexOf(';', start);
        if (end < 0) {
            throw new IllegalStateException("Unterminated historical DDL: " + statementPrefix);
        }
        return migration.substring(start, end);
    }

    private long insertHistoricalRows() throws Exception {
        long runId = insertAndReturnId("""
                INSERT INTO data_retention_run
                    (domain, mode, status, started_at)
                VALUES ('poll_results', 'execute', 'succeeded', UTC_TIMESTAMP())
                """);
        execute("""
                INSERT INTO data_retention_purged_slice
                    (domain, project_id, batch_date, question_tier, retention_run_id)
                VALUES ('poll_results', 7, '2026-05-26', 'A', %d)
                """.formatted(runId));
        execute("""
                INSERT INTO poll_audit_purge_runs
                    (project_id, requested_by, purge_reason, scope_json,
                     audit_committed_at)
                VALUES (7, 1, 'historical fixture', JSON_OBJECT('retentionRunId', %d),
                        UTC_TIMESTAMP())
                """.formatted(runId));
        return runId;
    }

    private WebsiteFixture insertWebsiteFixture() throws Exception {
        long originRunId = insertRun(
                "00000000-0000-0000-0000-000000000001", "created-v1");
        long retryRunId = insertRun(
                "00000000-0000-0000-0000-000000000002", "committed-v2");

        execute("""
                INSERT INTO data_retention_operation
                    (operation_id, origin_run_group_id, domain, operation_type,
                     batch_key_hash, hash_schema_version, item_count, cutoff_at,
                     upper_bound_business_key, config_fingerprint_schema_version,
                     config_fingerprint, policy_version, created_application_version,
                     outcome_status)
                VALUES
                    ('00000000-0000-0000-0000-000000000101', %d,
                     'website_published_content', 'website_batch',
                     UNHEX('14a67e69ebea79dea3c01abef4cd982c8fcb22303ec519941e7f319c633abbe7'),
                     1, 1, '2026-08-10 03:30:00.000', 101, 1,
                     UNHEX('5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd'),
                     'website-published-v1', 'created-v1', 'pending')
                """.formatted(originRunId));
        insertOperationRun(originRunId, "origin");
        insertOperationRun(retryRunId, "retry");
        long firstAttemptId = insertAttempt(originRunId, 1, 1, "normal_batch");
        execute("""
                INSERT INTO data_retention_batch_item
                    (run_group_id, first_attempt_id, operation_id, domain,
                     business_key, sequence_no)
                VALUES
                    (%d, %d, '00000000-0000-0000-0000-000000000101',
                     'website_published_content', 101, 1)
                """.formatted(originRunId, firstAttemptId));

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO data_retention_batch_commit
                            (operation_id, run_group_id, domain, batch_key_hash,
                             hash_schema_version, item_count, cutoff_at,
                             upper_bound_business_key,
                             config_fingerprint_schema_version, config_fingerprint,
                             policy_version, committed_application_version, committed_at)
                        VALUES
                            ('00000000-0000-0000-0000-000000000101', %d,
                             'website_published_content',
                             UNHEX('14a67e69ebea79dea3c01abef4cd982c8fcb22303ec519941e7f319c633abbe7'),
                             1, 1, '2026-08-10 03:30:00.000', 101, 1,
                             UNHEX('5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd'),
                             'website-published-v1', 'committed-v2', UTC_TIMESTAMP(3))
                        """.formatted(retryRunId));
                statement.executeUpdate("""
                        UPDATE data_retention_operation
                           SET outcome_status = 'committed',
                               outcome_finalized_at = UTC_TIMESTAMP(3),
                               outcome_finalization_source = 'website_commit',
                               outcome_finalization_reason = 'committed',
                               detail_retention_anchor_at = UTC_TIMESTAMP(3)
                         WHERE operation_id = '00000000-0000-0000-0000-000000000101'
                        """);
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
        return new WebsiteFixture(originRunId, retryRunId, firstAttemptId);
    }

    private long insertRun(String triggerId, String applicationVersion) throws Exception {
        return insertAndReturnId("""
                INSERT INTO data_retention_run
                    (domain, mode, status, trigger_id, trigger_source, requested_by,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, application_version, started_at)
                VALUES
                    ('website_published_content', 'execute', 'running', '%s', 'manual', 1,
                     1, UNHEX('5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd'),
                     'website-published-v1', '%s', UTC_TIMESTAMP())
                """.formatted(triggerId, applicationVersion));
    }

    private void insertOperationRun(long runId, String relationType) throws Exception {
        execute("""
                INSERT INTO data_retention_operation_run
                    (operation_id, run_group_id, domain,
                     config_fingerprint_schema_version, config_fingerprint,
                     policy_version, relation_type, linked_at)
                VALUES
                    ('00000000-0000-0000-0000-000000000101', %d,
                     'website_published_content', 1,
                     UNHEX('5f19f515ba805e342bcc076c5ce56cc4cc4ac20b08dc27a70c2dba5f1fbc2cbd'),
                     'website-published-v1', '%s', UTC_TIMESTAMP(3))
                """.formatted(runId, relationType));
    }

    private long insertAttempt(long runId,
                               int sequenceNo,
                               int attemptNo,
                               String attemptType) throws Exception {
        return insertAndReturnId("""
                INSERT INTO data_retention_batch_attempt
                    (run_group_id, operation_id, sequence_no, attempt_no, domain,
                     attempt_type, status, started_at)
                VALUES
                    (%d, '00000000-0000-0000-0000-000000000101', %d, %d,
                     'website_published_content', '%s', 'failed', UTC_TIMESTAMP(3))
                """.formatted(runId, sequenceNo, attemptNo, attemptType));
    }

    private void assertOperationLockSerializesCleanupAndWriter(WebsiteFixture fixture)
            throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch writerReady = new CountDownLatch(1);
        try (Connection cleanup = dataSource.getConnection()) {
            cleanup.setAutoCommit(false);
            lockSingleRow(cleanup, "data_retention_operation", "operation_id",
                    "00000000-0000-0000-0000-000000000101");
            lockSingleRow(cleanup, "data_retention_batch_attempt", "id",
                    Long.toString(fixture.firstAttemptId()));
            lockSingleRow(cleanup, "data_retention_batch_item", "first_attempt_id",
                    Long.toString(fixture.firstAttemptId()));

            Future<Timestamp> writer = executor.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    writerReady.countDown();
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT updated_at
                              FROM data_retention_operation
                             WHERE operation_id = ?
                             FOR UPDATE
                            """)) {
                        statement.setString(1, "00000000-0000-0000-0000-000000000101");
                        try (ResultSet result = statement.executeQuery()) {
                            assertTrue(result.next());
                            Timestamp timestamp = result.getTimestamp(1);
                            connection.rollback();
                            return timestamp;
                        }
                    }
                }
            });

            assertTrue(writerReady.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> writer.get(300, TimeUnit.MILLISECONDS));
            cleanup.commit();
            assertNotNull(writer.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void lockSingleRow(Connection connection,
                               String table,
                               String column,
                               String value) throws SQLException {
        String sql = "SELECT " + column + " FROM " + table + " WHERE " + column
                + " = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (column.equals("operation_id")) {
                statement.setString(1, value);
            } else {
                statement.setLong(1, Long.parseLong(value));
            }
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertNotNull(result.getObject(1));
            }
        }
    }

    private void assertSameShapeDefinitionRejectedAndRepairable(String mutation)
            throws Exception {
        resetToExtractedRetentionBaseline();
        assertEquals(1, governanceFlyway().migrate().migrationsExecuted);
        execute(mutation);
        // Test-only history removal forces V354 to inspect an already-present, altered schema.
        execute("DELETE FROM flyway_schema_history WHERE version = '354'");

        Flyway validation = governanceFlyway();
        FlywayException failure = assertThrows(FlywayException.class, validation::migrate);
        assertTrue(exceptionChain(failure).toLowerCase().contains(
                        "v354_exact_governance_schema_signature_mismatch"),
                exceptionChain(failure));

        removeFailedV354NewTables();
        validation.repair();
        assertEquals(1, validation.migrate().migrationsExecuted);
        assertEquals(0, validation.migrate().migrationsExecuted);
    }

    private void removeFailedV354NewTables() throws Exception {
        execute("""
                ALTER TABLE data_retention_purged_slice
                  DROP FOREIGN KEY fk_retention_purged_slice_operation_run,
                  DROP FOREIGN KEY fk_retention_purged_slice_operation_scope
                """);
        execute("""
                ALTER TABLE poll_audit_purge_runs
                  DROP FOREIGN KEY fk_poll_audit_purge_operation
                """);
        for (String table : List.of(
                "data_retention_batch_item",
                "data_retention_batch_commit",
                "data_retention_batch_attempt",
                "data_retention_failure_active_key",
                "data_retention_failure_regroup_history",
                "data_retention_failure_group_item",
                "data_retention_failure_group",
                "data_retention_unknown_outcome_reconciliation",
                "data_retention_dynamic_switch_change",
                "data_retention_operation_run",
                "data_retention_operation")) {
            execute("DROP TABLE " + table);
        }
    }

    private Flyway fullHistoricalFlyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + MIGRATION_DIRECTORY.toString().replace('\\', '/'))
                .target(MigrationVersion.fromVersion("354"))
                .load();
    }

    private String exceptionChain(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return messages.toString();
    }

    private Flyway governanceFlyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + MIGRATION_DIRECTORY.toString().replace('\\', '/'))
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("353"))
                .target(MigrationVersion.fromVersion("354"))
                .load();
    }

    private boolean columnExists(String table, String column) throws Exception {
        return scalarInt("""
                SELECT COUNT(*)
                  FROM information_schema.columns
                 WHERE table_schema = DATABASE()
                   AND table_name = '%s'
                   AND column_name = '%s'
                """.formatted(table, column)) == 1;
    }

    private int mysqlMajorVersion() throws Exception {
        String version = String.valueOf(scalar("SELECT VERSION()"));
        return Integer.parseInt(version.substring(0, version.indexOf('.')));
    }

    private long insertAndReturnId(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int scalarInt(String sql) throws Exception {
        return ((Number) scalar(sql)).intValue();
    }

    private Object scalar(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next(), sql);
            return result.getObject(1);
        }
    }

    private DataSource dataSource(String url, String username, String password) {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(url);
        source.setUsername(username);
        source.setPassword(password);
        return source;
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record WebsiteFixture(long originRunId, long retryRunId, long firstAttemptId) {
    }

    private record CapacityMeasurement(String dataset,
                                       int attemptCount,
                                       long emptyBytes,
                                       long loadedBytes,
                                       long deltaBytes,
                                       double bytesPerAttempt,
                                       List<TableCapacity> tables) {

        private boolean nearLimit() {
            return dataset.equals("near-limit");
        }
    }

    private record TableCapacity(String tableName,
                                 long estimatedRows,
                                 long dataBytes,
                                 long indexBytes) {
    }

    private record CapacityProjection(long oneYearBytes,
                                      long threeYearBytes,
                                      long fiveYearBytes,
                                      long fiveYearWithReserveBytes,
                                      long failClosedFiveYearBytes,
                                      long failClosedFiveYearWithReserveBytes) {
    }
}
