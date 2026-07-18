package com.huanjing.geo.module.system.modeldiagnostic;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticRun;
import com.huanjing.geo.module.system.modeldiagnostic.entity.AiModelDiagnosticSession;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticRunMapper;
import com.huanjing.geo.module.system.modeldiagnostic.mapper.AiModelDiagnosticSessionMapper;
import com.huanjing.geo.module.system.modeldiagnostic.history.ModelDiagnosticHistoryQuery;
import com.huanjing.geo.module.system.modeldiagnostic.history.ModelDiagnosticRunSummary;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelDiagnosticMysqlIntegrationTest {

    private static final String MYSQL_IMAGE = "mysql:8.0.36";
    private static final String REQUIRED_GATE_PROPERTY = "model.diagnostic.mysql-it.required";
    private static final String UNAVAILABLE_REASON =
            "Docker is unavailable and MODEL_DIAGNOSTIC_MYSQL_IT_PASSWORD is not configured";

    private MySQLContainer<?> container;
    private DataSource dataSource;
    private SqlSessionFactory sqlSessionFactory;
    private boolean databaseReady;
    private String externalDatabaseName;
    private boolean externalDatabaseCreated;
    private String externalServerUrl;
    private String externalUsername;
    private String externalPassword;

    @BeforeAll
    void migrateV315AndV316OnMysql8() throws Exception {
        databaseReady = configureDatabase();
        if (!databaseReady) {
            return;
        }
        createPrerequisiteSchema();

        String migrationPath = Path.of("src/main/resources/db/migration")
                .toAbsolutePath().toString().replace('\\', '/');
        MigrateResult result = Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationPath)
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("314"))
                .target(MigrationVersion.fromVersion("316"))
                .load()
                .migrate();

        assertEquals(2, result.migrationsExecuted);
        assertEquals("8", mysqlMajorVersion());
        sqlSessionFactory = createSqlSessionFactory(dataSource);
    }

    @AfterAll
    void releaseDatabase() throws Exception {
        if (container != null) {
            container.stop();
        }
        if (externalDatabaseCreated) {
            try (Connection connection = java.sql.DriverManager.getConnection(
                    externalServerUrl, externalUsername, externalPassword);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + externalDatabaseName + "`");
            }
        }
    }

    @BeforeEach
    void resetDiagnosticRows() throws Exception {
        Assumptions.assumeTrue(databaseReady, UNAVAILABLE_REASON);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            statement.execute("TRUNCATE TABLE ai_model_diagnostic_runs");
            statement.execute("TRUNCATE TABLE ai_model_diagnostic_sessions");
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    @Test
    void flywayCreatesTablesChecksIndexesAndFrozenRbac() throws Exception {
        assertTrue(tableExists("ai_model_diagnostic_sessions"));
        assertTrue(tableExists("ai_model_diagnostic_runs"));
        assertEquals(2, scalarInt("""
                SELECT COUNT(*)
                FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE p.perm_key = 'ai.platform.diagnose'
                  AND r.role_key IN ('manager', 'super_admin')
                """));
        assertEquals(0, scalarInt("""
                SELECT COUNT(*)
                FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE p.perm_key = 'ai.platform.diagnose'
                  AND r.role_key NOT IN ('manager', 'super_admin')
                """));
        assertEquals(1, scalarInt("""
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'ai_model_diagnostic_runs'
                  AND index_name = 'uk_ai_diag_run_owner_client_request'
                  AND non_unique = 0
                """));
    }

    @Test
    void databaseRejectsMismatchedSessionIdentityAndInvalidStatusConclusion() throws Exception {
        long sessionRecordId = insertSession(1L, uuid());

        assertThrows(SQLException.class, () -> insertRun(
                sessionRecordId, 1L, uuid(), 1, uuid(), "RUNNING", null,
                LocalDateTime.now().plusMinutes(2)));
        assertThrows(SQLException.class, () -> insertRun(
                sessionRecordId, 1L, ownedSessionId(sessionRecordId), 1, uuid(), "SUCCEEDED", null,
                LocalDateTime.now().plusMinutes(2)));
    }

    @Test
    void databaseEnforcesIdempotencyAndAuditTurnUniqueness() throws Exception {
        String sessionId = uuid();
        String clientRequestId = uuid();
        long sessionRecordId = insertSession(1L, sessionId);
        insertRun(sessionRecordId, 1L, sessionId, 1, clientRequestId, "RUNNING", null,
                LocalDateTime.now().plusMinutes(2));

        assertThrows(SQLException.class, () -> insertRun(
                sessionRecordId, 1L, sessionId, 2, clientRequestId, "RUNNING", null,
                LocalDateTime.now().plusMinutes(2)));
        assertThrows(SQLException.class, () -> insertRun(
                sessionRecordId, 1L, sessionId, 1, uuid(), "RUNNING", null,
                LocalDateTime.now().plusMinutes(2)));
    }

    @Test
    void mapperOnlyAbandonsExpiredRunningRows() throws Exception {
        String sessionId = uuid();
        long sessionRecordId = insertSession(1L, sessionId);
        long expired = insertRun(sessionRecordId, 1L, sessionId, 1, uuid(), "RUNNING", null,
                LocalDateTime.now().minusSeconds(1));
        long unexpired = insertRun(sessionRecordId, 1L, sessionId, 2, uuid(), "RUNNING", null,
                LocalDateTime.now().plusMinutes(2));
        long completed = insertRun(sessionRecordId, 1L, sessionId, 3, uuid(), "SUCCEEDED", "PASS",
                LocalDateTime.now().minusSeconds(1));

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AiModelDiagnosticRunMapper mapper = session.getMapper(AiModelDiagnosticRunMapper.class);
            assertEquals(List.of(expired), mapper.selectExpiredRunningIds(10));
            assertNotNull(mapper.selectByIdempotencyKeyForUpdate(
                    1L, mapper.selectOwnedRun(expired, 1L).getClientRequestId()));
            AiModelDiagnosticRun lateSuccess = new AiModelDiagnosticRun();
            lateSuccess.setId(expired);
            lateSuccess.setStatus("SUCCEEDED");
            lateSuccess.setConclusion("PASS");
            lateSuccess.setAuthenticationStatus("PASS");
            lateSuccess.setGenerationStatus("PASS");
            lateSuccess.setWebSearchStatus("NOT_APPLICABLE");
            lateSuccess.setSourceParsingStatus("NOT_APPLICABLE");
            lateSuccess.setCitationParsingStatus("NOT_APPLICABLE");
            lateSuccess.setAnswer("late answer");
            assertEquals(0, mapper.finishRunning(lateSuccess));
            assertEquals(1, mapper.markAbandonedIfExpired(expired));
            assertEquals(0, mapper.markAbandonedIfExpired(unexpired));
            assertEquals(0, mapper.markAbandonedIfExpired(completed));

            AiModelDiagnosticRun abandoned = mapper.selectOwnedRun(expired, 1L);
            assertEquals("ABANDONED", abandoned.getStatus());
            assertEquals(1L, abandoned.getVersion());
            assertEquals("RUNNING", mapper.selectOwnedRun(unexpired, 1L).getStatus());
            assertEquals("SUCCEEDED", mapper.selectOwnedRun(completed, 1L).getStatus());
            assertNotNull(mapper.selectByIdempotencyKey(1L, abandoned.getClientRequestId()));
        }
    }

    @Test
    void mapperConditionallyRejectsOnlyUnexpiredRunningBeforeExecution() throws Exception {
        String sessionId = uuid();
        long sessionRecordId = insertSession(1L, sessionId);
        long running = insertRun(sessionRecordId, 1L, sessionId, 1, uuid(),
                "RUNNING", null, LocalDateTime.now().plusMinutes(2));
        long expired = insertRun(sessionRecordId, 1L, sessionId, 2, uuid(),
                "RUNNING", null, LocalDateTime.now().minusSeconds(1));

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AiModelDiagnosticRunMapper mapper = session.getMapper(AiModelDiagnosticRunMapper.class);
            assertEquals(1, mapper.rejectRunningBeforeExecution(
                    running, "RATE_LIMIT", "DIAGNOSTIC_BUSY", "busy"));
            AiModelDiagnosticRun rejected = mapper.selectOwnedRun(running, 1L);
            assertEquals("REJECTED", rejected.getStatus());
            assertNull(rejected.getConclusion());
            assertEquals("DIAGNOSTIC_BUSY", rejected.getErrorCode());
            assertEquals(0, mapper.rejectRunningBeforeExecution(
                    running, "RATE_LIMIT", "DIAGNOSTIC_BUSY", "busy"));

            assertEquals(0, mapper.rejectRunningBeforeExecution(
                    expired, "RATE_LIMIT", "DIAGNOSTIC_BUSY", "busy"));
            assertEquals(1, mapper.markAbandonedIfExpired(expired));
            assertEquals("ABANDONED", mapper.selectOwnedRun(expired, 1L).getStatus());
        }
    }

    @Test
    void sessionLockMakesConcurrentReplayObserveTheRunningPlaceholder() throws Exception {
        String sessionId = uuid();
        String clientRequestId = uuid();
        long sessionRecordId = insertSession(1L, sessionId);
        CountDownLatch secondAttemptStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try (Connection first = dataSource.getConnection();
             Connection second = dataSource.getConnection()) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            lockSessionIdentity(first, 1L, sessionId);
            insertRun(first, sessionRecordId, 1L, sessionId, 1, clientRequestId,
                    "RUNNING", null, LocalDateTime.now().plusMinutes(2));

            Future<String> replay = executor.submit(() -> {
                secondAttemptStarted.countDown();
                lockSessionIdentity(second, 1L, sessionId);
                try (PreparedStatement statement = second.prepareStatement("""
                        SELECT status
                        FROM ai_model_diagnostic_runs
                        WHERE operator_id = ? AND client_request_id = ?
                        FOR UPDATE
                        """)) {
                    statement.setLong(1, 1L);
                    statement.setString(2, clientRequestId);
                    try (ResultSet result = statement.executeQuery()) {
                        assertTrue(result.next());
                        return result.getString(1);
                    }
                }
            });

            assertTrue(secondAttemptStarted.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> replay.get(150, TimeUnit.MILLISECONDS));
            first.commit();
            assertEquals("RUNNING", replay.get(2, TimeUnit.SECONDS));
            second.commit();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void mapperConditionallyFinishesRunningBeforeDeadlineAndExposesSuccessfulContext() throws Exception {
        String sessionId = uuid();
        long sessionRecordId = insertSession(1L, sessionId);
        long runId = insertRun(sessionRecordId, 1L, sessionId, 1, uuid(), "RUNNING", null,
                LocalDateTime.now().plusMinutes(2));
        AiModelDiagnosticRun terminal = new AiModelDiagnosticRun();
        terminal.setId(runId);
        terminal.setStatus("SUCCEEDED");
        terminal.setConclusion("PASS");
        terminal.setAuthenticationStatus("PASS");
        terminal.setGenerationStatus("PASS");
        terminal.setWebSearchStatus("NOT_APPLICABLE");
        terminal.setSourceParsingStatus("NOT_APPLICABLE");
        terminal.setCitationParsingStatus("NOT_APPLICABLE");
        terminal.setAnswer("answer");
        terminal.setSearchEvidenceJson("[]");
        terminal.setSourcesJson("[]");
        terminal.setCitationsJson("[]");
        terminal.setUsageJson("{}");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AiModelDiagnosticRunMapper mapper = session.getMapper(AiModelDiagnosticRunMapper.class);
            assertEquals(1, mapper.freezeRequestMessagesIfRunning(
                    runId, "[{\"role\":\"user\",\"content\":\"question\"}]"));
            assertEquals(1, mapper.finishRunning(terminal));
            AiModelDiagnosticRun completed = mapper.selectOwnedRun(runId, 1L);
            assertEquals("SUCCEEDED", completed.getStatus());
            assertEquals("answer", completed.getAnswer());
            assertEquals(2L, completed.getVersion());
            assertTrue(completed.getRequestMessagesJson().contains("question"));
            assertEquals(1, mapper.selectRecentSuccessfulFreeChatContext(
                    sessionRecordId, 2).size());
        }
    }

    @Test
    void mapperPersistsUnexecutedFailureCapabilitiesAsNull() throws Exception {
        String sessionId = uuid();
        long sessionRecordId = insertSession(1L, sessionId);
        long runId = insertRun(sessionRecordId, 1L, sessionId, 1, uuid(),
                "RUNNING", null, LocalDateTime.now().plusMinutes(2));
        AiModelDiagnosticRun failure = new AiModelDiagnosticRun();
        failure.setId(runId);
        failure.setStatus("FAILED");
        failure.setConclusion("FAIL");
        failure.setAuthenticationStatus("FAIL");
        failure.setErrorCategory("AUTHENTICATION");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AiModelDiagnosticRunMapper mapper = session.getMapper(AiModelDiagnosticRunMapper.class);
            assertNotNull(mapper.selectByIdForUpdate(runId));
            assertEquals(1, mapper.finishRunning(failure));
            AiModelDiagnosticRun stored = mapper.selectOwnedRun(runId, 1L);
            assertEquals("FAILED", stored.getStatus());
            assertEquals("FAIL", stored.getAuthenticationStatus());
            assertNull(stored.getGenerationStatus());
            assertNull(stored.getWebSearchStatus());
            assertNull(stored.getSourceParsingStatus());
            assertNull(stored.getCitationParsingStatus());
        }
    }

    @Test
    void terminalDurationIsClampedWhenApplicationClockLeadsDatabase() throws Exception {
        String sessionId = uuid();
        long sessionRecordId = insertSession(1L, sessionId);
        long runId = insertRun(sessionRecordId, 1L, sessionId, 1, uuid(),
                "RUNNING", null, LocalDateTime.now().plusMinutes(2));
        setStartedAt(runId, databaseNow().plusSeconds(1));
        AiModelDiagnosticRun failure = new AiModelDiagnosticRun();
        failure.setId(runId);
        failure.setStatus("FAILED");
        failure.setConclusion("FAIL");
        failure.setErrorCategory("PROVIDER_HTTP");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AiModelDiagnosticRunMapper mapper = session.getMapper(AiModelDiagnosticRunMapper.class);
            assertEquals(1, mapper.finishRunning(failure));
            AiModelDiagnosticRun stored = mapper.selectOwnedRun(runId, 1L);
            assertEquals(0L, stored.getDurationMs());
        }
    }

    @Test
    void contextQueryExcludesProbesAndReturnsOnlyNineRecentFreeChatTurns() throws Exception {
        String sessionId = uuid();
        long sessionRecordId = insertSession(1L, sessionId);
        for (int turn = 1; turn <= 11; turn++) {
            long runId = insertRun(sessionRecordId, 1L, sessionId, turn, uuid(),
                    "SUCCEEDED", "PASS", LocalDateTime.now().minusSeconds(1));
            markAsSuccessfulContext(runId, "FREE_CHAT", "answer-" + turn);
        }
        long probeId = insertRun(sessionRecordId, 1L, sessionId, 12, uuid(),
                "SUCCEEDED", "PASS", LocalDateTime.now().minusSeconds(1));
        markAsSuccessfulContext(probeId, "STANDARD_PROBE", "probe-answer");
        long futureId = insertRun(sessionRecordId, 1L, sessionId, 13, uuid(),
                "SUCCEEDED", "PASS", LocalDateTime.now().minusSeconds(1));
        markAsSuccessfulContext(futureId, "FREE_CHAT", "future-answer");

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            List<AiModelDiagnosticRun> context = session.getMapper(AiModelDiagnosticRunMapper.class)
                    .selectRecentSuccessfulFreeChatContext(sessionRecordId, 12);
            assertEquals(9, context.size());
            assertEquals(11, context.get(0).getTurnNo());
            assertEquals(3, context.get(8).getTurnNo());
            assertFalse(context.stream().anyMatch(run -> "probe-answer".equals(run.getAnswer())));
            assertFalse(context.stream().anyMatch(run -> "future-answer".equals(run.getAnswer())));
        }
    }

    @Test
    void terminalUpdateWaitingOnRowLockCannotCommitAfterDatabaseDeadline() throws Exception {
        String sessionId = uuid();
        long sessionRecordId = insertSession(1L, sessionId);
        LocalDateTime deadline = databaseNow().plusSeconds(1);
        long runId = insertRun(sessionRecordId, 1L, sessionId, 1, uuid(),
                "RUNNING", null, deadline);
        AiModelDiagnosticRun terminal = new AiModelDiagnosticRun();
        terminal.setId(runId);
        terminal.setStatus("FAILED");
        terminal.setConclusion("FAIL");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SqlSession lockSession = sqlSessionFactory.openSession(false)) {
            assertNotNull(lockSession.getMapper(AiModelDiagnosticRunMapper.class)
                    .selectByIdForUpdate(runId));
            Future<Integer> update = executor.submit(() -> {
                try (SqlSession worker = sqlSessionFactory.openSession(false)) {
                    AiModelDiagnosticRunMapper mapper = worker.getMapper(AiModelDiagnosticRunMapper.class);
                    mapper.selectByIdForUpdate(runId);
                    int updated = mapper.finishRunning(terminal);
                    worker.commit(true);
                    return updated;
                }
            });

            Thread.sleep(200);
            assertFalse(update.isDone(), "terminal update should be waiting on the row lock");
            Thread.sleep(1_200L);
            lockSession.commit(true);

            assertEquals(0, update.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AiModelDiagnosticRunMapper mapper = session.getMapper(AiModelDiagnosticRunMapper.class);
            assertEquals("RUNNING", mapper.selectOwnedRun(runId, 1L).getStatus());
            assertEquals(1, mapper.markAbandonedIfExpired(runId));
            assertEquals("ABANDONED", mapper.selectOwnedRun(runId, 1L).getStatus());
        }
    }

    @Test
    void sessionMapperAllocatesTurnUnderOwnedActiveSession() throws Exception {
        String sessionId = uuid();
        long sessionRecordId = insertSession(1L, sessionId);

        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            AiModelDiagnosticSessionMapper mapper = session.getMapper(AiModelDiagnosticSessionMapper.class);
            assertEquals(0, mapper.insertActiveIfAbsent(1L, sessionId, LocalDateTime.now()));
            AiModelDiagnosticSession diagnosticSession = mapper.selectOwnedForUpdate(1L, sessionId);
            assertNotNull(diagnosticSession);
            assertEquals(1, diagnosticSession.getNextTurnNo());
            assertEquals(1, mapper.consumeTurn(sessionRecordId, 1, LocalDateTime.now()));
            assertEquals(0, mapper.consumeTurn(sessionRecordId, 1, LocalDateTime.now()));
            session.commit();
        }

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AiModelDiagnosticSession updated = session.getMapper(AiModelDiagnosticSessionMapper.class)
                    .selectOwnedForUpdate(1L, sessionId);
            assertEquals(2, updated.getNextTurnNo());
        }
    }

    @Test
    void historyMapperExecutesDynamicFiltersAndEnforcesOperatorOwnership() throws Exception {
        String operatorOneSession = uuid();
        String operatorTwoSession = uuid();
        long operatorOneRecord = insertSession(1L, operatorOneSession);
        long operatorTwoRecord = insertSession(2L, operatorTwoSession);
        long operatorOneRun = insertRun(operatorOneRecord, 1L, operatorOneSession, 1,
                uuid(), "SUCCEEDED", "PASS", LocalDateTime.now().plusMinutes(2));
        insertRun(operatorTwoRecord, 2L, operatorTwoSession, 1,
                uuid(), "SUCCEEDED", "PASS", LocalDateTime.now().plusMinutes(2));
        ModelDiagnosticHistoryQuery query = new ModelDiagnosticHistoryQuery(
                1, 20, 101L, "fixture-model", "WEB_SEARCH",
                "SUCCEEDED", "PASS", LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1));

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AiModelDiagnosticRunMapper runMapper =
                    session.getMapper(AiModelDiagnosticRunMapper.class);
            AiModelDiagnosticSessionMapper sessionMapper =
                    session.getMapper(AiModelDiagnosticSessionMapper.class);

            assertEquals(1L, runMapper.countOwnedHistory(1L, query));
            List<ModelDiagnosticRunSummary> records =
                    runMapper.selectOwnedHistory(1L, query, 0, 20);
            assertEquals(1, records.size());
            assertEquals(operatorOneRun, records.get(0).getId());
            assertEquals("fixture-model", records.get(0).getRequestedModelId());
            assertNotNull(sessionMapper.selectOwned(1L, operatorOneSession));
            assertNull(sessionMapper.selectOwned(2L, operatorOneSession));
            assertEquals(1, runMapper.selectOwnedSessionRuns(
                    1L, operatorOneSession).size());
            assertTrue(runMapper.selectOwnedSessionRuns(
                    2L, operatorOneSession).isEmpty());
            assertNull(runMapper.selectOwnedRun(operatorOneRun, 2L));
        }
    }

    @Test
    void retentionMapperUsesStrictCutoffBatchingAndDeletesOnlyEmptyOldSessions()
            throws Exception {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30)
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        String oldSessionId = uuid();
        String oldWithRecentRunId = uuid();
        String oldEmptySessionId = uuid();
        String recentEmptySessionId = uuid();
        long oldSession = insertSession(1L, oldSessionId);
        long oldWithRecentRun = insertSession(1L, oldWithRecentRunId);
        long oldEmptySession = insertSession(1L, oldEmptySessionId);
        long recentEmptySession = insertSession(1L, recentEmptySessionId);
        long oldestRun = insertRun(oldSession, 1L, oldSessionId, 1, uuid(),
                "SUCCEEDED", "PASS", LocalDateTime.now().plusMinutes(2));
        long boundaryRun = insertRun(oldSession, 1L, oldSessionId, 2, uuid(),
                "SUCCEEDED", "PASS", LocalDateTime.now().plusMinutes(2));
        long recentRun = insertRun(oldWithRecentRun, 1L, oldWithRecentRunId, 1, uuid(),
                "SUCCEEDED", "PASS", LocalDateTime.now().plusMinutes(2));
        setCreatedAt("ai_model_diagnostic_runs", oldestRun, cutoff.minusDays(5));
        setCreatedAt("ai_model_diagnostic_runs", boundaryRun, cutoff);
        setCreatedAt("ai_model_diagnostic_runs", recentRun, cutoff.plusDays(1));
        setCreatedAt("ai_model_diagnostic_sessions", oldSession, cutoff.minusDays(10));
        setCreatedAt("ai_model_diagnostic_sessions", oldWithRecentRun, cutoff.minusDays(10));
        setCreatedAt("ai_model_diagnostic_sessions", oldEmptySession, cutoff.minusDays(10));
        setCreatedAt("ai_model_diagnostic_sessions", recentEmptySession, cutoff.plusDays(1));

        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            AiModelDiagnosticRunMapper runMapper =
                    session.getMapper(AiModelDiagnosticRunMapper.class);
            AiModelDiagnosticSessionMapper sessionMapper =
                    session.getMapper(AiModelDiagnosticSessionMapper.class);

            assertEquals(1, runMapper.deleteExpiredBatch(cutoff, 1));
            assertNull(runMapper.selectById(oldestRun));
            assertNotNull(runMapper.selectById(boundaryRun));
            assertNotNull(runMapper.selectById(recentRun));
            assertEquals(1, sessionMapper.deleteEmptyExpiredBatch(cutoff, 10));
            assertNull(sessionMapper.selectById(oldEmptySession));
            assertNotNull(sessionMapper.selectById(oldSession));
            assertNotNull(sessionMapper.selectById(oldWithRecentRun));
            assertNotNull(sessionMapper.selectById(recentEmptySession));
        }
    }

    private boolean configureDatabase() throws Exception {
        String password = System.getenv("MODEL_DIAGNOSTIC_MYSQL_IT_PASSWORD");
        if (password != null && !password.isBlank()) {
            configureExternalDatabase(password);
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
                        "Required mysql-it gate cannot run: " + UNAVAILABLE_REASON);
            }
            return false;
        }
        container = new MySQLContainer<>(MYSQL_IMAGE)
                .withDatabaseName("geo_diag_it")
                .withUsername("diag")
                .withPassword(UUID.randomUUID().toString());
        container.start();
        dataSource = dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        return true;
    }

    private void configureExternalDatabase(String password) throws Exception {
        String host = environmentOrDefault("MODEL_DIAGNOSTIC_MYSQL_IT_HOST", "localhost");
        String port = environmentOrDefault("MODEL_DIAGNOSTIC_MYSQL_IT_PORT", "3306");
        externalUsername = environmentOrDefault("MODEL_DIAGNOSTIC_MYSQL_IT_USER", "root");
        externalPassword = password;
        externalDatabaseName = "geo_diag_it_" + UUID.randomUUID().toString().replace("-", "");
        externalServerUrl = "jdbc:mysql://" + host + ":" + port
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection connection = java.sql.DriverManager.getConnection(
                externalServerUrl, externalUsername, externalPassword);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + externalDatabaseName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            externalDatabaseCreated = true;
        }
        String databaseUrl = "jdbc:mysql://" + host + ":" + port + "/" + externalDatabaseName
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        dataSource = dataSource(databaseUrl, externalUsername, externalPassword);
    }

    private void createPrerequisiteSchema() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE sys_user (
                        id BIGINT PRIMARY KEY,
                        username VARCHAR(64) NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("""
                    CREATE TABLE sys_role (
                        id BIGINT PRIMARY KEY,
                        role_key VARCHAR(64) NOT NULL UNIQUE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("""
                    CREATE TABLE sys_permission (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        perm_key VARCHAR(64) NOT NULL UNIQUE,
                        perm_name VARCHAR(128) NOT NULL,
                        module VARCHAR(64) NOT NULL,
                        action VARCHAR(32) NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("""
                    CREATE TABLE sys_role_permission (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        role_id BIGINT NOT NULL,
                        permission_id BIGINT NOT NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_sys_role_permission (role_id, permission_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("INSERT INTO sys_user (id, username) VALUES (1, 'operator-one'), (2, 'operator-two')");
            statement.execute("INSERT INTO sys_role (id, role_key) VALUES (1, 'manager'), (2, 'super_admin'), (3, 'operator')");
        }
    }

    private SqlSessionFactory createSqlSessionFactory(DataSource source) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment(
                "mysql-it", new JdbcTransactionFactory(), source));
        configuration.addMapper(AiModelDiagnosticRunMapper.class);
        configuration.addMapper(AiModelDiagnosticSessionMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private long insertSession(long operatorId, String sessionId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO ai_model_diagnostic_sessions
                         (session_id, operator_id, status, next_turn_no)
                     VALUES (?, ?, 'ACTIVE', 1)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, sessionId);
            statement.setLong(2, operatorId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private long insertRun(long sessionRecordId,
                           long operatorId,
                           String sessionId,
                           int turnNo,
                           String clientRequestId,
                           String status,
                           String conclusion,
                           LocalDateTime deadlineAt) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return insertRun(connection, sessionRecordId, operatorId, sessionId, turnNo,
                    clientRequestId, status, conclusion, deadlineAt);
        }
    }

    private long insertRun(Connection connection,
                           long sessionRecordId,
                           long operatorId,
                           String sessionId,
                           int turnNo,
                           String clientRequestId,
                           String status,
                           String conclusion,
                           LocalDateTime deadlineAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO ai_model_diagnostic_runs (
                         session_record_id, session_id, turn_no, operator_id,
                         client_request_id, request_fingerprint,
                         platform_config_id, platform_code, channel_code, platform_name,
                         usage_scene, integration_type, config_version,
                         config_snapshot_json, config_snapshot_hash, endpoint_url,
                         diagnostic_mode, test_mode, response_mode,
                         status, conclusion, user_message, request_messages_json,
                         requested_model_id, deadline_at, started_at
                     ) VALUES (
                         ?, ?, ?, ?, ?, REPEAT('a', 64),
                         101, 'fixture_web', 'fixture', 'Fixture',
                         'QUESTION_POLL_WEB', 'VOLCENGINE_RESPONSES_WEB', 1,
                         '{}', REPEAT('b', 64), 'https://example.test/responses',
                         'WEB_SEARCH', 'FREE_CHAT', 'SYNC',
                         ?, ?, 'question', '[]', 'fixture-model', ?, ?
                     )
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, sessionRecordId);
            statement.setString(2, sessionId);
            statement.setInt(3, turnNo);
            statement.setLong(4, operatorId);
            statement.setString(5, clientRequestId);
            statement.setString(6, status);
            if (conclusion == null) {
                statement.setNull(7, java.sql.Types.VARCHAR);
            } else {
                statement.setString(7, conclusion);
            }
            statement.setTimestamp(8, Timestamp.valueOf(deadlineAt));
            statement.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now().minusSeconds(5)));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private void lockSessionIdentity(Connection connection,
                                     long operatorId,
                                     String sessionId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id
                FROM ai_model_diagnostic_sessions
                WHERE operator_id = ? AND session_id = ?
                FOR UPDATE
                """)) {
            statement.setLong(1, operatorId);
            statement.setString(2, sessionId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
            }
        }
    }

    private void markAsSuccessfulContext(long runId, String testMode, String answer) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE ai_model_diagnostic_runs
                     SET test_mode = ?, generation_status = 'PASS', answer = ?
                     WHERE id = ?
                     """)) {
            statement.setString(1, testMode);
            statement.setString(2, answer);
            statement.setLong(3, runId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void setCreatedAt(String table, long id, LocalDateTime createdAt) throws Exception {
        if (!"ai_model_diagnostic_runs".equals(table)
                && !"ai_model_diagnostic_sessions".equals(table)) {
            throw new IllegalArgumentException("unsupported table");
        }
        String sql = "ai_model_diagnostic_runs".equals(table)
                ? "UPDATE ai_model_diagnostic_runs SET created_at = ? WHERE id = ?"
                : "UPDATE ai_model_diagnostic_sessions SET created_at = ?, last_run_at = NULL WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(createdAt));
            statement.setLong(2, id);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private void setStartedAt(long runId, LocalDateTime startedAt) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE ai_model_diagnostic_runs SET started_at = ? WHERE id = ?")) {
            statement.setTimestamp(1, Timestamp.valueOf(startedAt));
            statement.setLong(2, runId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private String ownedSessionId(long sessionRecordId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT session_id FROM ai_model_diagnostic_sessions WHERE id = ?")) {
            statement.setLong(1, sessionRecordId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private boolean tableExists(String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM information_schema.tables
                     WHERE table_schema = DATABASE()
                       AND table_name = ?
                     """)) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
    }

    private int scalarInt(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private LocalDateTime databaseNow() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT NOW(3)")) {
            assertTrue(result.next());
            return result.getTimestamp(1).toLocalDateTime();
        }
    }

    private String mysqlMajorVersion() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT SUBSTRING_INDEX(VERSION(), '.', 1)")) {
            result.next();
            return result.getString(1);
        }
    }

    private DataSource dataSource(String url, String username, String password) {
        return new DriverManagerDataSource(url, username, password);
    }

    private String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String uuid() {
        return UUID.randomUUID().toString();
    }
}
