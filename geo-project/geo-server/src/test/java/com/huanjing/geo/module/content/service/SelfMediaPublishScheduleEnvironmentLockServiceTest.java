package com.huanjing.geo.module.content.service;

import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleEnvironmentLock;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleEnvironmentLockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfMediaPublishScheduleEnvironmentLockServiceTest {
    private SelfMediaPublishScheduleEnvironmentLockMapper mapper;
    private SelfMediaPublishScheduleEnvironmentLockService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SelfMediaPublishScheduleEnvironmentLockMapper.class);
        service = new SelfMediaPublishScheduleEnvironmentLockService(mapper);
    }

    @Test
    void tryAcquireReturnsTrueWhenCurrentScheduleOwnsLock() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime lockedUntil = now.plusMinutes(30);
        SelfMediaPublishScheduleEnvironmentLock row = new SelfMediaPublishScheduleEnvironmentLock();
        row.setBrowserEnvironmentId(15L);
        row.setScheduleId(90L);
        row.setLockedUntil(lockedUntil);
        when(mapper.selectByEnvironmentId(15L)).thenReturn(row);

        boolean acquired = service.tryAcquire(15L, 90L, lockedUntil, now);

        assertTrue(acquired);
        verify(mapper).upsertIfExpired(15L, 90L, lockedUntil, now);
    }

    @Test
    void tryAcquireReturnsFalseWhenAnotherScheduleOwnsLock() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 10, 0);
        LocalDateTime lockedUntil = now.plusMinutes(30);
        SelfMediaPublishScheduleEnvironmentLock row = new SelfMediaPublishScheduleEnvironmentLock();
        row.setBrowserEnvironmentId(15L);
        row.setScheduleId(91L);
        row.setLockedUntil(lockedUntil);
        when(mapper.selectByEnvironmentId(15L)).thenReturn(row);

        boolean acquired = service.tryAcquire(15L, 90L, lockedUntil, now);

        assertFalse(acquired);
        verify(mapper).upsertIfExpired(15L, 90L, lockedUntil, now);
    }

    @Test
    void tryAcquireReturnsFalseWhenInputMissing() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 10, 0);

        assertFalse(service.tryAcquire(null, 90L, now.plusMinutes(30), now));
        assertFalse(service.tryAcquire(15L, null, now.plusMinutes(30), now));
    }

    @Test
    void releaseDeletesByScheduleId() {
        service.release(90L);

        verify(mapper).deleteByScheduleId(90L);
    }
}
