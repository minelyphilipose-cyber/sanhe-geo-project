package com.huanjing.geo.module.retention.schedule;

import com.huanjing.geo.module.retention.config.DataRetentionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataRetentionSchedulerLockTest {

    @Test
    @SuppressWarnings("unchecked")
    void leaseRenewsBeforeEachBatchAndCancelsRenewalOnClose() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<Object> renewalTask = mock(ScheduledFuture.class);
        DataRetentionProperties properties = new DataRetentionProperties();
        properties.getScheduler().setLockKey("geo:retention:test");
        properties.getScheduler().setLockTtlSeconds(60L);

        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("geo:retention:test"), anyString(), eq(Duration.ofSeconds(60))))
                .thenReturn(true);
        when(scheduler.scheduleAtFixedRate(
                any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(20))))
                .thenAnswer(ignored -> renewalTask);
        when(redis.execute(any(DefaultRedisScript.class), eq(List.of("geo:retention:test")),
                anyString(), eq("60000"))).thenReturn(1L);
        when(redis.execute(any(DefaultRedisScript.class), eq(List.of("geo:retention:test")),
                anyString())).thenReturn(1L);

        DataRetentionSchedulerLock.Lease lease =
                new DataRetentionSchedulerLock(redis, properties, scheduler).tryAcquire();

        assertNotNull(lease);
        lease.ensureHeld();
        lease.close();

        verify(renewalTask).cancel(false);
        verify(redis).execute(any(DefaultRedisScript.class), eq(List.of("geo:retention:test")),
                anyString(), eq("60000"));
        verify(redis).execute(any(DefaultRedisScript.class), eq(List.of("geo:retention:test")),
                anyString());
    }

    @Test
    void scriptsCheckOwnerBeforeRenewOrRelease() {
        assertTrue(DataRetentionSchedulerLock.renewScript().contains("GET', KEYS[1]) == ARGV[1]"));
        assertTrue(DataRetentionSchedulerLock.renewScript().contains("PEXPIRE"));
        assertTrue(DataRetentionSchedulerLock.releaseScript().contains("GET', KEYS[1]) == ARGV[1]"));
        assertTrue(DataRetentionSchedulerLock.releaseScript().contains("DEL"));
    }
}
