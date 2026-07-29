package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.dashboard.service.ProjectDashboardSnapshotService;
import com.huanjing.geo.module.dispatch.enums.DispatchAlertSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchScheduler {

    private final DispatchQueueService dispatchQueueService;
    private final DispatchPlannerService dispatchPlannerService;
    private final DispatchTaskService dispatchTaskService;
    private final DispatchAlertService dispatchAlertService;
    private final ProjectDashboardSnapshotService projectDashboardSnapshotService;
    private final DispatchPollAggregationService dispatchPollAggregationService;

    @Scheduled(cron = "${geo.dispatch.cron:0 5 0 * * *}", zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void dailyScan() {
        String lockValue = UUID.randomUUID().toString();
        try {
            boolean locked = dispatchQueueService.tryAcquireScanLock(lockValue);
            if (!locked) {
                log.info("Skip daily dispatch scan because lock is occupied");
                return;
            }
            dispatchPlannerService.scanAndPlan(LocalDate.now());
            dispatchTaskService.enqueueRecoveryTasks();
            if (dispatchTaskService.isHistoryCleanupEnabled()) {
                cleanupHistory();
            }
        } catch (Exception ex) {
            log.error("Dispatch daily scan failed", ex);
            dispatchAlertService.createAlert(
                    null,
                    null,
                    DispatchAlertSeverity.ERROR,
                    "Dispatch daily scan failed",
                    ex.getMessage(),
                    0,
                    null
            );
        } finally {
            dispatchQueueService.releaseScanLock(lockValue);
        }
    }

    private void cleanupHistory() {
        try {
            dispatchTaskService.cleanupHistory();
        } catch (Exception ex) {
            log.error("Dispatch task history cleanup failed", ex);
            dispatchAlertService.createOrRefreshAlert(
                    null,
                    null,
                    "dispatch_task_history_cleanup_failed",
                    DispatchAlertSeverity.ERROR,
                    "Dispatch task history cleanup failed",
                    summarizeError(ex),
                    0,
                    null
            );
        }
    }

    private String summarizeError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 1_800 ? message : message.substring(0, 1_800);
    }

    @Scheduled(fixedDelayString = "${geo.dispatch.retry-check-ms:30000}")
    public void retryRecoveryScan() {
        try {
            dispatchTaskService.enqueueRecoveryTasks();
            dispatchTaskService.reclaimTimedOutRunningTasks();
            dispatchPollAggregationService.recoverFinishedAggregations(100);
        } catch (Exception ex) {
            log.error("Retry recovery scan failed", ex);
        }
    }

    @Scheduled(cron = "${geo.dashboard.snapshot-cron:0 5 * * * *}", zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void refreshDashboardSnapshots() {
        try {
            projectDashboardSnapshotService.refreshAllActive();
        } catch (Exception ex) {
            log.error("Project dashboard snapshot refresh failed", ex);
        }
    }
}
