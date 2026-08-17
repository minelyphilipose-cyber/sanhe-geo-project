package com.huanjing.geo.module.content.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.mapper.AccountAuthRiskScanBatchMapper;
import com.huanjing.geo.module.extension.service.ExtensionRedisStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

class AccountAuthHealthScanCoordinatorTest {
    private SelfMediaAccountHealthAlertService selfMediaService;
    private ForumCookieHealthAlertService forumService;
    private AccountAuthRiskScanBatchMapper batchMapper;
    private ExtensionRedisStore redisStore;
    private TaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        selfMediaService = mock(SelfMediaAccountHealthAlertService.class);
        forumService = mock(ForumCookieHealthAlertService.class);
        batchMapper = mock(AccountAuthRiskScanBatchMapper.class);
        redisStore = mock(ExtensionRedisStore.class);
        scheduler = mock(TaskScheduler.class);
        when(batchMapper.selectList(any())).thenReturn(List.of());
        when(redisStore.tryLock(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisStore.renewLock(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenReturn(mock(ScheduledFuture.class));
    }

    @Test
    void submissionFailureReleasesLeaseImmediately() {
        Executor rejectingExecutor = command -> { throw new RejectedExecutionException("full"); };
        AccountAuthHealthScanCoordinator coordinator = coordinator(rejectingExecutor);

        BizException error = assertThrows(BizException.class, coordinator::launch);

        assertEquals(503, error.getCode());
        verify(redisStore).releaseLock(anyString(), anyString());
    }

    @Test
    void acceptedScanRenewsAndReleasesLease() {
        AccountAuthHealthScanCoordinator coordinator = coordinator(Runnable::run);

        assertEquals("accepted", coordinator.launch().status());

        verify(redisStore, atLeastOnce()).renewLock(anyString(), anyString(), any(Duration.class));
        verify(selfMediaService).scanOnce();
        verify(forumService).scanOnce();
        verify(redisStore).releaseLock(anyString(), anyString());
    }

    private AccountAuthHealthScanCoordinator coordinator(Executor executor) {
        AccountAuthHealthScanCoordinator coordinator = new AccountAuthHealthScanCoordinator(
                selfMediaService, forumService, batchMapper, redisStore, executor, scheduler);
        ReflectionTestUtils.setField(coordinator, "scanLockMinutes", 30);
        ReflectionTestUtils.setField(coordinator, "staleRunningMinutes", 60);
        return coordinator;
    }
}
