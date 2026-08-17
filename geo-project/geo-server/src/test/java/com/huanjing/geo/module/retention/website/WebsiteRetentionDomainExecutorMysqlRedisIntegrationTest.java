package com.huanjing.geo.module.retention.website;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.service.DataRetentionP0AuditService;
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
import com.huanjing.geo.module.retention.execute.DataRetentionDomainExecutorRegistry;
import com.huanjing.geo.module.retention.execute.DataRetentionExecuteCoordinator;
import com.huanjing.geo.module.retention.execute.DataRetentionExecuteLease;
import com.huanjing.geo.module.retention.execute.DataRetentionExecutionAuthorizationPolicy;
import com.huanjing.geo.module.retention.execute.DataRetentionExecutionBudgetGate;
import com.huanjing.geo.module.retention.execute.DataRetentionStaticExecuteGate;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionBudget;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionResultCode;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecuteCommand;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecutionResult;
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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebsiteRetentionDomainExecutorMysqlRedisIntegrationTest {
    private static final String REQUIRED = "retention.website-it.required";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration").toAbsolutePath();
    private static final String LEASE_KEY = "geo:retention:wp6-business-it:lease";

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
    private final java.util.List<ScheduledExecutorService> leaseExecutors = new java.util.ArrayList<>();
    private boolean ready;

    @BeforeAll
    void startInfrastructure() throws Exception {
        if (RetentionExternalTestServices.mysqlConfigured()
                && RetentionExternalTestServices.redisConfigured()) {
            externalMysql = RetentionExternalTestServices.createMysql("geo_retention_wp6_business_it_");
            dataSource = externalMysql.dataSource();
            connectDataAccess();
            redisFactory = RetentionExternalTestServices.redisConnectionFactory();
            connectRedis();
            ready = true;
            return;
        }
        boolean docker;
        try {
            docker = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException unavailable) {
            docker = false;
        }
        if (!docker) {
            if (Boolean.getBoolean(REQUIRED)) {
                throw new IllegalStateException("Required WP6 website business integration infrastructure is unavailable");
            }
            return;
        }
        mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName("geo_retention_wp6_business_it")
                .withUsername("retention")
                .withPassword(UUID.randomUUID().toString());
        redis = new GenericContainer<>(DockerImageName.parse("redis:7.2.5-alpine"))
                .withExposedPorts(6379);
        mysql.start();
        redis.start();
        dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        connectDataAccess();
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        redisFactory = new LettuceConnectionFactory(redisConfiguration);
        redisFactory.afterPropertiesSet();
        redisFactory.start();
        connectRedis();
        ready = true;
    }

    @BeforeEach
    void reset() throws Exception {
        Assumptions.assumeTrue(ready, "MySQL and Redis integration infrastructure is unavailable");
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
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("353"))
                .target(MigrationVersion.fromVersion("354"))
                .load().migrate();
        installAuthorizationFixture();
        installWebsiteBusinessSchema();
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    MIGRATIONS.resolve("V366__guard_distribution_payload_purge_marker.sql")));
        }
        seedCandidates();
        properties = properties();
        audit = audit();
        redisTemplate.delete(List.of(
                SwitchKey.GLOBAL_EXECUTE.redisKey(),
                SwitchKey.WEBSITE_PUBLISHED_CONTENT_EXECUTE.redisKey(),
                LEASE_KEY));
        putEnabled(SwitchKey.GLOBAL_EXECUTE, "60000000-0000-4000-8000-000000000001", 1);
        putEnabled(SwitchKey.WEBSITE_PUBLISHED_CONTENT_EXECUTE,
                "60000000-0000-4000-8000-000000000002", 1);
    }

    @AfterAll
    void stopInfrastructure() throws Exception {
        leaseExecutors.forEach(ScheduledExecutorService::shutdownNow);
        if (redisTemplate != null) {
            redisTemplate.delete(List.of(
                    SwitchKey.GLOBAL_EXECUTE.redisKey(),
                    SwitchKey.WEBSITE_PUBLISHED_CONTENT_EXECUTE.redisKey(),
                    LEASE_KEY));
        }
        if (redisFactory != null) redisFactory.destroy();
        if (redis != null) redis.stop();
        if (mysql != null) mysql.stop();
        if (externalMysql != null) externalMysql.close();
    }

    @Test
    void realExecutorAndBusinessRepositoryCommitTwentyRowsIncludingPayloadOnlyReheat() {
        WebsiteRetentionBusinessRepository business =
                new WebsiteRetentionBusinessRepository(jdbc, transactionManager);
        WebsiteRetentionDomainExecutor executor =
                new WebsiteRetentionDomainExecutor(business, Clock.fixed(NOW, ZoneOffset.UTC));
        RetentionExecutionResult result = coordinator(executor).execute(command());

        assertNotNull(result.runId());
        assertEquals(ExecutionResultCode.COMPLETED, result.code());
        assertEquals(20, result.candidateCount());
        assertEquals(20, result.affectedCount());
        assertEquals(0, result.skippedCount());
        assertEquals(0, result.warningCount());

        assertEquals(20, count("SELECT COUNT(*) FROM article_draft WHERE status='deleted'"));
        assertEquals(0, count("SELECT COUNT(*) FROM article_draft_version WHERE content_markdown IS NOT NULL"));
        assertEquals(20, count("SELECT COUNT(*) FROM article_draft_version WHERE content_purged_at IS NOT NULL"));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM distribution_tasks
                 WHERE request_payload IS NOT NULL OR fill_payload IS NOT NULL OR response_payload IS NOT NULL
                """));
        assertEquals(20, count("SELECT COUNT(*) FROM distribution_tasks WHERE payload_purged_at IS NOT NULL"));
        assertEquals(0, count("SELECT COUNT(*) FROM article_publish_record WHERE raw_response IS NOT NULL"));

        assertEquals(1, count("""
                SELECT COUNT(*) FROM distribution_tasks
                 WHERE article_id=20 AND response_payload IS NULL AND payload_purged_at IS NOT NULL
                """));
        assertEquals(20, count("SELECT COUNT(*) FROM article_draft WHERE project_id=9001"));
        assertEquals(20, count("SELECT COUNT(*) FROM article_draft_version"));
        assertEquals(20, count("SELECT COUNT(*) FROM distribution_tasks WHERE project_id=9001"
                + " AND published_url LIKE 'https://example.test/article/%'"));
        assertEquals(20, count("SELECT COUNT(*) FROM article_publish_record"
                + " WHERE publish_status='distributed' AND source_type='distribution_task'"
                + " AND published_url LIKE 'https://example.test/article/%' AND url_quality='verified'"));

        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_operation WHERE outcome_status='committed'"));
        assertEquals(20, count("SELECT COUNT(*) FROM data_retention_batch_item"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE status='succeeded'"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_batch_commit WHERE item_count=20"));
        assertEquals("succeeded", jdbc.queryForObject(
                "SELECT status FROM data_retention_run WHERE id=?", String.class, result.runId()));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(LEASE_KEY)));
    }

    private DataRetentionExecuteCoordinator coordinator(WebsiteRetentionDomainExecutor executor) {
        DataRetentionDynamicSwitchMetrics metrics = new DataRetentionDynamicSwitchMetrics(new SimpleMeterRegistry());
        DataRetentionDynamicSwitchRedisStore store = new DataRetentionDynamicSwitchRedisStore(redisTemplate, metrics);
        DataRetentionDynamicSwitchAlertSink alerts = (code, key, detail) -> { };
        DataRetentionDynamicSwitchGate gate = new DataRetentionDynamicSwitchGate.RedisGate(store, alerts, metrics);
        return new DataRetentionExecuteCoordinator(audit,
                new DataRetentionDomainExecutorRegistry(List.of(executor)),
                new DataRetentionExecutionAuthorizationPolicy(jdbc),
                new DataRetentionStaticExecuteGate(properties), gate,
                new DataRetentionExecutionBudgetGate(properties), lease(), properties, transactionManager);
    }

    private DataRetentionExecuteLease lease() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        leaseExecutors.add(executor);
        return new DataRetentionExecuteLease(redisTemplate, properties, new ConcurrentTaskScheduler(executor));
    }

    private DataRetentionP0AuditService audit() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("geo.retention.audit.application-version", "wp6-business-it-v1");
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
        return new RetentionExecuteCommand(UUID.randomUUID().toString(),
                RetentionDomain.WEBSITE_PUBLISHED_CONTENT, ExecutionSource.MANUAL, 7,
                "WP6 real website executor and repository integration", "approved-WP6", null,
                contract, Map.of(
                        "effectiveConfig", encoder.websiteEffectiveConfig(24),
                        "requestLimit", 20),
                null, null, new ExecutionBudget(2, 20, Duration.ofMinutes(10)), null);
    }

    private DataRetentionProperties properties() {
        DataRetentionProperties value = new DataRetentionProperties();
        value.getScheduler().setExecuteEnabled(true);
        value.getWebsitePublishedCleanup().setExecuteEnabled(true);
        value.getScheduler().setLockKey(LEASE_KEY);
        value.getScheduler().setLockTtlSeconds(7200);
        value.getExecute().setMutationTimeoutSeconds(30);
        value.getExecute().setMaxRunSeconds(600);
        value.getExecute().setMaxMutationTransactions(2);
        value.getExecute().setMaxCandidateItems(20);
        return value;
    }

    private void putEnabled(SwitchKey key, String changeId, long version) {
        Payload payload = new Payload(changeId, version, true, NOW);
        redisTemplate.opsForHash().putAll(key.redisKey(), Map.of(
                "change_id", payload.changeId(),
                "version", payload.versionText(),
                "enabled", payload.enabledText(),
                "changed_at", payload.changedAtText()));
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

    private void installWebsiteBusinessSchema() {
        jdbc.execute("""
                CREATE TABLE article_draft (
                    id BIGINT NOT NULL PRIMARY KEY,
                    project_id BIGINT,
                    status VARCHAR(32),
                    updated_at DATETIME(3)
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE article_draft_version (
                    id BIGINT NOT NULL PRIMARY KEY,
                    article_id BIGINT NOT NULL,
                    content_markdown LONGTEXT,
                    content_purged_at DATETIME(3),
                    KEY idx_wp6_version_article (article_id)
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE distribution_tasks (
                    id BIGINT NOT NULL PRIMARY KEY,
                    article_id BIGINT NOT NULL,
                    project_id BIGINT,
                    target_kind VARCHAR(64),
                    status VARCHAR(32),
                    published_url VARCHAR(500),
                    request_payload JSON,
                    fill_payload JSON,
                    response_payload JSON,
                    payload_purged_at DATETIME(3),
                    KEY idx_wp6_task_article (article_id)
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE article_publish_record (
                    id BIGINT NOT NULL PRIMARY KEY,
                    article_id BIGINT NOT NULL,
                    source_type VARCHAR(64),
                    source_id BIGINT,
                    target_kind VARCHAR(64),
                    publish_status VARCHAR(32),
                    published_url VARCHAR(500),
                    url_quality VARCHAR(64),
                    published_at DATETIME(3),
                    verified_at DATETIME(3),
                    created_at DATETIME(3),
                    raw_response JSON,
                    KEY idx_wp6_record_article (article_id),
                    KEY idx_wp6_record_source (source_type, source_id)
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE self_media_publish_schedule (
                    id BIGINT NOT NULL PRIMARY KEY,
                    article_id BIGINT NOT NULL,
                    status VARCHAR(32),
                    KEY idx_wp6_schedule_article (article_id)
                ) ENGINE=InnoDB
                """);
    }

    private void seedCandidates() {
        Timestamp publishedAt = Timestamp.valueOf(LocalDateTime.ofInstant(
                NOW.minus(48, ChronoUnit.HOURS), WebsiteRetentionBusinessRepository.BUSINESS_ZONE));
        Timestamp currentTime = Timestamp.valueOf(LocalDateTime.ofInstant(
                NOW, WebsiteRetentionBusinessRepository.BUSINESS_ZONE));
        String[] targets = {"brand_geo_site", "industry_site", "forum_site"};
        for (long articleId = 1; articleId <= 20; articleId++) {
            boolean payloadOnlyReheat = articleId == 20;
            long taskId = articleId * 100;
            String target = targets[(int) ((articleId - 1) % targets.length)];
            String url = "https://example.test/article/" + articleId;
            jdbc.update("INSERT INTO article_draft (id, project_id, status, updated_at) VALUES (?,?,?,?)",
                    articleId, 9001L, payloadOnlyReheat ? "deleted" : "distributed", currentTime);
            jdbc.update("""
                    INSERT INTO article_draft_version
                        (id, article_id, content_markdown, content_purged_at)
                    VALUES (?, ?, ?, ?)
                    """, articleId * 10, articleId,
                    payloadOnlyReheat ? null : "# retained body " + articleId,
                    payloadOnlyReheat ? currentTime : null);
            jdbc.update("""
                    INSERT INTO distribution_tasks (
                        id, article_id, project_id, target_kind, status, published_url,
                        request_payload, fill_payload, response_payload, payload_purged_at
                    ) VALUES (?, ?, ?, ?, 'submitted', ?,
                              CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), NULL)
                    """, taskId, articleId, 9001L, target, url,
                    payloadOnlyReheat ? null : "{\"request\":true}",
                    payloadOnlyReheat ? null : "{\"fill\":true}",
                    payloadOnlyReheat ? "{\"review_status\":\"under_review\"}" : "{\"response\":true}");
            jdbc.update("""
                    INSERT INTO article_publish_record (
                        id, article_id, source_type, source_id, target_kind, publish_status,
                        published_url, url_quality, published_at, verified_at, created_at, raw_response
                    ) VALUES (?, ?, 'distribution_task', ?, ?, 'distributed', ?, 'verified', ?, ?, ?,
                              CAST(? AS JSON))
                    """, articleId * 1000, articleId, taskId, target, url,
                    publishedAt, currentTime, publishedAt,
                    payloadOnlyReheat ? null : "{\"provider\":\"ok\"}");
        }
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private void connectDataAccess() {
        transactionManager = new DataSourceTransactionManager(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    private void connectRedis() {
        redisTemplate = new StringRedisTemplate(redisFactory);
        redisTemplate.afterPropertiesSet();
    }

    private void executeCreateTable(java.sql.Connection connection, String migration,
                                    String statementPrefix) throws Exception {
        String sql = Files.readString(MIGRATIONS.resolve(migration));
        int start = sql.indexOf(statementPrefix);
        int end = sql.indexOf(';', start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("Missing fixture DDL: " + statementPrefix);
        }
        try (var statement = connection.createStatement()) {
            statement.execute(sql.substring(start, end));
        }
    }
}
