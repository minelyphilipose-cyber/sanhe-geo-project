package com.huanjing.geo.module.dashboard.service;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectDashboardRefreshLockTest {

    @Test
    @SuppressWarnings("unchecked")
    void leaseRenewsAndReleasesOnlyWithOwnerValue() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<Object> renewalTask = mock(ScheduledFuture.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("geo:dashboard:snapshot:refresh:8"), anyString(), eq(Duration.ofMinutes(2))))
                .thenReturn(true);
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(30))))
                .thenAnswer(ignored -> renewalTask);
        when(redis.execute(any(DefaultRedisScript.class), eq(List.of("geo:dashboard:snapshot:refresh:8")),
                anyString(), eq("120000"))).thenReturn(1L);
        when(redis.execute(any(DefaultRedisScript.class), eq(List.of("geo:dashboard:snapshot:refresh:8")),
                anyString())).thenReturn(1L);

        ProjectDashboardRefreshLock lock = new ProjectDashboardRefreshLock(redis, scheduler);
        ProjectDashboardRefreshLock.Lease lease = lock.tryAcquire(8L);

        assertNotNull(lease);
        lease.ensureHeld();
        lease.close();

        verify(renewalTask).cancel(false);
        verify(redis).execute(any(DefaultRedisScript.class), eq(List.of("geo:dashboard:snapshot:refresh:8")),
                anyString(), eq("120000"));
        verify(redis).execute(any(DefaultRedisScript.class), eq(List.of("geo:dashboard:snapshot:refresh:8")),
                anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void occupiedLockReturnsNullAndPreservesStartedAt() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("geo:dashboard:snapshot:refresh:8"), anyString(), eq(Duration.ofMinutes(2))))
                .thenReturn(false);
        when(values.get("geo:dashboard:snapshot:refresh:8"))
                .thenReturn("owner-token|2026-07-18T15:05:00");

        ProjectDashboardRefreshLock lock = new ProjectDashboardRefreshLock(redis, scheduler);

        assertNull(lock.tryAcquire(8L));
        assertTrue(lock.getStartedAt(8L).startsWith("2026-07-18T15:05:00"));
    }

    @Test
    void scriptsCheckOwnerBeforeRenewOrDelete() {
        assertTrue(ProjectDashboardRefreshLock.renewScript().contains("GET', KEYS[1]) == ARGV[1]"));
        assertTrue(ProjectDashboardRefreshLock.renewScript().contains("PEXPIRE"));
        assertTrue(ProjectDashboardRefreshLock.releaseScript().contains("GET', KEYS[1]) == ARGV[1]"));
        assertTrue(ProjectDashboardRefreshLock.releaseScript().contains("DEL"));
    }
}
