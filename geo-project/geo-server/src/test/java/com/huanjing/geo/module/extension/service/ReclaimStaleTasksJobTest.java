package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.module.system.service.SystemAlertService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReclaimStaleTasksJobTest {

    @Test
    void thirdConsecutiveFailureCreatesSystemAlert() {
        ExtensionTaskStateService taskStateService = mock(ExtensionTaskStateService.class);
        ExtensionRedisStore redisStore = mock(ExtensionRedisStore.class);
        SystemAlertService systemAlertService = mock(SystemAlertService.class);
        ReclaimStaleTasksJob job = new ReclaimStaleTasksJob(taskStateService, redisStore, systemAlertService);
        when(redisStore.tryLock(eq("reclaim:stale_tasks:lock"), any(), any(Duration.class))).thenReturn(true);
        when(taskStateService.reclaimStaleTasks()).thenThrow(new IllegalStateException("db down"));

        job.reclaim();
        job.reclaim();
        job.reclaim();

        verify(systemAlertService).createAlert(
                eq("semi_auto_reclaim_failed"),
                eq("error"),
                eq("semi_auto_task"),
                eq("Reclaim stale semi-auto tasks failed"),
                any()
        );
        verify(redisStore, times(3)).releaseLock(eq("reclaim:stale_tasks:lock"), any());
    }

    @Test
    void failuresAfterThresholdDoNotCreateRepeatedAlerts() {
        ExtensionTaskStateService taskStateService = mock(ExtensionTaskStateService.class);
        ExtensionRedisStore redisStore = mock(ExtensionRedisStore.class);
        SystemAlertService systemAlertService = mock(SystemAlertService.class);
        ReclaimStaleTasksJob job = new ReclaimStaleTasksJob(taskStateService, redisStore, systemAlertService);
        when(redisStore.tryLock(eq("reclaim:stale_tasks:lock"), any(), any(Duration.class))).thenReturn(true);
        when(taskStateService.reclaimStaleTasks()).thenThrow(new IllegalStateException("db down"));

        job.reclaim();
        job.reclaim();
        job.reclaim();
        job.reclaim();
        job.reclaim();

        verify(systemAlertService).createAlert(
                eq("semi_auto_reclaim_failed"),
                eq("error"),
                eq("semi_auto_task"),
                eq("Reclaim stale semi-auto tasks failed"),
                any()
        );
    }

    @Test
    void lockMissSkipsReclaimWithoutCountingFailure() {
        ExtensionTaskStateService taskStateService = mock(ExtensionTaskStateService.class);
        ExtensionRedisStore redisStore = mock(ExtensionRedisStore.class);
        SystemAlertService systemAlertService = mock(SystemAlertService.class);
        ReclaimStaleTasksJob job = new ReclaimStaleTasksJob(taskStateService, redisStore, systemAlertService);
        when(redisStore.tryLock(eq("reclaim:stale_tasks:lock"), any(), any(Duration.class))).thenReturn(false);

        job.reclaim();
        job.reclaim();
        job.reclaim();

        verify(taskStateService, never()).reclaimStaleTasks();
        verify(systemAlertService, never()).createAlert(any(), any(), any(), any(), any());
        verify(redisStore, never()).releaseLock(any(), any());
    }
}
