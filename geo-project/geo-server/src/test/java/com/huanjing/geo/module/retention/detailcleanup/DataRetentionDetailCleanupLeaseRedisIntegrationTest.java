package com.huanjing.geo.module.retention.detailcleanup;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import com.huanjing.geo.module.retention.support.RetentionExternalTestServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataRetentionDetailCleanupLeaseRedisIntegrationTest {
    private static final String REQUIRED = "retention.detail-cleanup-it.required";
    private GenericContainer<?> container;
    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private ThreadPoolTaskScheduler scheduler;
    private DataRetentionDetailCleanupLease lease;
    private String key;
    private boolean ready;

    @BeforeAll void configure() {
        ready = configureRedis();
        if (!ready) return;
        redis = new StringRedisTemplate(connectionFactory);
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.initialize();
        DataRetentionProperties properties = new DataRetentionProperties();
        key = "geo:retention:detail-cleanup:it:" + UUID.randomUUID();
        properties.getDetailCleanup().setLockKey(key);
        properties.getDetailCleanup().setLockTtlSeconds(60);
        lease = new DataRetentionDetailCleanupLease(redis, properties, scheduler);
    }

    @AfterAll void release() {
        if (redis != null && key != null) redis.delete(key);
        if (scheduler != null) scheduler.shutdown();
        if (connectionFactory != null) connectionFactory.destroy();
        if (container != null) container.stop();
    }

    @Test void leaseIsExclusiveAndCompareDeleteReleasesIt() {
        Assumptions.assumeTrue(ready, "Redis is unavailable");
        DataRetentionDetailCleanupLease.AcquireResult first = lease.tryAcquire();
        assertThat(first.code()).isEqualTo(DataRetentionDetailCleanupLease.AcquireCode.ACQUIRED);
        assertThat(lease.tryAcquire().code()).isEqualTo(DataRetentionDetailCleanupLease.AcquireCode.BUSY);
        first.lease().ensureHeld();
        first.lease().close();
        DataRetentionDetailCleanupLease.AcquireResult second = lease.tryAcquire();
        assertThat(second.code()).isEqualTo(DataRetentionDetailCleanupLease.AcquireCode.ACQUIRED);
        second.lease().close();
        assertThat(redis.hasKey(key)).isFalse();
    }

    private boolean configureRedis() {
        if (RetentionExternalTestServices.redisConfigured()) {
            connectionFactory = RetentionExternalTestServices.redisConnectionFactory();
            return true;
        }
        boolean docker;
        try { docker = DockerClientFactory.instance().isDockerAvailable(); }
        catch (RuntimeException error) { docker = false; }
        if (!docker) {
            if (Boolean.getBoolean(REQUIRED)) throw new IllegalStateException("Required WP8 Redis is unavailable");
            return false;
        }
        container = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);
        container.start();
        connectionFactory = new LettuceConnectionFactory(container.getHost(), container.getMappedPort(6379));
        connectionFactory.afterPropertiesSet(); connectionFactory.start();
        return true;
    }
}
