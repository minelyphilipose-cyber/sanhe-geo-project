package com.huanjing.geo.module.retention.poll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.mapper.PollAuditPurgeRunMapper;
import com.huanjing.geo.module.content.service.ArticleBodyPurgeService;
import com.huanjing.geo.module.content.service.ArticleRetentionDryRunService;
import com.huanjing.geo.module.dispatch.service.PollSummaryRecomputeService;
import com.huanjing.geo.module.dispatch.websearch.purge.PollAuditPurgeAuditWriter;
import com.huanjing.geo.module.mobiledashboard.service.MobileDashboardEntityJudgeService;
import com.huanjing.geo.module.retention.audit.canonical.DataRetentionCanonicalEncoder;
import com.huanjing.geo.module.retention.audit.model.DataRetentionAuditModels.RetentionCompatibilityContract;
import com.huanjing.geo.module.retention.audit.port.PollPurgeAuditIdentityWriter;
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
import com.huanjing.geo.module.retention.execute.DataRetentionExecuteCommandFactory;
import com.huanjing.geo.module.retention.execute.DataRetentionExecuteLease;
import com.huanjing.geo.module.retention.execute.DataRetentionExecutionAuthorizationPolicy;
import com.huanjing.geo.module.retention.execute.DataRetentionExecutionBudgetGate;
import com.huanjing.geo.module.retention.execute.DataRetentionStaticExecuteGate;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionBudget;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionResultCode;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.ExecutionSource;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecuteCommand;
import com.huanjing.geo.module.retention.execute.model.DataRetentionExecutionModels.RetentionExecutionResult;
import com.huanjing.geo.module.retention.service.PollRetentionSliceLockService;
import com.huanjing.geo.module.retention.service.PollRetentionDryRunService;
import com.huanjing.geo.module.retention.service.WebsitePublishedContentCleanupService;
import com.huanjing.geo.module.retention.schedule.DataRetentionScheduler;
import com.huanjing.geo.module.retention.schedule.DataRetentionSchedulerLock;
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
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PollRetentionDomainExecutorMysqlRedisIntegrationTest {
    private static final String REQUIRED = "retention.poll-it.required";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final LocalDate OLD_DATE = LocalDate.of(2026, 6, 1);
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration").toAbsolutePath();
    private static final String LEASE_KEY = "geo:retention:wp7-business-it:lease";

    private ExternalMysql externalMysql;
    private LettuceConnectionFactory redisFactory;
    private StringRedisTemplate redis;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private DataRetentionProperties properties;
    private DataRetentionP0AuditService audit;
    private ScheduledExecutorService leaseExecutor;
    private boolean ready;

    @BeforeAll
    void startInfrastructure() throws Exception {
        if (!RetentionExternalTestServices.mysqlConfigured()
                || !RetentionExternalTestServices.redisConfigured()) {
            if (Boolean.getBoolean(REQUIRED)) {
                throw new IllegalStateException("Required WP7 poll MySQL/Redis infrastructure is unavailable");
            }
            return;
        }
        externalMysql = RetentionExternalTestServices.createMysql("geo_retention_wp7_business_it_");
        dataSource = externalMysql.dataSource();
        jdbc = new JdbcTemplate(dataSource);
        transactions = new DataSourceTransactionManager(dataSource);
        redisFactory = RetentionExternalTestServices.redisConnectionFactory();
        redis = new StringRedisTemplate(redisFactory);
        redis.afterPropertiesSet();
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
                .baselineOnMigrate(true).baselineVersion(MigrationVersion.fromVersion("353"))
                .target(MigrationVersion.fromVersion("354")).load().migrate();
        installAuthorizationFixture();
        installPollBusinessSchema();
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                    MIGRATIONS.resolve("V367__poll_retention_execute_and_preserved_cleanup.sql")));
        }
        seedPrimarySlice();
        properties = properties();
        audit = audit();
        redis.delete(List.of(SwitchKey.GLOBAL_EXECUTE.redisKey(),
                SwitchKey.POLL_RESULTS_EXECUTE.redisKey(), LEASE_KEY));
        putEnabled(SwitchKey.GLOBAL_EXECUTE, UUID.randomUUID().toString(), 1);
        putEnabled(SwitchKey.POLL_RESULTS_EXECUTE, UUID.randomUUID().toString(), 1);
    }

    @AfterAll
    void stopInfrastructure() throws Exception {
        if (leaseExecutor != null) leaseExecutor.shutdownNow();
        if (redis != null) redis.delete(List.of(SwitchKey.GLOBAL_EXECUTE.redisKey(),
                SwitchKey.POLL_RESULTS_EXECUTE.redisKey(), LEASE_KEY));
        if (redisFactory != null) redisFactory.destroy();
        if (externalMysql != null) externalMysql.close();
    }

    @Test
    void realCoordinatorRepositoryAndLegacyAuditCommitPrimaryThenCleanupOnly() {
        DataRetentionExecuteCoordinator coordinator = coordinator();
        RetentionExecutionResult primary = coordinator.execute(command(1));

        assertNotNull(primary.runId());
        assertEquals(ExecutionResultCode.COMPLETED, primary.code(), () -> attemptFailure(primary.runId()));
        assertEquals(1, primary.candidateCount());
        assertEquals(1, primary.affectedCount());
        assertEquals(0, count("SELECT COUNT(*) FROM poll_results WHERE id=1"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_results WHERE id=2"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_invocation_attempts WHERE poll_result_id=1"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_invocation_attempts WHERE poll_result_id=2"));
        assertEquals(0, count("SELECT COUNT(*) FROM poll_batch_shards"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_purged_slice WHERE status='purged'"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_poll_preserved_result"
                + " WHERE poll_result_id=2 AND cleanup_status='preserved'"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_audit_purge_runs WHERE status='SUCCEEDED'"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_operation"
                + " WHERE operation_type='poll_slice' AND outcome_status='committed'"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_batch_attempt WHERE status='succeeded'"));
        assertEquals(0, count("SELECT COUNT(*) FROM data_retention_batch_item"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_keyword_daily_summary"));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(LEASE_KEY)));

        seedNewerProjection();
        RetentionExecutionResult cleanup = coordinator.execute(command(1));

        assertEquals(ExecutionResultCode.COMPLETED, cleanup.code());
        assertEquals(1, cleanup.candidateCount());
        assertEquals(1, cleanup.affectedCount());
        assertEquals(0, count("SELECT COUNT(*) FROM poll_results WHERE id=2"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_results WHERE id=3"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_poll_preserved_result"
                + " WHERE poll_result_id=2 AND cleanup_status='cleaned' AND cleanup_operation_id IS NOT NULL"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_operation"
                + " WHERE operation_type='poll_preserved_cleanup' AND outcome_status='committed'"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_batch_item WHERE business_key=2"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_purged_slice"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_audit_purge_runs"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_keyword_daily_summary"));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(LEASE_KEY)));
    }

    @Test
    void cleanupConsumesFirstOfTwentyRealTransactionsAndLeavesOnePrimarySlice() {
        DataRetentionExecuteCoordinator coordinator = coordinator();
        assertEquals(ExecutionResultCode.COMPLETED, coordinator.execute(command(1)).code());
        seedNewerProjection();
        seedPrimaryBacklog(20);

        RetentionExecutionResult result = coordinator.execute(command(20));

        assertEquals(ExecutionResultCode.COMPLETED, result.code());
        assertEquals(20, result.candidateCount());
        assertEquals(20, result.affectedCount());
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_poll_preserved_result"
                + " WHERE poll_result_id=2 AND cleanup_status='cleaned'"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_operation"
                + " WHERE origin_run_group_id=" + result.runId()
                + " AND operation_type='poll_preserved_cleanup' AND outcome_status='committed'"));
        assertEquals(19, count("SELECT COUNT(*) FROM data_retention_operation"
                + " WHERE origin_run_group_id=" + result.runId()
                + " AND operation_type='poll_slice' AND outcome_status='committed'"));
        assertEquals(20, count("SELECT COUNT(*) FROM data_retention_batch_attempt"
                + " WHERE run_group_id=" + result.runId() + " AND status='succeeded'"));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM poll_results pr
                 WHERE pr.project_id=71 AND pr.batch_date BETWEEN '2026-06-02' AND '2026-06-21'
                   AND NOT EXISTS (
                     SELECT 1 FROM data_retention_purged_slice marker
                      WHERE marker.domain='poll_results' AND marker.project_id=pr.project_id
                        AND marker.batch_date=pr.batch_date
                        AND marker.question_tier=pr.question_tier AND marker.status='purged')
                """));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(LEASE_KEY)));
    }

    @Test
    void schedulerDeferredRetryRestoresOriginWindowAndCommitsHeldPrimaryOperation() {
        DataRetentionExecuteCoordinator coordinator = coordinator(true);

        RetentionExecutionResult stopped = coordinator.execute(command(1));

        assertEquals(ExecutionResultCode.EXECUTE_DYNAMIC_DISABLED, stopped.code());
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_operation"
                + " WHERE operation_type='poll_slice' AND outcome_status='rolled_back'"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_batch_attempt"
                + " WHERE status='skipped' AND last_error LIKE 'recovery_not_started:%'"));
        assertEquals(1, count("SELECT COUNT(*) FROM poll_results WHERE id=1"));

        putState(SwitchKey.POLL_RESULTS_EXECUTE, true, 3);
        DataRetentionExecuteCommandFactory commandFactory = new DataRetentionExecuteCommandFactory(
                new DataRetentionCanonicalEncoder(), new DataRetentionExecutionBudgetGate(properties), properties);
        DataRetentionScheduler scheduler = new DataRetentionScheduler(properties,
                mock(WebsitePublishedContentCleanupService.class), mock(ArticleRetentionDryRunService.class),
                mock(ArticleBodyPurgeService.class), mock(PollRetentionDryRunService.class),
                mock(DataRetentionSchedulerLock.class), coordinator, commandFactory, audit);

        scheduler.runDryRunSuite();

        assertEquals(0, count("SELECT COUNT(*) FROM poll_results WHERE id=1"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_operation"
                + " WHERE operation_type='poll_slice' AND outcome_status='committed'"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_run"
                + " WHERE trigger_source='retry' AND status='succeeded'"
                + " AND retention_window_start IS NULL AND retention_window_end='2026-07-31'"));
        assertEquals(1, count("SELECT COUNT(*) FROM data_retention_operation_run"
                + " WHERE relation_type='retry'"));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(LEASE_KEY)));
    }

    private DataRetentionExecuteCoordinator coordinator() {
        return coordinator(false);
    }

    private DataRetentionExecuteCoordinator coordinator(boolean disablePollAfterAdmissionCheck) {
        PollSummaryRecomputeService summary = mock(PollSummaryRecomputeService.class);
        when(summary.verifySlice(any(), any(), any())).thenReturn(
                new PollSummaryRecomputeService.SummaryVerification(1, 1, 1, true, 1, 1, true));
        MobileDashboardEntityJudgeService entity = mock(MobileDashboardEntityJudgeService.class);
        when(entity.verifySummarySlice(any(), any(), any())).thenReturn(
                new MobileDashboardEntityJudgeService.EntityJudgeSummaryVerification(0, 0, true));
        PollRetentionBusinessRepository business = new PollRetentionBusinessRepository(jdbc,
                new PollRetentionSliceLockService(jdbc), summary, entity, transactions);
        PollAuditPurgeAuditWriter legacy = new PollAuditPurgeAuditWriter(
                mock(PollAuditPurgeRunMapper.class), jdbc);
        PollRetentionDomainExecutor executor = new PollRetentionDomainExecutor(business, legacy,
                new PollPurgeAuditIdentityWriter(jdbc), new DataRetentionAuditJsonCodec(new ObjectMapper()),
                Clock.fixed(NOW, ZoneOffset.UTC));
        DataRetentionDynamicSwitchMetrics metrics = new DataRetentionDynamicSwitchMetrics(new SimpleMeterRegistry());
        DataRetentionDynamicSwitchRedisStore store = new DataRetentionDynamicSwitchRedisStore(redis, metrics);
        DataRetentionDynamicSwitchAlertSink alerts = (code, key, detail) -> { };
        DataRetentionDynamicSwitchGate redisGate = new DataRetentionDynamicSwitchGate.RedisGate(
                store, alerts, metrics);
        AtomicInteger checks = new AtomicInteger();
        DataRetentionDynamicSwitchGate gate = new DataRetentionDynamicSwitchGate() {
            @Override
            public com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Decision check(
                    RetentionDomain domain) {
                return redisGate.check(domain);
            }

            @Override
            public com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Decision
            requireEnabled(RetentionDomain domain) {
                var decision = redisGate.requireEnabled(domain);
                if (disablePollAfterAdmissionCheck && domain == RetentionDomain.POLL_RESULTS
                        && checks.getAndIncrement() == 0) {
                    putState(SwitchKey.POLL_RESULTS_EXECUTE, false, 2);
                }
                return decision;
            }
        };
        return new DataRetentionExecuteCoordinator(audit,
                new DataRetentionDomainExecutorRegistry(List.of(executor)),
                new DataRetentionExecutionAuthorizationPolicy(jdbc),
                new DataRetentionStaticExecuteGate(properties), gate,
                new DataRetentionExecutionBudgetGate(properties), lease(), properties, transactions);
    }

    private DataRetentionExecuteLease lease() {
        if (leaseExecutor != null) leaseExecutor.shutdownNow();
        leaseExecutor = Executors.newSingleThreadScheduledExecutor();
        return new DataRetentionExecuteLease(redis, properties,
                new ConcurrentTaskScheduler(leaseExecutor));
    }

    private DataRetentionP0AuditService audit() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("geo.retention.audit.application-version", "wp7-business-it-v1");
        return new DataRetentionP0AuditService(jdbc, new DataRetentionCanonicalEncoder(),
                new DataRetentionAuditJsonCodec(new ObjectMapper()),
                new DataRetentionApplicationVersionProvider(environment),
                new DataRetentionAuditClock(Clock.fixed(NOW, ZoneOffset.UTC)), transactions);
    }

    private RetentionExecuteCommand command(int maxTransactions) {
        DataRetentionCanonicalEncoder encoder = new DataRetentionCanonicalEncoder();
        RetentionCompatibilityContract contract = new RetentionCompatibilityContract(
                "poll_results", 1, encoder.pollFingerprint(14, 7).sha256(),
                DataRetentionCanonicalEncoder.POLL_POLICY_VERSION);
        return new RetentionExecuteCommand(UUID.randomUUID().toString(), RetentionDomain.POLL_RESULTS,
                ExecutionSource.MANUAL, 7, "WP7 real poll executor and repository integration", "approved-WP7",
                null, contract, Map.of(
                "effectiveConfig", encoder.pollEffectiveConfig(14, 7),
                "requestLimit", 20, "projectId", 71L,
                "effectiveEndDate", "2026-07-31"),
                null, LocalDate.of(2026, 7, 31),
                new ExecutionBudget(maxTransactions, 20, Duration.ofMinutes(10)), null);
    }

    private DataRetentionProperties properties() {
        DataRetentionProperties value = new DataRetentionProperties();
        value.getScheduler().setExecuteEnabled(true);
        value.getScheduler().setOperatorUserId(7L);
        value.getPollResults().setExecuteEnabled(true);
        value.getScheduler().setLockKey(LEASE_KEY);
        value.getScheduler().setLockTtlSeconds(7200);
        value.getExecute().setMutationTimeoutSeconds(30);
        value.getExecute().setMaxRunSeconds(600);
        value.getExecute().setMaxMutationTransactions(20);
        value.getExecute().setMaxCandidateItems(20);
        return value;
    }

    private void putEnabled(SwitchKey key, String changeId, long version) {
        putState(key, true, version, changeId);
    }

    private void putState(SwitchKey key, boolean enabled, long version) {
        putState(key, enabled, version, UUID.randomUUID().toString());
    }

    private void putState(SwitchKey key, boolean enabled, long version, String changeId) {
        Payload payload = new Payload(changeId, version, enabled, NOW);
        redis.opsForHash().putAll(key.redisKey(), Map.of(
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
        jdbc.update("INSERT INTO sys_permission VALUES (2,'data.retention.execute.retry','active')");
        jdbc.update("INSERT INTO sys_role_permission VALUES (1,1),(1,2)");
    }

    private void installPollBusinessSchema() {
        jdbc.execute("CREATE TABLE poll_batches (id BIGINT PRIMARY KEY,project_id BIGINT,batch_date DATE,"
                + "question_tier VARCHAR(16) COLLATE utf8mb4_0900_ai_ci,status VARCHAR(32)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE poll_results (id BIGINT PRIMARY KEY,project_id BIGINT,batch_date DATE,"
                + "question_tier VARCHAR(16) COLLATE utf8mb4_0900_ai_ci,status VARCHAR(32),keyword_result_id BIGINT,"
                + "keyword_text_snapshot VARCHAR(500),channel_code VARCHAR(64),platform_code VARCHAR(64),"
                + "created_at DATETIME(3),updated_at DATETIME(3)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE poll_invocation_attempts (id BIGINT PRIMARY KEY,poll_result_id BIGINT,status VARCHAR(32)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE poll_provider_calls (id BIGINT PRIMARY KEY,attempt_id BIGINT) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE poll_search_sources (id BIGINT PRIMARY KEY,attempt_id BIGINT) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE poll_citations (id BIGINT PRIMARY KEY,attempt_id BIGINT) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE poll_result_entity_judge (id BIGINT PRIMARY KEY,poll_result_id BIGINT) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE poll_batch_shards (id BIGINT PRIMARY KEY,project_id BIGINT,batch_date DATE,question_tier VARCHAR(16)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE poll_batch_shard_items (id BIGINT PRIMARY KEY,shard_id BIGINT) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE poll_keyword_daily_summary (id BIGINT PRIMARY KEY,project_id BIGINT,batch_date DATE,question_tier VARCHAR(16),metric_value VARCHAR(64)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE poll_platform_daily_summary (id BIGINT PRIMARY KEY,project_id BIGINT,batch_date DATE,question_tier VARCHAR(16),metric_value VARCHAR(64)) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE poll_entity_judge_daily_summary (id BIGINT PRIMARY KEY,project_id BIGINT,batch_date DATE,question_tier VARCHAR(16),metric_value VARCHAR(64)) ENGINE=InnoDB");
    }

    private void seedPrimarySlice() {
        jdbc.update("INSERT INTO poll_batches VALUES (1,71,?,'A','finished')", java.sql.Date.valueOf(OLD_DATE));
        insertResult(1, OLD_DATE, LocalDateTime.of(2026, 6, 1, 1, 0));
        insertResult(2, OLD_DATE, LocalDateTime.of(2026, 6, 1, 2, 0));
        jdbc.update("INSERT INTO poll_invocation_attempts VALUES (11,1,'SUCCEEDED'),(12,2,'SUCCEEDED')");
        jdbc.update("INSERT INTO poll_provider_calls VALUES (21,11),(22,12)");
        jdbc.update("INSERT INTO poll_search_sources VALUES (31,11),(32,12)");
        jdbc.update("INSERT INTO poll_citations VALUES (41,11),(42,12)");
        jdbc.update("INSERT INTO poll_result_entity_judge VALUES (51,1),(52,2)");
        jdbc.update("INSERT INTO poll_batch_shards VALUES (61,71,?,'A')", java.sql.Date.valueOf(OLD_DATE));
        jdbc.update("INSERT INTO poll_batch_shard_items VALUES (71,61)");
        jdbc.update("INSERT INTO poll_keyword_daily_summary VALUES (81,71,?,'A','stable')",
                java.sql.Date.valueOf(OLD_DATE));
    }

    private void seedNewerProjection() {
        insertResult(3, LocalDate.of(2026, 8, 10), LocalDateTime.of(2026, 8, 10, 2, 0));
    }

    private void seedPrimaryBacklog(int count) {
        for (int index = 0; index < count; index++) {
            LocalDate date = LocalDate.of(2026, 6, 2).plusDays(index);
            long id = 100L + index;
            jdbc.update("INSERT INTO poll_batches VALUES (?,?,?,'A','finished')",
                    id, 71L, java.sql.Date.valueOf(date));
            insertResult(id, date, date.atTime(2, 0));
        }
    }

    private void insertResult(long id, LocalDate date, LocalDateTime updatedAt) {
        Timestamp timestamp = Timestamp.valueOf(updatedAt);
        jdbc.update("INSERT INTO poll_results (id,project_id,batch_date,question_tier,status,keyword_result_id,"
                        + "keyword_text_snapshot,channel_code,platform_code,created_at,updated_at)"
                        + " VALUES (?,71,?,'A','completed',101,'question','doubao','doubao',?,?)",
                id, java.sql.Date.valueOf(date), timestamp, timestamp);
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private String attemptFailure(Long runId) {
        if (runId == null) return "run id missing";
        List<String> values = jdbc.queryForList("SELECT COALESCE(last_error,'') FROM data_retention_batch_attempt "
                + "WHERE run_group_id=? ORDER BY id", String.class, runId);
        return String.join(" | ", values);
    }

    private void executeCreateTable(java.sql.Connection connection, String migration,
                                    String statementPrefix) throws Exception {
        String sql = Files.readString(MIGRATIONS.resolve(migration));
        int start = sql.indexOf(statementPrefix);
        int end = sql.indexOf(';', start);
        if (start < 0 || end < 0) throw new IllegalStateException("Missing fixture DDL: " + statementPrefix);
        try (var statement = connection.createStatement()) {
            statement.execute(sql.substring(start, end));
        }
    }
}
