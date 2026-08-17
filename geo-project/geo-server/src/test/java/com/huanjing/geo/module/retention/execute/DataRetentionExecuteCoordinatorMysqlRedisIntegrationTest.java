package com.huanjing.geo.module.retention.execute;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.AttemptType;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionAttemptCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionOperationContext;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteCommitCommand;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.WebsiteOperationCommand;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
import com.huanjing.geo.module.retention.audit.service.TrustedMutationReceipt;
import com.huanjing.geo.module.retention.audit.support.DataRetentionApplicationVersionProvider;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditClock;
import com.huanjing.geo.module.retention.audit.support.DataRetentionAuditJsonCodec;
import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchAlertSink;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchGate;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchMetrics;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Payload;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RetentionDomain;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchRedisStore;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionBudget;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionResultCode;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.MutationEstimate;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.PendingMutation;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.PersistedRunState;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecuteCommand;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecutionResult;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionMutationActual;
import com.huanjing.geo.module.retention.support.RetentionExternalTestServices;
import com.huanjing.geo.module.retention.support.RetentionExternalTestServices.ExternalMysql;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataRetentionExecuteCoordinatorMysqlRedisIntegrationTest {
    private static final String REQUIRED = "retention.coordinator-it.required";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration").toAbsolutePath();

    private MySQLContainer<?> mysql;
    private GenericContainer<?> redis;
    private ExternalMysql externalMysql;
    private LettuceConnectionFactory redisFactory;
    private StringRedisTemplate redisTemplate;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactionManager;
    private DataRetentionProperties properties;
    private DataRetentionP0AuditService audit;
    private RetentionExecuteCommand command;
    private final java.util.List<ScheduledExecutorService> leaseExecutors = new java.util.ArrayList<>();
    private boolean ready;

    @BeforeAll
    void startInfrastructure() throws Exception {
        if (RetentionExternalTestServices.mysqlConfigured()
                && RetentionExternalTestServices.redisConfigured()) {
            externalMysql = RetentionExternalTestServices.createMysql("geo_retention_wp5_it_");
            dataSource = externalMysql.dataSource();
            transactionManager = new DataSourceTransactionManager(dataSource);
            jdbc = new JdbcTemplate(dataSource);
            redisFactory = RetentionExternalTestServices.redisConnectionFactory();
            redisTemplate = new StringRedisTemplate(redisFactory);
            redisTemplate.afterPropertiesSet();
            ready = true;
            return;
        }
        boolean docker;
        try { docker = DockerClientFactory.instance().isDockerAvailable(); }
        catch (RuntimeException unavailable) { docker = false; }
        if (!docker) {
            if (Boolean.getBoolean(REQUIRED)) {
                throw new IllegalStateException("Required WP5 coordinator integration infrastructure is unavailable");
            }
            return;
        }
        mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName("geo_retention_wp5_it").withUsername("retention")
                .withPassword(UUID.randomUUID().toString());
        redis = new GenericContainer<>(DockerImageName.parse("redis:7.2.5-alpine")).withExposedPorts(6379);
        mysql.start();
        redis.start();
        dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        transactionManager = new DataSourceTransactionManager(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        redisFactory = new LettuceConnectionFactory(redisConfiguration);
        redisFactory.afterPropertiesSet();
        redisFactory.start();
        redisTemplate = new StringRedisTemplate(redisFactory);
        redisTemplate.afterPropertiesSet();
        ready = true;
    }

    @BeforeEach
    void reset() throws Exception {
        Assumptions.assumeTrue(ready, "Docker is unavailable");
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
                    MIGRATIONS.resolve("V335__align_poll_retention_slice_collation.sql")));
        }
        Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + MIGRATIONS.toString().replace('\\', '/'))
                .baselineOnMigrate(true).baselineVersion(MigrationVersion.fromVersion("353"))
                .target(MigrationVersion.fromVersion("354")).load().migrate();
        installAuthorizationFixture();
        installBusinessMutationFixture();
        properties = properties();
        audit = audit();
        command = command();
        redisTemplate.delete(List.of(
                SwitchKey.GLOBAL_EXECUTE.redisKey(),
                SwitchKey.WEBSITE_PUBLISHED_CONTENT_EXECUTE.redisKey(),
                properties.getScheduler().getLockKey()));
        putEnabled(SwitchKey.GLOBAL_EXECUTE, "10000000-0000-4000-8000-000000000001", 1);
        putEnabled(SwitchKey.WEBSITE_PUBLISHED_CONTENT_EXECUTE,
                "10000000-0000-4000-8000-000000000002", 1);
    }

    @AfterAll
    void stopInfrastructure() throws Exception {
        leaseExecutors.forEach(ScheduledExecutorService::shutdownNow);
        if (redisTemplate != null) {
            redisTemplate.delete(List.of(
                    SwitchKey.GLOBAL_EXECUTE.redisKey(),
                    SwitchKey.WEBSITE_PUBLISHED_CONTENT_EXECUTE.redisKey(),
                    "geo:retention:wp5-it:lease"));
        }
        if (redisFactory != null) redisFactory.destroy();
        if (redis != null) redis.stop();
        if (mysql != null) mysql.stop();
        if (externalMysql != null) externalMysql.close();
    }

    @Test
    void businessMutationAndMarkerCommitAtomically() {
        DataRetentionExecuteCoordinator coordinator = coordinator(List.of(new WebsiteExecutor()));
        RetentionExecutionResult result = coordinator.execute(command);

        assertEquals(ExecutionResultCode.COMPLETED, result.code());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wp5_retention_business_row", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM data_retention_batch_commit", Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_retention_batch_attempt
                 WHERE status='succeeded'
                   AND JSON_EXTRACT(metrics_json,'$.executionBudgetUsage.committedTransactionCount')=1
                   AND JSON_EXTRACT(metrics_json,'$.executionBudgetUsage.chargedCandidateItems')=1
                """, Integer.class));
        assertEquals("succeeded", jdbc.queryForObject("SELECT status FROM data_retention_run WHERE id=?",
                String.class, result.runId()));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(properties.getScheduler().getLockKey())));
    }

    @Test
    void failureAfterMarkerRollsBackBusinessMutationMarkerAndAttemptTerminalState() {
        DataRetentionExecuteCoordinator coordinator = coordinator(List.of(new WebsiteExecutor(true)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> coordinator.execute(command));

        assertEquals("injected failure after marker", error.getMessage());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wp5_retention_business_row", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM data_retention_batch_commit", Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM data_retention_batch_attempt
                 WHERE status='running'
                   AND finished_at IS NULL
                   AND JSON_EXTRACT(metrics_json,'$.executionBudgetUsage') IS NULL
                """, Integer.class));
        assertEquals("pending", jdbc.queryForObject(
                "SELECT outcome_status FROM data_retention_operation", String.class));
        assertEquals("running", jdbc.queryForObject(
                "SELECT status FROM data_retention_run", String.class));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(properties.getScheduler().getLockKey())));
    }

    @Test
    void realLeaseCompetitionRejectsSecondCoordinatorWithoutCreatingRun() {
        DataRetentionExecuteLease lease = lease();
        try (DataRetentionExecuteLease.Lease ignored = lease.tryAcquire().lease()) {
            DataRetentionExecutionException error = assertThrows(DataRetentionExecutionException.class,
                    () -> coordinator(List.of(new WebsiteExecutor())).execute(command));
            assertEquals("EXECUTE_LEASE_BUSY", error.resultCode());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM data_retention_run", Integer.class));
        }
    }

    @Test
    void missingExecutorFailsBeforeGateLeaseAndAuditMutation() {
        redisTemplate.delete(List.of(SwitchKey.GLOBAL_EXECUTE.redisKey(),
                SwitchKey.WEBSITE_PUBLISHED_CONTENT_EXECUTE.redisKey()));
        DataRetentionExecutionException error = assertThrows(DataRetentionExecutionException.class,
                () -> coordinator(List.of()).execute(command));
        assertEquals("DOMAIN_EXECUTOR_UNAVAILABLE", error.resultCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM data_retention_run", Integer.class));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(properties.getScheduler().getLockKey())));
    }

    private DataRetentionExecuteCoordinator coordinator(List<DataRetentionDomainExecutor> executors) {
        DataRetentionDynamicSwitchMetrics metrics = new DataRetentionDynamicSwitchMetrics(new SimpleMeterRegistry());
        DataRetentionDynamicSwitchRedisStore store = new DataRetentionDynamicSwitchRedisStore(redisTemplate, metrics);
        DataRetentionDynamicSwitchAlertSink alerts = (code, key, detail) -> { };
        DataRetentionDynamicSwitchGate gate = new DataRetentionDynamicSwitchGate.RedisGate(store, alerts, metrics);
        return new DataRetentionExecuteCoordinator(audit, new DataRetentionDomainExecutorRegistry(executors),
                new DataRetentionExecutionAuthorizationPolicy(jdbc), new DataRetentionStaticExecuteGate(properties),
                gate, new DataRetentionExecutionBudgetGate(properties), lease(), properties, transactionManager);
    }

    private DataRetentionExecuteLease lease() {
        ScheduledExecutorService executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        leaseExecutors.add(executor);
        return new DataRetentionExecuteLease(redisTemplate, properties,
                new ConcurrentTaskScheduler(executor));
    }

    private final class WebsiteExecutor implements DataRetentionDomainExecutor {
        private final boolean failAfterMarker;

        private WebsiteExecutor() {
            this(false);
        }

        private WebsiteExecutor(boolean failAfterMarker) {
            this.failAfterMarker = failAfterMarker;
        }

        @Override public RetentionDomain domain() { return RetentionDomain.WEBSITE_PUBLISHED_CONTENT; }

        @Override
        public RetentionExecutionResult executeFresh(RetentionExecutionSession session) {
            RetentionAttemptCommand attempt = new RetentionAttemptCommand(
                    "30000000-0000-4000-8000-000000000001", AttemptType.NORMAL_BATCH,
                    null, null, Map.of("worker", "wp5-it"));
            RetentionOperationContext operation = session.audit().prepareWebsiteOrigin(session.run(),
                    new WebsiteOperationCommand(List.of(17L), NOW.minusSeconds(3600), 17L), attempt);
            session.audit().markAttemptRunning(operation.firstAttempt().attemptId());
            session.inMutationTransaction(new MutationEstimate(1), () -> {
                int deleted = jdbc.update("DELETE FROM wp5_retention_business_row WHERE id=?", 17L);
                if (deleted != 1) throw new IllegalStateException("expected one business row to be deleted");
                TrustedMutationReceipt receipt = session.audit().commitWebsiteMutation(new WebsiteCommitCommand(
                        operation, session.run().runId(), operation.firstAttempt().attemptId(), NOW));
                if (failAfterMarker) throw new IllegalStateException("injected failure after marker");
                return new PendingMutation<>("committed",
                        new RetentionMutationActual(operation.operationId(), 1, 1, 0, 0), receipt);
            });
            return new RetentionExecutionResult(null, ExecutionResultCode.COMPLETED, 1, 1, 0, 0, null);
        }

        @Override
        public RetentionExecutionResult resumeRunning(RetentionExecutionSession session, PersistedRunState state) {
            throw new AssertionError("fresh integration scenario must not recover");
        }
    }

    private DataRetentionP0AuditService audit() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("geo.retention.audit.application-version", "wp5-it-v1");
        return new DataRetentionP0AuditService(jdbc, new DataRetentionCanonicalEncoder(),
                new DataRetentionAuditJsonCodec(new ObjectMapper()),
                new DataRetentionApplicationVersionProvider(environment),
                new DataRetentionAuditClock(Clock.fixed(NOW, ZoneOffset.UTC)), transactionManager);
    }

    private RetentionExecuteCommand command() {
        DataRetentionCanonicalEncoder encoder = new DataRetentionCanonicalEncoder();
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(
                "website_published_content", 1, encoder.websiteFingerprint(24).sha256(),
                DataRetentionCanonicalEncoder.WEBSITE_POLICY_VERSION);
        return new RetentionExecuteCommand("20000000-0000-4000-8000-000000000001",
                RetentionDomain.WEBSITE_PUBLISHED_CONTENT, ExecutionSource.MANUAL, 7,
                "WP5 coordinator integration", "approved-WP5", null, contract,
                Map.of("effectiveConfig", encoder.websiteEffectiveConfig(24)), null, null,
                new ExecutionBudget(2, 10, Duration.ofMinutes(10)), null);
    }

    private DataRetentionProperties properties() {
        DataRetentionProperties value = new DataRetentionProperties();
        value.getScheduler().setExecuteEnabled(true);
        value.getWebsitePublishedCleanup().setExecuteEnabled(true);
        value.getScheduler().setLockKey("geo:retention:wp5-it:lease");
        value.getScheduler().setLockTtlSeconds(7200);
        value.getExecute().setMutationTimeoutSeconds(30);
        value.getExecute().setMaxRunSeconds(600);
        value.getExecute().setMaxMutationTransactions(2);
        value.getExecute().setMaxCandidateItems(10);
        return value;
    }

    private void putEnabled(SwitchKey key, String changeId, long version) {
        Payload payload = new Payload(changeId, version, true, NOW);
        redisTemplate.opsForHash().putAll(key.redisKey(), Map.of(
                "change_id", payload.changeId(), "version", payload.versionText(),
                "enabled", payload.enabledText(), "changed_at", payload.changedAtText()));
    }

    private void installAuthorizationFixture() {
        jdbc.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, role VARCHAR(32), is_active TINYINT(1))");
        jdbc.execute("CREATE TABLE sys_role (id BIGINT PRIMARY KEY, role_key VARCHAR(64), status VARCHAR(16), role_type VARCHAR(16))");
        jdbc.execute("CREATE TABLE sys_user_role (user_id BIGINT, role_id BIGINT)");
        jdbc.execute("CREATE TABLE sys_permission (id BIGINT PRIMARY KEY, perm_key VARCHAR(64), status VARCHAR(16))");
        jdbc.execute("CREATE TABLE sys_role_permission (role_id BIGINT, permission_id BIGINT)");
        jdbc.update("INSERT INTO sys_user VALUES (7,'super_admin',1)");
        jdbc.update("INSERT INTO sys_role VALUES (1,'super_admin','active','internal')");
        jdbc.update("INSERT INTO sys_user_role VALUES (7,1)");
        jdbc.update("INSERT INTO sys_permission VALUES (1,'data.retention.execute.manual','active')");
        jdbc.update("INSERT INTO sys_role_permission VALUES (1,1)");
    }

    private void installBusinessMutationFixture() {
        jdbc.execute("""
                CREATE TABLE wp5_retention_business_row (
                    id BIGINT NOT NULL PRIMARY KEY,
                    payload VARCHAR(64) NOT NULL
                ) ENGINE=InnoDB
                """);
        jdbc.update("INSERT INTO wp5_retention_business_row (id, payload) VALUES (?, ?)",
                17L, "eligible-retention-row");
    }

    private void executeCreateTable(java.sql.Connection connection, String migration,
                                    String statementPrefix) throws Exception {
        String sql = Files.readString(MIGRATIONS.resolve(migration));
        int start = sql.indexOf(statementPrefix);
        int end = sql.indexOf(';', start);
        if (start < 0 || end < 0) throw new IllegalStateException("Missing fixture DDL: " + statementPrefix);
        try (var statement = connection.createStatement()) { statement.execute(sql.substring(start, end)); }
    }
}
