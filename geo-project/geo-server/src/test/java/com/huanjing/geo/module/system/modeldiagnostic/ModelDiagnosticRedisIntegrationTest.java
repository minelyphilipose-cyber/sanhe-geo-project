package com.huanjing.geo.module.system.modeldiagnostic;

import com.huanjing.geo.module.system.modeldiagnostic.concurrency.ModelDiagnosticPermitStore;
import com.huanjing.geo.module.system.modeldiagnostic.cleanup.ModelDiagnosticCleanupLockStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelDiagnosticRedisIntegrationTest {

    private static final String REQUIRED_GATE_PROPERTY = "model.diagnostic.redis-it.required";
    private static final String UNAVAILABLE_REASON =
            "Docker is unavailable and MODEL_DIAGNOSTIC_REDIS_IT_HOST is not configured";

    private GenericContainer<?> container;
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private ModelDiagnosticPermitStore store;
    private ModelDiagnosticCleanupLockStore cleanupLockStore;
    private boolean redisReady;
    private String hashTag;
    private final List<String> keys = new ArrayList<>();

    @BeforeAll
    void connectToRedis() {
        String host = System.getenv("MODEL_DIAGNOSTIC_REDIS_IT_HOST");
        int port;
        String password = System.getenv("MODEL_DIAGNOSTIC_REDIS_IT_PASSWORD");
        if (host != null && !host.isBlank()) {
            port = Integer.parseInt(System.getenv().getOrDefault(
                    "MODEL_DIAGNOSTIC_REDIS_IT_PORT", "6379"));
        } else if (dockerAvailable()) {
            container = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);
            container.start();
            host = container.getHost();
            port = container.getMappedPort(6379);
        } else {
            requireOrSkip();
            return;
        }

        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().ping();
        store = new ModelDiagnosticPermitStore(redisTemplate);
        cleanupLockStore = new ModelDiagnosticCleanupLockStore(redisTemplate);
        hashTag = "{model-diagnostic-it-" + UUID.randomUUID() + "}";
        redisReady = true;
    }

    @BeforeEach
    void cleanKeys() {
        Assumptions.assumeTrue(redisReady, UNAVAILABLE_REASON);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
            keys.clear();
        }
    }

    @AfterAll
    void closeRedis() {
        if (redisTemplate != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void luaAtomicallyEnforcesGlobalTwoAndOperatorOne() {
        String global = key("global");
        String operatorOne = key("operator:1");
        String operatorTwo = key("operator:2");
        String operatorThree = key("operator:3");
        long now = System.currentTimeMillis();
        long leaseUntil = now + 60_000L;

        assertTrue(store.acquire(global, operatorOne, "owner-a", now, leaseUntil, 30_000L));
        assertFalse(store.acquire(global, operatorOne, "owner-b", now, leaseUntil, 30_000L));
        assertTrue(store.acquire(global, operatorTwo, "owner-b", now, leaseUntil, 30_000L));
        assertFalse(store.acquire(global, operatorThree, "owner-c", now, leaseUntil, 30_000L));
        assertTrue(store.release(global, operatorOne, "owner-a", now, 30_000L));
        assertTrue(store.acquire(global, operatorThree, "owner-c", now, leaseUntil, 30_000L));
    }

    @Test
    void releaseRequiresTheOwnerToken() {
        String global = key("release-global");
        String operator = key("release-operator:1");
        long now = System.currentTimeMillis();

        assertTrue(store.acquire(global, operator, "owner", now, now + 60_000L, 30_000L));
        assertFalse(store.release(global, operator, "other-owner", now, 30_000L));
        assertTrue(store.release(global, operator, "owner", now, 30_000L));
    }

    @Test
    void expiredLeaseIsReclaimedWithoutProcessRelease() throws Exception {
        String global = key("expiry-global");
        String operator = key("expiry-operator:1");
        long now = System.currentTimeMillis();

        assertTrue(store.acquire(global, operator, "stale", now, now + 1_000L, 1_000L));
        Thread.sleep(1_100L);
        long afterExpiry = System.currentTimeMillis();
        assertTrue(store.acquire(
                global, operator, "replacement", afterExpiry, afterExpiry + 1_000L, 1_000L));
    }

    @Test
    void shorterSecondLeaseDoesNotShortenGlobalKeyLifetime() {
        String global = key("ttl-global");
        String longOperator = key("ttl-operator:long");
        String shortOperator = key("ttl-operator:short");
        long now = System.currentTimeMillis();

        assertTrue(store.acquire(
                global, longOperator, "long-owner", now, now + 10_000L, 1_000L));
        assertTrue(store.acquire(
                global, shortOperator, "short-owner", now, now + 1_000L, 1_000L));

        Long ttl = redisTemplate.getExpire(global, TimeUnit.MILLISECONDS);
        assertTrue(ttl != null && ttl > 8_000L);
    }

    @Test
    void luaRejectsAlreadyExpiredLease() {
        String global = key("expired-request-global");
        String operator = key("expired-request-operator");
        long now = System.currentTimeMillis();

        assertFalse(store.acquire(global, operator, "expired", now, now, 1_000L));
        assertEquals(0L, redisTemplate.opsForZSet().size(global));
        assertEquals(0L, redisTemplate.opsForZSet().size(operator));
    }

    @Test
    void cleanupLockUsesNxTtlAndOnlyItsOwnerCanRelease() {
        String lockKey = key("cleanup-lock");

        assertTrue(cleanupLockStore.tryAcquire(
                lockKey, "owner-a", java.time.Duration.ofSeconds(30)));
        assertFalse(cleanupLockStore.tryAcquire(
                lockKey, "owner-b", java.time.Duration.ofSeconds(30)));
        assertFalse(cleanupLockStore.release(lockKey, "owner-b"));
        assertEquals("owner-a", redisTemplate.opsForValue().get(lockKey));
        assertTrue(cleanupLockStore.release(lockKey, "owner-a"));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey)));
    }

    private String key(String suffix) {
        String key = "geo:test:" + hashTag + ":" + suffix;
        keys.add(key);
        return key;
    }

    private boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void requireOrSkip() {
        if (Boolean.getBoolean(REQUIRED_GATE_PROPERTY)) {
            throw new IllegalStateException(UNAVAILABLE_REASON);
        }
        redisReady = false;
    }
}
