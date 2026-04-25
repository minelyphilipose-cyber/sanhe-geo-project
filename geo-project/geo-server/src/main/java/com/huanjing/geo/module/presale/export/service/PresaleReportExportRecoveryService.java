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

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresaleReportExportRecoveryService {
    private final PresaleReportExportMapper exportMapper;
    private final PresaleExportWorkerIdentity workerIdentity;
    private final PresaleExportProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void markOwnRunningInterrupted() {
        String workerId = workerIdentity.workerId();
        List<PresaleReportExport> running = exportMapper.selectRunningByWorker(workerId);
        if (running.isEmpty()) {
            return;
        }
        int updated = exportMapper.markInterruptedByRestart(workerId,
                "{\"retry_history\":[{\"error_code\":\"INTERRUPTED_BY_RESTART\"}]}");
        log.info("Presale export restart recovery marked {} RUNNING tasks as FAILED, workerId={}, exportIds={}",
                updated, workerId, running.stream().map(PresaleReportExport::getId).toList());
    }

    @Scheduled(fixedDelayString = "${geo.presale-export.worker.stale-scan-interval-ms:60000}")
    public void markStaleRunningFailed() {
        if (!properties.getWorker().isEnabled()) {
            return;
        }
        LocalDateTime deadline = LocalDateTime.now().minusNanos(properties.getWorker().getStaleRunningTimeoutMs() * 1_000_000);
        List<PresaleReportExport> stale = exportMapper.selectStaleRunning(deadline);
        for (PresaleReportExport task : stale) {
            int updated = exportMapper.markStaleFailed(task.getId(),
                    "{\"retry_history\":[{\"error_code\":\"WORKER_HEARTBEAT_TIMEOUT\"}]}");
            if (updated > 0) {
                log.warn("Stale RUNNING force-failed: exportId={}, last_updated_at={}, worker_id={}",
                        task.getId(), task.getUpdatedAt(), task.getWorkerId());
            }
        }
    }
}
