package com.huanjing.geo.module.retention.dynamicswitch;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository.ChangeRow;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchChangeRepository.CreateCommand;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.CasResultCode;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.ChangeStatus;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.DynamicSwitchException;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.Payload;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.RequestSource;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SnapshotIntegrity;
import com.huanjing.geo.module.retention.dynamicswitch.DataRetentionDynamicSwitchModels.SwitchKey;
import com.huanjing.geo.module.retention.support.RetentionExternalTestServices;
import com.huanjing.geo.module.retention.support.RetentionExternalTestServices.ExternalMysql;
import com.huanjing.geo.module.system.entity.SysUser;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataRetentionDynamicSwitchMysqlRedisIntegrationTest {

    private static final String REQUIRED = "retention.switch-it.required";
    private static final String UNAVAILABLE = "Docker is unavailable for retention-switch-it";
    private static final String ID = "0f4e3df1-331b-4c61-9b94-8f53866d02da";
    private static final Instant AT = Instant.parse("2026-08-12T06:30:45.123Z");

    private MySQLContainer<?> mysql;
    private GenericContainer<?> redis;
    private ExternalMysql externalMysql;
    private DataSource dataSource;
    private boolean externalInfrastructure;
    private LettuceConnectionFactory redisConnectionFactory;
    private StringRedisTemplate redisTemplate;
    private JdbcTemplate jdbc;
    private DataRetentionDynamicSwitchRedisStore redisStore;
    private DataRetentionDynamicSwitchChangeRepository repository;
    private DataRetentionDynamicSwitchMetrics metrics;
    private DataRetentionProperties properties;
    private DataRetentionDynamicSwitchAuthorizationPolicy authorization;
    private DataRetentionDynamicSwitchAlertSink alertSink;
    private final List<LettuceConnectionFactory> extraRedisConnectionFactories = new ArrayList<>();
    private boolean ready;

    @BeforeAll
    void startInfrastructure() throws Exception {
        if (RetentionExternalTestServices.mysqlConfigured()
                && RetentionExternalTestServices.redisConfigured()) {
            externalMysql = RetentionExternalTestServices.createMysql("geo_retention_wp4_it_");
            dataSource = externalMysql.dataSource();
            redisConnectionFactory = RetentionExternalTestServices.redisConnectionFactory();
            externalInfrastructure = true;
        } else {
            if (!dockerAvailable()) {
                if (Boolean.getBoolean(REQUIRED)) throw new IllegalStateException(UNAVAILABLE);
                return;
            }
            mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                    .withDatabaseName("geo_retention_wp4_it")
                    .withUsername("test").withPassword("test")
                    .withCommand("--default-time-zone=+08:00");
            redis = new GenericContainer<>(DockerImageName.parse("redis:7.2.5-alpine")).withExposedPorts(6379);
            mysql.start();
            redis.start();
            dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
            RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                    redis.getHost(), redis.getMappedPort(6379));
            redisConnectionFactory = new LettuceConnectionFactory(configuration);
            redisConnectionFactory.afterPropertiesSet();
            redisConnectionFactory.start();
        }

        jdbc = new JdbcTemplate(dataSource);
        installSwitchTableFixture();
        jdbc.execute("SET time_zone = '+08:00'");
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        repository = new DataRetentionDynamicSwitchChangeRepository(jdbc, transactionManager);
        installAuthorizationFixture();

        redisTemplate = new StringRedisTemplate(redisConnectionFactory);
        redisTemplate.afterPropertiesSet();
        metrics = new DataRetentionDynamicSwitchMetrics(new SimpleMeterRegistry());
        redisStore = new DataRetentionDynamicSwitchRedisStore(redisTemplate, metrics);
        properties = new DataRetentionProperties();
        properties.getDynamicSwitch().setReconcileOperatorUserId(9001L);
        properties.getDynamicSwitch().setEnableWriteEnabled(false);
        authorization = new DataRetentionDynamicSwitchAuthorizationPolicy(
                mock(com.huanjing.geo.module.system.service.CurrentUserService.class), jdbc);
        alertSink = (code, key, detail) -> { };
        ready = true;
    }

    @BeforeEach
    void clean() throws Exception {
        Assumptions.assumeTrue(ready, UNAVAILABLE);
        setRedisScriptsAllowed(true);
        properties.getDynamicSwitch().setEnableWriteEnabled(false);
        properties.getDynamicSwitch().setReconcilerEnabled(true);
        properties.getDynamicSwitch().setReconcileOperatorUserId(9001L);
        jdbc.execute("DROP TRIGGER IF EXISTS wp4_fail_compensation");
        jdbc.update("DELETE FROM data_retention_dynamic_switch_change");
        redisTemplate.delete(List.of(
                SwitchKey.GLOBAL_EXECUTE.redisKey(),
                SwitchKey.WEBSITE_PUBLISHED_CONTENT_EXECUTE.redisKey(),
                SwitchKey.POLL_RESULTS_EXECUTE.redisKey()));
    }

    @AfterAll
    void stopInfrastructure() throws Exception {
        if (redisTemplate != null) {
            redisTemplate.delete(List.of(
                    SwitchKey.GLOBAL_EXECUTE.redisKey(),
                    SwitchKey.WEBSITE_PUBLISHED_CONTENT_EXECUTE.redisKey(),
                    SwitchKey.POLL_RESULTS_EXECUTE.redisKey()));
        }
        extraRedisConnectionFactories.forEach(LettuceConnectionFactory::destroy);
        if (redisConnectionFactory != null) redisConnectionFactory.destroy();
        if (redis != null) redis.stop();
        if (mysql != null) mysql.stop();
        if (externalMysql != null) externalMysql.close();
    }

    @AfterEach
    void restoreFailClosedTestNamespace() throws Exception {
        if (!ready) return;
        setRedisScriptsAllowed(true);
        properties.getDynamicSwitch().setEnableWriteEnabled(false);
        extraRedisConnectionFactories.forEach(LettuceConnectionFactory::destroy);
        extraRedisConnectionFactories.clear();
        redisTemplate.delete(List.of(
                SwitchKey.GLOBAL_EXECUTE.redisKey(),
                SwitchKey.WEBSITE_PUBLISHED_CONTENT_EXECUTE.redisKey(),
                SwitchKey.POLL_RESULTS_EXECUTE.redisKey()));
        assertFalse(properties.getDynamicSwitch().isEnableWriteEnabled());
        for (SwitchKey key : SwitchKey.values()) {
            assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(key.redisKey())));
        }
    }

    @Test
    void missingPairIsDisabledAndReadOnly() {
        var pair = redisStore.readPair(SwitchKey.POLL_RESULTS_EXECUTE);
        assertEquals(SnapshotIntegrity.MISSING, pair.global().integrity());
        assertEquals(SnapshotIntegrity.MISSING, pair.domain().integrity());
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(SwitchKey.GLOBAL_EXECUTE.redisKey())));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(SwitchKey.POLL_RESULTS_EXECUTE.redisKey())));
    }

    @Test
    void gateReadsGlobalAndDomainAtomicallyAndPrioritizesMalformedDomain() {
        writePayload(SwitchKey.GLOBAL_EXECUTE, new Payload(ID, 1, false, AT));
        redisTemplate.opsForHash().putAll(SwitchKey.POLL_RESULTS_EXECUTE.redisKey(),
                Map.of("change_id", ID, "version", "1", "enabled", "1", "changed_at", "not-a-time"));
        var pair = redisStore.readPair(SwitchKey.POLL_RESULTS_EXECUTE);
        assertEquals(SnapshotIntegrity.VALID, pair.global().integrity());
        assertEquals(SnapshotIntegrity.MALFORMED, pair.domain().integrity());
        assertEquals(DataRetentionDynamicSwitchModels.DecisionCode.MALFORMED_DOMAIN_STATE,
                DataRetentionDynamicSwitchGate.RedisGate.decide(pair));
    }

    @Test
    void disableCasWritesExactlyFourFieldsAndRepositoryFinalizes() {
        ChangeRow row = repository.createRequested(new CreateCommand(ID, SwitchKey.POLL_RESULTS_EXECUTE,
                false, AT, 11L, RequestSource.MANUAL, "WP4 integration test", null, "OPS-1"), null);
        var result = redisStore.disableCas(row.switchKey(), row.payload());
        assertEquals(CasResultCode.APPLIED, result.code());
        assertEquals(4L, redisTemplate.opsForHash().size(row.switchKey().redisKey()));
        assertEquals(-1L, redisTemplate.getExpire(row.switchKey().redisKey()));
        ChangeRow applied = repository.finalizeApplied(row, result.observed().payload(), AT.plusSeconds(1));
        assertEquals(ChangeStatus.APPLIED, applied.status());
        assertEquals(AT, applied.changedAt());
    }

    @Test
    void enableRequiresExpectedStateAndRedisTimeGrace() {
        ChangeRow row = repository.createRequested(command(ID, true, 11L), null);
        Instant approvedAt = redisStore.redisTime();
        ChangeRow approved = repository.approve(ID, 12L, "CAB-2026-0812", approvedAt);
        var result = redisStore.enableCas(approved.switchKey(), approved.payload(),
                DataRetentionDynamicSwitchModels.Snapshot.missing(approved.switchKey()), approvedAt);
        assertEquals(CasResultCode.APPLIED, result.code());
        assertTrue(result.observed().enabled());
        assertEquals(CasResultCode.ALREADY_APPLIED,
                redisStore.enableCas(approved.switchKey(), approved.payload(),
                        DataRetentionDynamicSwitchModels.Snapshot.missing(approved.switchKey()), approvedAt).code());
    }

    @Test
    void selfApprovalAndDifferentApproverReplayAreRejected() {
        repository.createRequested(command(ID, true, 11L), null);
        Instant approvalTime = redisStore.redisTime();
        assertThrows(RuntimeException.class, () -> repository.approve(ID, 11L, "CAB-2026-0812", approvalTime));
        repository.approve(ID, 12L, "CAB-2026-0812", approvalTime);
        assertThrows(RuntimeException.class, () -> repository.approve(ID, 13L, "CAB-2026-0812", approvalTime));
    }

    @Test
    void malformedAndSameVersionDifferentPayloadNeverOverwrite() {
        String key = SwitchKey.POLL_RESULTS_EXECUTE.redisKey();
        redisTemplate.opsForHash().putAll(key, Map.of("change_id", ID, "version", "1", "enabled", "true", "changed_at", "bad"));
        Payload target = new Payload(UUID.randomUUID().toString(), 2, false, AT);
        assertEquals(CasResultCode.MALFORMED_CURRENT, redisStore.disableCas(SwitchKey.POLL_RESULTS_EXECUTE, target).code());
        assertEquals("true", redisTemplate.opsForHash().get(key, "enabled"));

        redisTemplate.delete(key);
        redisTemplate.opsForHash().putAll(key, Map.of("change_id", ID, "version", "2", "enabled", "1",
                "changed_at", "2026-08-12T06:30:45.123Z"));
        assertEquals(CasResultCode.SAME_VERSION_DIFFERENT_PAYLOAD,
                redisStore.disableCas(SwitchKey.POLL_RESULTS_EXECUTE, target).code());
        assertEquals("1", redisTemplate.opsForHash().get(key, "enabled"));
    }

    @Test
    void invalidCalendarDateIsMalformedAndPreserved() {
        String key = SwitchKey.POLL_RESULTS_EXECUTE.redisKey();
        redisTemplate.opsForHash().putAll(key, Map.of("change_id", ID, "version", "1", "enabled", "0",
                "changed_at", "2026-02-31T06:30:45.123Z"));
        assertEquals(SnapshotIntegrity.MALFORMED, redisStore.read(SwitchKey.POLL_RESULTS_EXECUTE).integrity());
        assertEquals(CasResultCode.MALFORMED_CURRENT,
                redisStore.disableCas(SwitchKey.POLL_RESULTS_EXECUTE,
                        new Payload(UUID.randomUUID().toString(), 2, false, AT)).code());
        assertEquals("2026-02-31T06:30:45.123Z", redisTemplate.opsForHash().get(key, "changed_at"));
    }

    @Test
    void versionsAboveLuaSafeIntegerStillCompareExactly() {
        String key = SwitchKey.POLL_RESULTS_EXECUTE.redisKey();
        Payload current = new Payload(ID, 9_007_199_254_740_993L, false, AT);
        writePayload(SwitchKey.POLL_RESULTS_EXECUTE, current);
        Payload lower = new Payload(UUID.randomUUID().toString(), 9_007_199_254_740_992L, false, AT.plusMillis(1));
        assertEquals(CasResultCode.STALE_TARGET,
                redisStore.disableCas(SwitchKey.POLL_RESULTS_EXECUTE, lower).code());
        assertEquals(current.versionText(), redisTemplate.opsForHash().get(key, "version"));
    }

    @Test
    void mysqlNonUtcSessionStillRoundTripsUtcMillis() {
        ChangeRow row = repository.createRequested(new CreateCommand(ID, SwitchKey.POLL_RESULTS_EXECUTE,
                false, AT, 11L, RequestSource.MANUAL, "UTC fixture", null, null), null);
        assertEquals(AT, row.changedAt());
        assertEquals(AT, repository.findByChangeId(ID).orElseThrow().changedAt());
    }

    @Test
    void changeIdReplayAndConflictAreStrict() {
        ChangeRow first = repository.createRequested(command(ID, false, 11L), null);
        assertEquals(first.id(), repository.createRequested(command(ID, false, 11L), null).id());
        assertThrows(RuntimeException.class, () -> repository.createRequested(command(ID, false, 12L), null));
    }

    @Test
    void newerDisableAtomicallySupersedesRequestedEnable() {
        ChangeRow enable = repository.createRequested(command(ID, true, 11L), null);
        String disableId = UUID.randomUUID().toString();
        ChangeRow disable = repository.createRequested(command(disableId, false, 12L), null);
        assertEquals(enable.version() + 1, disable.version());
        assertEquals(ChangeStatus.FAILED, repository.findByChangeId(ID).orElseThrow().status());
        assertEquals("superseded_by_disable", repository.findByChangeId(ID).orElseThrow().lastError());
    }

    @Test
    void newerEmergencyDisablePreventsOlderEnableCas() {
        ChangeRow enable = repository.createRequested(command(ID, true, 11L), null);
        ChangeRow approved = repository.approve(ID, 12L, "CAB-2026-0812", redisStore.redisTime());
        ChangeRow disable = repository.createRequested(command(UUID.randomUUID().toString(), false, 13L), null);
        var disableResult = redisStore.disableCas(disable.switchKey(), disable.payload());
        assertEquals(CasResultCode.APPLIED, disableResult.code());
        var oldEnableResult = redisStore.enableCas(approved.switchKey(), approved.payload(),
                DataRetentionDynamicSwitchModels.Snapshot.missing(approved.switchKey()), approved.approvedAt());
        assertEquals(CasResultCode.STALE_TARGET, oldEnableResult.code());
        assertEquals("0", redisTemplate.opsForHash().get(disable.switchKey().redisKey(), "enabled"));
    }

    @Test
    void redisRestartKeyLossNeverRestoresHistoricalTrue() {
        ChangeRow enabled = repository.createRequested(command(ID, true, 11L), null);
        ChangeRow approved = repository.approve(ID, 12L, "CAB-2026-0812", redisStore.redisTime());
        var result = redisStore.enableCas(approved.switchKey(), approved.payload(),
                DataRetentionDynamicSwitchModels.Snapshot.missing(approved.switchKey()), approved.approvedAt());
        repository.finalizeApplied(approved, result.observed().payload(), AT.plusSeconds(1));
        redisTemplate.delete(approved.switchKey().redisKey());
        assertEquals(SnapshotIntegrity.MISSING, redisStore.read(approved.switchKey()).integrity());
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(approved.switchKey().redisKey())));
    }

    @Test
    void permissionMigrationIsIdempotentAndExact() {
        installRbacFixture();
        Flyway.configure().dataSource(requireDataSource()).locations("filesystem:"
                        + Path.of("src/main/resources/db/migration").toAbsolutePath().toString().replace('\\', '/'))
                .baselineOnMigrate(true).baselineVersion(MigrationVersion.fromVersion("358"))
                .target(MigrationVersion.fromVersion("359")).load().migrate();
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE perm_key LIKE 'data.retention.switch.%'", Integer.class));
        assertEquals(11, jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_role_permission rp
                JOIN sys_permission p ON p.id=rp.permission_id
                WHERE p.perm_key LIKE 'data.retention.switch.%'
                """, Integer.class));
    }

    @Test
    void enableCasRejectsRedisTimeRollbackWithoutWriting() {
        Payload target = new Payload(ID, 1, true, redisStore.redisTime());
        var result = redisStore.enableCas(SwitchKey.POLL_RESULTS_EXECUTE, target,
                DataRetentionDynamicSwitchModels.Snapshot.missing(SwitchKey.POLL_RESULTS_EXECUTE),
                redisStore.redisTime().plusSeconds(10));
        assertEquals(CasResultCode.REDIS_TIME_ROLLBACK, result.code());
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(SwitchKey.POLL_RESULTS_EXECUTE.redisKey())));
    }

    @Test
    void enableCasRejectsExpiredApprovalWithoutWriting() {
        Payload target = new Payload(ID, 1, true, redisStore.redisTime());
        var result = redisStore.enableCas(SwitchKey.POLL_RESULTS_EXECUTE, target,
                DataRetentionDynamicSwitchModels.Snapshot.missing(SwitchKey.POLL_RESULTS_EXECUTE),
                redisStore.redisTime().minusMillis(DataRetentionDynamicSwitchModels.ENABLE_CAS_GRACE_MILLIS + 1));
        assertEquals(CasResultCode.ENABLE_APPROVAL_EXPIRED, result.code());
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(SwitchKey.POLL_RESULTS_EXECUTE.redisKey())));
    }

    @Test
    void exactTargetWinsAfterGraceForLostResponseRecovery() {
        Instant approvedAt = redisStore.redisTime().minusMillis(
                DataRetentionDynamicSwitchModels.ENABLE_CAS_GRACE_MILLIS + 1);
        Payload target = new Payload(ID, 1, true, approvedAt);
        writePayload(SwitchKey.POLL_RESULTS_EXECUTE, target);
        assertEquals(CasResultCode.ALREADY_APPLIED, redisStore.enableCas(
                SwitchKey.POLL_RESULTS_EXECUTE, target,
                DataRetentionDynamicSwitchModels.Snapshot.missing(SwitchKey.POLL_RESULTS_EXECUTE),
                approvedAt).code());
    }

    @Test
    void approvalAfterThirtyMinutesFailsWithoutApprovalFacts() {
        Instant changedAt = redisStore.redisTime().minusMillis(
                DataRetentionDynamicSwitchModels.ENABLE_APPROVAL_EXPIRY_MILLIS + 1);
        repository.createRequested(new CreateCommand(ID, SwitchKey.POLL_RESULTS_EXECUTE, true, changedAt,
                11L, RequestSource.MANUAL, "expired approval", "CAB-2026-0812", null), null);
        ChangeRow failed = repository.approve(ID, 12L, "CAB-2026-0812", redisStore.redisTime());
        assertEquals(ChangeStatus.FAILED, failed.status());
        assertEquals("approval_expired", failed.lastError());
        assertEquals(null, failed.approvedAt());
    }

    @Test
    void casTimeoutBeforeWriteRecoversFromRequested() {
        ChangeRow row = repository.createRequested(command(ID, false, 11L), null);
        DataRetentionDynamicSwitchRedisStore timeout = faultInjectingStore(FaultMode.BEFORE_COMMAND);
        var failing = disableService(timeout, Clock.systemUTC());
        assertThrows(RuntimeException.class, () -> failing.resume(row));
        assertEquals(ChangeStatus.REQUESTED, repository.findByChangeId(ID).orElseThrow().status());
        assertEquals(ChangeStatus.APPLIED, disableService(redisStore, Clock.systemUTC()).resume(row).status());
    }

    @Test
    void casTimeoutAfterActualWriteRecoversWithoutSecondMutation() {
        ChangeRow row = repository.createRequested(command(ID, false, 11L), null);
        DataRetentionDynamicSwitchRedisStore timeout = faultInjectingStore(FaultMode.AFTER_COMMAND);
        assertThrows(RuntimeException.class, () -> disableService(timeout, Clock.systemUTC()).resume(row));
        ChangeRow recovered = disableService(redisStore, Clock.systemUTC()).resume(row);
        assertEquals(ChangeStatus.APPLIED, recovered.status());
        assertEquals(ID, redisTemplate.opsForHash().get(row.switchKey().redisKey(), "change_id"));
    }

    @Test
    void concurrentVersionAllocationUsesDistinctIncreasingVersions() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ChangeRow> first = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return repository.createRequested(command(UUID.randomUUID().toString(), false, 11L), null);
            });
            Future<ChangeRow> second = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return repository.createRequested(command(UUID.randomUUID().toString(), false, 12L), null);
            });
            long left = first.get(15, TimeUnit.SECONDS).version();
            long right = second.get(15, TimeUnit.SECONDS).version();
            assertTrue(left != right);
            assertEquals(2L, Math.max(left, right));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void twoWorkersFinalizeOneDisableIdempotently() throws Exception {
        ChangeRow row = repository.createRequested(command(ID, false, 11L), null);
        CountDownLatch readyWorkers = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        DataRetentionDynamicSwitchRedisStore firstStore = independentRedisStore();
        DataRetentionDynamicSwitchRedisStore secondStore = independentRedisStore();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<ChangeRow> firstWork = () -> {
                readyWorkers.countDown();
                release.await(10, TimeUnit.SECONDS);
                return disableService(firstStore, Clock.systemUTC()).resume(row);
            };
            java.util.concurrent.Callable<ChangeRow> secondWork = () -> {
                readyWorkers.countDown();
                release.await(10, TimeUnit.SECONDS);
                return disableService(secondStore, Clock.systemUTC()).resume(row);
            };
            Future<ChangeRow> first = executor.submit(firstWork);
            Future<ChangeRow> second = executor.submit(secondWork);
            assertTrue(readyWorkers.await(10, TimeUnit.SECONDS));
            release.countDown();
            assertEquals(ChangeStatus.APPLIED, first.get(15, TimeUnit.SECONDS).status());
            assertEquals(ChangeStatus.APPLIED, second.get(15, TimeUnit.SECONDS).status());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM data_retention_dynamic_switch_change", Integer.class));
    }

    @Test
    void twoReconcilersReachOneTerminalDisableConclusion() throws Exception {
        ChangeRow row = repository.createRequested(command(ID, false, 11L), null);
        CountDownLatch readyWorkers = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        DataRetentionDynamicSwitchRedisStore firstStore = independentRedisStore();
        DataRetentionDynamicSwitchRedisStore secondStore = independentRedisStore();
        DataRetentionDynamicSwitchReconciler firstReconciler = reconciler(firstStore);
        DataRetentionDynamicSwitchReconciler secondReconciler = reconciler(secondStore);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                readyWorkers.countDown();
                await(release);
                firstReconciler.reconcileRequested(row, 9001L);
            });
            Future<?> second = executor.submit(() -> {
                readyWorkers.countDown();
                await(release);
                secondReconciler.reconcileRequested(row, 9001L);
            });
            assertTrue(readyWorkers.await(10, TimeUnit.SECONDS));
            release.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(ChangeStatus.APPLIED, repository.findByChangeId(ID).orElseThrow().status());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_retention_dynamic_switch_change WHERE change_id=?",
                Integer.class, ID));
    }

    @Test
    void staleDisableAtomicallyCreatesAndAppliesHigherCompensation() {
        ChangeRow stale = repository.createRequested(command(ID, false, 11L), null);
        Payload higherTrue = new Payload(UUID.randomUUID().toString(), stale.version() + 5, true, redisStore.redisTime());
        writePayload(stale.switchKey(), higherTrue);
        ChangeRow compensated = disableService(redisStore, Clock.systemUTC()).resume(stale);
        assertEquals(ChangeStatus.FAILED, repository.findByChangeId(ID).orElseThrow().status());
        assertEquals(ChangeStatus.APPLIED, compensated.status());
        assertTrue(compensated.version() > higherTrue.version());
        assertFalse(compensated.enabled());
        assertEquals("0", redisTemplate.opsForHash().get(stale.switchKey().redisKey(), "enabled"));
    }

    @Test
    void compensationConflictRollsBackStaleFailure() {
        ChangeRow stale = repository.createRequested(command(ID, false, 11L), null);
        Payload higherTrue = new Payload(UUID.randomUUID().toString(), 7, true, redisStore.redisTime());
        String compensationId = UUID.nameUUIDFromBytes(("retention-switch-v1:stale:"
                + stale.switchKey().databaseValue() + ":" + stale.version() + ":" + higherTrue.version())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        jdbc.update("""
                INSERT INTO data_retention_dynamic_switch_change (
                    change_id, switch_key, version, enabled, status, changed_at,
                    requested_by, request_source, reason, ticket_ref, failed_at, last_error
                ) VALUES (?, ?, 2, 0, 'failed', ?, 9001, 'reconcile',
                          'different command', 'DIFFERENT', ?, 'collision fixture')
                """, compensationId, stale.switchKey().databaseValue(),
                java.sql.Timestamp.from(redisStore.redisTime()), java.sql.Timestamp.from(redisStore.redisTime()));
        writePayload(stale.switchKey(), higherTrue);
        assertThrows(RuntimeException.class, () -> disableService(redisStore, Clock.systemUTC()).resume(stale));
        assertEquals(ChangeStatus.REQUESTED, repository.findByChangeId(ID).orElseThrow().status());
    }

    @Test
    void inactiveInternalRoleCannotAuthorizeReconcileOperator() {
        jdbc.update("UPDATE sys_role SET status='inactive' WHERE role_key='manager'");
        assertThrows(RuntimeException.class, () -> authorization.validateReconcileOperator(9001L));
        jdbc.update("UPDATE sys_role SET status='active' WHERE role_key='manager'");
        authorization.validateReconcileOperator(9001L);
    }

    @Test
    void externalRoleTypeCannotAuthorizeReconcileOperator() {
        jdbc.update("UPDATE sys_role SET role_type='partner' WHERE role_key='manager'");
        assertThrows(RuntimeException.class, () -> authorization.validateReconcileOperator(9001L));
        jdbc.update("UPDATE sys_role SET role_type='internal' WHERE role_key='manager'");
        authorization.validateReconcileOperator(9001L);
    }

    @Test
    void injectedJvmClockControlsDisableAuditWithoutAffectingRedisTime() {
        Instant farFuture = Instant.parse("2030-01-02T03:04:05.678Z");
        ChangeRow row = repository.createRequested(new CreateCommand(ID, SwitchKey.POLL_RESULTS_EXECUTE,
                false, farFuture, 11L, RequestSource.MANUAL, "clock offset", null, null), null);
        ChangeRow applied = disableService(redisStore, Clock.fixed(farFuture, ZoneOffset.UTC)).resume(row);
        assertEquals(farFuture, row.changedAt());
        assertEquals(ChangeStatus.APPLIED, applied.status());
    }

    @Test
    void manualReconcileKeepsExpiredApprovedEnableRequestedWhenRedisIsMalformed() {
        ChangeRow approved = oldApprovedEnable();
        redisTemplate.opsForHash().put(approved.switchKey().redisKey(), "version", "1");

        var result = managementService(redisStore, mockAuthorization(9001L), Clock.systemUTC())
                .reconcileRequested(approved, 9001L);

        ChangeRow persisted = repository.findByChangeId(approved.changeId()).orElseThrow();
        assertEquals(ChangeStatus.REQUESTED, result.status());
        assertEquals(ChangeStatus.REQUESTED, persisted.status());
        assertEquals("malformed_current", persisted.lastError());
    }

    @Test
    void manualReconcileKeepsExpiredApprovedEnableRequestedWhenRedisIsUnavailable() throws Exception {
        ChangeRow approved = oldApprovedEnable();
        DataRetentionDynamicSwitchManagementService.ChangeView result;
        setRedisScriptsAllowed(false);
        try {
            result = managementService(redisStore, mockAuthorization(9001L), Clock.systemUTC())
                    .reconcileRequested(approved, 9001L);
        } finally {
            setRedisScriptsAllowed(true);
        }

        ChangeRow persisted = repository.findByChangeId(approved.changeId()).orElseThrow();
        assertEquals(ChangeStatus.REQUESTED, result.status());
        assertEquals(ChangeStatus.REQUESTED, persisted.status());
        assertEquals("redis_unavailable", persisted.lastError());
    }

    @Test
    void staticEnableWriterRejectsTrueButStillAllowsDisable() {
        DataRetentionDynamicSwitchAuthorizationPolicy current = mockAuthorization(11L);
        var service = managementService(redisStore, current, Clock.systemUTC());
        assertThrows(DynamicSwitchException.class, () -> service.createManual(
                SwitchKey.POLL_RESULTS_EXECUTE,
                new DataRetentionDynamicSwitchManagementService.ManualChangeCommand(
                        ID, true, "enable remains frozen", "CAB-2026-0812", "OPS-1")));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_retention_dynamic_switch_change", Integer.class));

        String disableId = UUID.randomUUID().toString();
        var disabled = service.createManual(SwitchKey.POLL_RESULTS_EXECUTE,
                new DataRetentionDynamicSwitchManagementService.ManualChangeCommand(
                        disableId, false, "safe disable", null, "OPS-1"));
        assertEquals(ChangeStatus.APPLIED, disabled.status());
        assertEquals("0", redisTemplate.opsForHash().get(
                SwitchKey.POLL_RESULTS_EXECUTE.redisKey(), "enabled"));
    }

    @Test
    void realRedisScriptFailureBlocksEnableCreationButPersistsDisableIntent() throws Exception {
        properties.getDynamicSwitch().setEnableWriteEnabled(true);
        setRedisScriptsAllowed(false);
        try {
            var service = managementService(redisStore, mockAuthorization(11L), Clock.systemUTC());
            assertThrows(DynamicSwitchException.class, () -> service.createManual(
                    SwitchKey.POLL_RESULTS_EXECUTE,
                    new DataRetentionDynamicSwitchManagementService.ManualChangeCommand(
                            ID, true, "time must come from Redis", "CAB-2026-0812", null)));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM data_retention_dynamic_switch_change", Integer.class));

            assertThrows(DynamicSwitchException.class, () -> service.createManual(
                    SwitchKey.POLL_RESULTS_EXECUTE,
                    new DataRetentionDynamicSwitchManagementService.ManualChangeCommand(
                            UUID.randomUUID().toString(), false, "persist safe intent", null, null)));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM data_retention_dynamic_switch_change WHERE status='requested' AND enabled=0",
                    Integer.class));
        } finally {
            setRedisScriptsAllowed(true);
        }
    }

    @Test
    void realRedisTimeFailureLeavesApprovalFactsEmpty() throws Exception {
        properties.getDynamicSwitch().setEnableWriteEnabled(true);
        DataRetentionDynamicSwitchAuthorizationPolicy current = mock(DataRetentionDynamicSwitchAuthorizationPolicy.class);
        when(current.requireCurrent(anyString())).thenReturn(user(11L), user(12L));
        var service = managementService(redisStore, current, Clock.systemUTC());
        service.createManual(SwitchKey.POLL_RESULTS_EXECUTE,
                new DataRetentionDynamicSwitchManagementService.ManualChangeCommand(
                        ID, true, "approval time is Redis time", "CAB-2026-0812", null));

        setRedisScriptsAllowed(false);
        try {
            var unavailableService = managementService(redisStore, current, Clock.systemUTC());
            assertThrows(DynamicSwitchException.class,
                    () -> unavailableService.approve(ID, "CAB-2026-0812"));
        } finally {
            setRedisScriptsAllowed(true);
        }
        ChangeRow persisted = repository.findByChangeId(ID).orElseThrow();
        assertEquals(ChangeStatus.REQUESTED, persisted.status());
        assertEquals(null, persisted.approvedBy());
        assertEquals(null, persisted.approvedAt());
    }

    @Test
    void unapprovedExactTrueIsFailedAndCompensatedToFalse() {
        Instant changedAt = redisStore.redisTime();
        ChangeRow unapproved = repository.createRequested(new CreateCommand(
                ID, SwitchKey.POLL_RESULTS_EXECUTE, true, changedAt, 11L,
                RequestSource.MANUAL, "unapproved exact true", "CAB-2026-0812", null), null);
        writePayload(unapproved.switchKey(), unapproved.payload());

        var result = managementService(redisStore, mockAuthorization(9001L), Clock.systemUTC())
                .reconcileRequested(unapproved, 9001L);

        assertEquals(ChangeStatus.FAILED, repository.findByChangeId(ID).orElseThrow().status());
        assertEquals(ChangeStatus.APPLIED, result.status());
        assertFalse(result.targetEnabled());
        assertEquals("0", redisTemplate.opsForHash().get(unapproved.switchKey().redisKey(), "enabled"));
    }

    @Test
    void appliedTrueKeyLossConvergesToNewerFalse() {
        ChangeRow appliedTrue = createAppliedEnable();
        redisTemplate.delete(appliedTrue.switchKey().redisKey());

        reconciler().reconcileKey(appliedTrue.switchKey(), 9001L);

        ChangeRow latest = repository.findLatest(appliedTrue.switchKey()).orElseThrow();
        assertTrue(latest.version() > appliedTrue.version());
        assertFalse(latest.enabled());
        assertEquals(ChangeStatus.APPLIED, latest.status());
        assertEquals("0", redisTemplate.opsForHash().get(appliedTrue.switchKey().redisKey(), "enabled"));
    }

    @Test
    void historicalAppliedTrueRollbackCreatesHigherFalseCompensation() {
        ChangeRow appliedTrue = createAppliedEnable();
        ChangeRow appliedFalse = disableService(redisStore, Clock.systemUTC()).resume(
                repository.createRequested(command(UUID.randomUUID().toString(), false, 13L), appliedTrue.version()));
        assertTrue(appliedFalse.version() > appliedTrue.version());
        writePayload(appliedTrue.switchKey(), appliedTrue.payload());

        reconciler().reconcileKey(appliedTrue.switchKey(), 9001L);

        ChangeRow latest = repository.findLatest(appliedTrue.switchKey()).orElseThrow();
        assertTrue(latest.version() > appliedFalse.version());
        assertFalse(latest.enabled());
        assertEquals(ChangeStatus.APPLIED, latest.status());
        assertEquals("0", redisTemplate.opsForHash().get(appliedTrue.switchKey().redisKey(), "enabled"));
    }

    @Test
    void compensationInsertFailureRollsBackStaleFailure() {
        ChangeRow stale = repository.createRequested(command(ID, false, 11L), null);
        Payload higherTrue = new Payload(UUID.randomUUID().toString(), 7, true, redisStore.redisTime());
        writePayload(stale.switchKey(), higherTrue);
        jdbc.execute("CREATE TRIGGER wp4_fail_compensation BEFORE INSERT ON "
                + "data_retention_dynamic_switch_change FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected compensation failure'");
        try {
            assertThrows(RuntimeException.class,
                    () -> disableService(redisStore, Clock.systemUTC()).resume(stale));
        } finally {
            jdbc.execute("DROP TRIGGER wp4_fail_compensation");
        }
        ChangeRow persisted = repository.findByChangeId(ID).orElseThrow();
        assertEquals(ChangeStatus.REQUESTED, persisted.status());
        assertEquals(null, persisted.failedAt());
    }

    private CreateCommand command(String changeId, boolean enabled, long userId) {
        return new CreateCommand(changeId, SwitchKey.POLL_RESULTS_EXECUTE, enabled, redisStore.redisTime(), userId,
                RequestSource.MANUAL, "WP4 integration test", enabled ? "CAB-2026-0812" : null, "OPS-1");
    }

    private void installRbacFixture() {
        // The authorization fixture is installed once in @BeforeAll. V359 must accept its exact reconcile seed.
    }

    private void installAuthorizationFixture() {
        jdbc.execute("CREATE TABLE sys_role (id BIGINT PRIMARY KEY AUTO_INCREMENT, role_key VARCHAR(64) UNIQUE, role_name VARCHAR(128), role_type VARCHAR(16), status VARCHAR(16))");
        jdbc.execute("CREATE TABLE sys_permission (id BIGINT PRIMARY KEY AUTO_INCREMENT, perm_key VARCHAR(64) UNIQUE, perm_name VARCHAR(128), module VARCHAR(64), action VARCHAR(32), status VARCHAR(16))");
        jdbc.execute("CREATE TABLE sys_role_permission (id BIGINT PRIMARY KEY AUTO_INCREMENT, role_id BIGINT, permission_id BIGINT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_rp(role_id,permission_id))");
        jdbc.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, role VARCHAR(64), is_active TINYINT(1))");
        jdbc.execute("CREATE TABLE sys_user_role (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, role_id BIGINT, UNIQUE KEY uk_ur(user_id,role_id))");
        jdbc.update("INSERT INTO sys_role(role_key,role_name,role_type,status) VALUES "
                + "('super_admin','Super Admin','internal','active'),"
                + "('manager','Manager','internal','active'),"
                + "('delivery_manager','Delivery Manager','internal','active')");
        jdbc.update("INSERT INTO sys_permission(perm_key,perm_name,module,action,status) VALUES "
                + "('data.retention.switch.reconcile','Data Retention Switch Reconcile','data_retention','switch_reconcile','active')");
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) "
                + "SELECT r.id,p.id FROM sys_role r JOIN sys_permission p "
                + "WHERE r.role_key='manager' AND p.perm_key='data.retention.switch.reconcile'");
        jdbc.update("INSERT INTO sys_user(id,role,is_active) VALUES (9001,'manager',1)");
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT 9001,id FROM sys_role WHERE role_key='manager'");
    }

    private DataRetentionDynamicSwitchDisablePort.SafeDisableService disableService(
            DataRetentionDynamicSwitchRedisStore store, Clock clock) {
        return new DataRetentionDynamicSwitchDisablePort.SafeDisableService(
                repository, store, authorization, alertSink, properties, metrics, clock);
    }

    private DataRetentionDynamicSwitchRedisStore independentRedisStore() {
        return new DataRetentionDynamicSwitchRedisStore(independentRedisTemplate(), metrics);
    }

    private DataRetentionDynamicSwitchRedisStore faultInjectingStore(FaultMode mode) {
        return new DataRetentionDynamicSwitchRedisStore(
                new FaultInjectingRedisTemplate(independentRedisConnectionFactory(), mode), metrics);
    }

    private StringRedisTemplate independentRedisTemplate() {
        StringRedisTemplate template = new StringRedisTemplate(independentRedisConnectionFactory());
        template.afterPropertiesSet();
        return template;
    }

    private LettuceConnectionFactory independentRedisConnectionFactory() {
        if (externalInfrastructure) {
            LettuceConnectionFactory factory = RetentionExternalTestServices.redisConnectionFactory();
            extraRedisConnectionFactories.add(factory);
            return factory;
        }
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getMappedPort(6379));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        factory.start();
        extraRedisConnectionFactories.add(factory);
        return factory;
    }

    private DataRetentionDynamicSwitchManagementService managementService(
            DataRetentionDynamicSwitchRedisStore store,
            DataRetentionDynamicSwitchAuthorizationPolicy currentAuthorization,
            Clock clock) {
        return new DataRetentionDynamicSwitchManagementService(
                repository, store, disableService(store, clock), currentAuthorization,
                alertSink, properties, metrics, clock);
    }

    private DataRetentionDynamicSwitchReconciler reconciler() {
        return reconciler(redisStore);
    }

    private DataRetentionDynamicSwitchReconciler reconciler(DataRetentionDynamicSwitchRedisStore store) {
        return new DataRetentionDynamicSwitchReconciler(
                repository, store, disableService(store, Clock.systemUTC()), authorization,
                alertSink, properties, metrics);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("latch timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("latch interrupted", interrupted);
        }
    }

    private ChangeRow oldApprovedEnable() {
        Instant changedAt = redisStore.redisTime().minusSeconds(120);
        ChangeRow row = repository.createRequested(new CreateCommand(
                ID, SwitchKey.POLL_RESULTS_EXECUTE, true, changedAt, 11L,
                RequestSource.MANUAL, "expired grace fixture", "CAB-2026-0812", null), null);
        return repository.approve(ID, 12L, "CAB-2026-0812", changedAt.plusSeconds(1));
    }

    private ChangeRow createAppliedEnable() {
        Instant changedAt = redisStore.redisTime();
        ChangeRow row = repository.createRequested(new CreateCommand(
                ID, SwitchKey.POLL_RESULTS_EXECUTE, true, changedAt, 11L,
                RequestSource.MANUAL, "approved enable fixture", "CAB-2026-0812", null), null);
        ChangeRow approved = repository.approve(ID, 12L, "CAB-2026-0812", changedAt.plusMillis(1));
        writePayload(approved.switchKey(), approved.payload());
        return repository.finalizeApplied(approved, approved.payload(), changedAt.plusMillis(2));
    }

    private DataRetentionDynamicSwitchAuthorizationPolicy mockAuthorization(long userId) {
        DataRetentionDynamicSwitchAuthorizationPolicy policy = mock(DataRetentionDynamicSwitchAuthorizationPolicy.class);
        when(policy.requireCurrent(anyString())).thenReturn(user(userId));
        return policy;
    }

    private static SysUser user(long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRole("manager");
        user.setIsActive(true);
        return user;
    }

    private void setRedisScriptsAllowed(boolean allowed) throws Exception {
        if (externalInfrastructure) {
            redisStore = allowed
                    ? new DataRetentionDynamicSwitchRedisStore(redisTemplate, metrics)
                    : faultInjectingStore(FaultMode.BEFORE_COMMAND);
            return;
        }
        String modifier = allowed ? "+eval" : "-eval";
        String shaModifier = allowed ? "+evalsha" : "-evalsha";
        var result = redis.execInContainer("redis-cli", "ACL", "SETUSER", "default", modifier, shaModifier);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Could not update Redis ACL: " + result.getStderr());
        }
    }

    private void installSwitchTableFixture() {
        jdbc.execute("""
                CREATE TABLE data_retention_dynamic_switch_change (
                    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                    change_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    switch_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    version BIGINT UNSIGNED NOT NULL,
                    enabled TINYINT(1) NOT NULL,
                    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    changed_at DATETIME(3) NOT NULL,
                    requested_by BIGINT NOT NULL,
                    request_source VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    reason VARCHAR(500) NOT NULL,
                    approval_ref VARCHAR(128) NULL,
                    approved_by BIGINT NULL,
                    approved_at DATETIME(3) NULL,
                    ticket_ref VARCHAR(128) NULL,
                    redis_observed_version BIGINT UNSIGNED NULL,
                    redis_observed_enabled TINYINT(1) NULL,
                    last_error VARCHAR(2000) NULL,
                    applied_at DATETIME(3) NULL,
                    failed_at DATETIME(3) NULL,
                    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    PRIMARY KEY(id), UNIQUE KEY uk_change(change_id), UNIQUE KEY uk_switch_version(switch_key,version),
                    CHECK (enabled IN (0,1)), CHECK (redis_observed_enabled IS NULL OR redis_observed_enabled IN (0,1)),
                    CHECK (last_error IS NULL OR OCTET_LENGTH(last_error) <= 2048)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void writePayload(SwitchKey key, Payload payload) {
        redisTemplate.opsForHash().putAll(key.redisKey(), Map.of(
                "change_id", payload.changeId(), "version", payload.versionText(),
                "enabled", payload.enabledText(), "changed_at", payload.changedAtText()));
    }

    private DataSource requireDataSource() {
        return dataSource;
    }

    private static boolean dockerAvailable() {
        try { return DockerClientFactory.instance().isDockerAvailable(); }
        catch (RuntimeException error) { return false; }
    }

    private enum FaultMode { BEFORE_COMMAND, AFTER_COMMAND }

    private static final class FaultInjectingRedisTemplate extends StringRedisTemplate {
        private final FaultMode mode;

        private FaultInjectingRedisTemplate(RedisConnectionFactory connectionFactory, FaultMode mode) {
            super(connectionFactory);
            this.mode = mode;
            afterPropertiesSet();
        }

        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            if (mode == FaultMode.BEFORE_COMMAND) throw new RuntimeException("injected timeout before command");
            T response = super.execute(script, keys, args);
            throw new RuntimeException("injected timeout after command; response discarded: " + response);
        }
    }
}
