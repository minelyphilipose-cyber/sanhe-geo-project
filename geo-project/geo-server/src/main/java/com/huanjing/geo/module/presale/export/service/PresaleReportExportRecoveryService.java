package com.huanjing.geo.module.presale.export.service;

import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import com.huanjing.geo.module.presale.export.persist.entity.PresaleReportExport;
import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
/*
 * Startup recovery is intentionally not implemented. worker_id contains a UUID and changes on every
 * process start, so a restarted process cannot reliably identify its own previous RUNNING tasks.
 * All orphaned RUNNING tasks are recovered by markStaleRunningFailed based on heartbeat timeout.
 */
public class PresaleReportExportRecoveryService {
    private final PresaleReportExportMapper exportMapper;
    private final PresaleExportProperties properties;
    private final PresaleExportMetricsJsonHelper metricsJsonHelper;

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
