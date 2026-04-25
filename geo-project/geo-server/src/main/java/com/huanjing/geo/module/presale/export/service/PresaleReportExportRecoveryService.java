package com.huanjing.geo.module.presale.export.service;

import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import com.huanjing.geo.module.presale.export.persist.entity.PresaleReportExport;
import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresaleReportExportRecoveryService {
    private final PresaleReportExportMapper exportMapper;
    private final PresaleExportWorkerIdentity workerIdentity;
    private final PresaleExportProperties properties;
    private final PresaleExportMetricsJsonHelper metricsJsonHelper;

    @EventListener(ApplicationReadyEvent.class)
    public void markOwnRunningInterrupted() {
        String workerId = workerIdentity.workerId();
        List<PresaleReportExport> running = exportMapper.selectRunningByWorker(workerId);
        if (running.isEmpty()) {
            return;
        }
        int updated = 0;
        for (PresaleReportExport task : running) {
            String metricsJson = metricsJsonHelper.appendRetryHistory(task.getMetricsJson(),
                    PresaleExportMetricsJsonHelper.RetryHistoryEntry.builder()
                            .errorCode("INTERRUPTED_BY_RESTART")
                            .errorMsg("Application restarted while rendering")
                            .retryCount(task.getRetryCount())
                            .build());
            updated += exportMapper.markInterruptedByRestartById(task.getId(), workerId, metricsJson);
        }
        log.info("Presale export restart recovery marked {} RUNNING tasks as FAILED, workerId={}, exportIds={}",
                updated, workerId, running.stream().map(PresaleReportExport::getId).toList());
    }

    @Scheduled(fixedDelayString = "${geo.presale-export.worker.stale-scan-interval-ms:60000}")
    public void markStaleRunningFailed() {
        if (!properties.getWorker().isEnabled()) {
            return;
        }
        LocalDateTime deadline = LocalDateTime.now()
                .minus(Duration.ofMillis(properties.getWorker().getStaleRunningTimeoutMs()));
        List<PresaleReportExport> stale = exportMapper.selectStaleRunning(deadline);
        for (PresaleReportExport task : stale) {
            String metricsJson = metricsJsonHelper.appendRetryHistory(task.getMetricsJson(),
                    PresaleExportMetricsJsonHelper.RetryHistoryEntry.builder()
                            .errorCode("WORKER_HEARTBEAT_TIMEOUT")
                            .errorMsg("Worker heartbeat timed out")
                            .retryCount(task.getRetryCount())
                            .build());
            int updated = exportMapper.markStaleFailed(task.getId(), metricsJson);
            if (updated > 0) {
                log.warn("Stale RUNNING force-failed: exportId={}, last_updated_at={}, worker_id={}",
                        task.getId(), task.getUpdatedAt(), task.getWorkerId());
            }
        }
    }
}
