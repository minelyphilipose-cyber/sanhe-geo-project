package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.dashboard.service.ProjectDashboardSnapshotService;
import com.huanjing.geo.module.dispatch.enums.DispatchAlertSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchSchedulerTest {

    private DispatchQueueService dispatchQueueService;
    private DispatchPlannerService dispatchPlannerService;
    private DispatchTaskService dispatchTaskService;
    private DispatchAlertService dispatchAlertService;
    private DispatchScheduler scheduler;

    @BeforeEach
    void setUp() {
        dispatchQueueService = mock(DispatchQueueService.class);
        dispatchPlannerService = mock(DispatchPlannerService.class);
        dispatchTaskService = mock(DispatchTaskService.class);
        dispatchAlertService = mock(DispatchAlertService.class);
        scheduler = new DispatchScheduler(
                dispatchQueueService,
                dispatchPlannerService,
                dispatchTaskService,
                dispatchAlertService,
                mock(ProjectDashboardSnapshotService.class),
                mock(DispatchPollAggregationService.class)
        );
    }

    @Test
    void historyCleanupFailureDoesNotTurnSuccessfulPlanningIntoDailyScanFailure() {
        when(dispatchQueueService.tryAcquireScanLock(anyString())).thenReturn(true);
        when(dispatchTaskService.isHistoryCleanupEnabled()).thenReturn(true);
        when(dispatchTaskService.cleanupHistory()).thenThrow(
                new IllegalStateException("referenced task cannot be deleted"));

        scheduler.dailyScan();

        verify(dispatchPlannerService).scanAndPlan(eq(LocalDate.now()));
        verify(dispatchTaskService).enqueueRecoveryTasks();
        verify(dispatchAlertService).createOrRefreshAlert(
                eq(null),
                eq(null),
                eq("dispatch_task_history_cleanup_failed"),
                eq(DispatchAlertSeverity.ERROR),
                eq("Dispatch task history cleanup failed"),
                eq("referenced task cannot be deleted"),
                eq(0),
                eq(null)
        );
        verify(dispatchQueueService).releaseScanLock(anyString());
    }
}
