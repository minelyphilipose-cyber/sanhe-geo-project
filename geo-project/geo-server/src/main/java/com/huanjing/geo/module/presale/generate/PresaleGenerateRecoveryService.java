package com.huanjing.geo.module.presale.generate;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresaleGenerateRecoveryService {
    private static final String STALE_FAILURE_REASON = "Generation worker heartbeat timed out";

    private final PresaleReportVersionMapper versionMapper;
    private final PresaleReportMapper reportMapper;
    private final PresaleGenerateOrchestrator orchestrator;

    @Value("${presale.generate.recovery.enabled:true}")
    private boolean enabled;

    @Value("${presale.generate.recovery.running-timeout-ms:900000}")
    private long runningTimeoutMs;

    @Value("${presale.generate.recovery.batch-size:50}")
    private int batchSize;

    @Value("${presale.generate.max-concurrent-reports:1}")
    private int maxConcurrentReports;

    @Value("${presale.generate.queue-dispatch.batch-size:10}")
    private int queueDispatchBatchSize;

    @Scheduled(fixedDelayString = "${presale.generate.recovery.scan-interval-ms:60000}")
    public void recoverStaleGenerations() {
        if (!enabled) {
            return;
        }
        recoverOnce();
    }

    int recoverOnce() {
        int changed = failStaleRunning();
        changed += dispatchQueued();
        return changed;
    }

    private int dispatchQueued() {
        int capacity = safeMaxConcurrentReports() - versionMapper.countRunningGenerations();
        if (capacity <= 0) {
            return 0;
        }
        List<PresaleReportVersion> queued = versionMapper.selectQueuedForDispatch(
                Math.min(capacity, safeQueueDispatchBatchSize())
        );
        int dispatched = 0;
        for (PresaleReportVersion version : queued) {
            if (version == null || version.getId() == null) {
                continue;
            }
            orchestrator.triggerGenerate(version.getId(), version.getCreatedBy(), false);
            dispatched++;
            log.info("Dispatched QUEUED presale generation: versionId={}, reportId={}, maxConcurrentReports={}",
                    version.getId(), version.getReportId(), safeMaxConcurrentReports());
        }
        return dispatched;
    }

    private int failStaleRunning() {
        LocalDateTime deadline = LocalDateTime.now()
                .minus(java.time.Duration.ofMillis(Math.max(runningTimeoutMs, 1L)));
        List<PresaleReportVersion> staleRunning = versionMapper.selectStaleRunning(deadline, safeBatchSize());
        int failed = 0;
        for (PresaleReportVersion version : staleRunning) {
            if (version == null || version.getId() == null) {
                continue;
            }
            long generationAttempt = version.getGenerationAttempt() == null
                    ? 0L
                    : version.getGenerationAttempt();
            int updated = generationAttempt > 0L
                    ? versionMapper.markStaleRunningAttemptFailed(
                            version.getId(), generationAttempt, STALE_FAILURE_REASON)
                    : versionMapper.markStaleRunningFailed(version.getId(), STALE_FAILURE_REASON);
            if (updated <= 0) {
                continue;
            }
            markLatestReportFailed(version);
            failed++;
            log.warn("Marked stale RUNNING presale generation failed: versionId={}, reportId={}, stage={}, updatedAt={}",
                    version.getId(), version.getReportId(), version.getGenerationStage(), version.getUpdatedAt());
        }
        return failed;
    }

    private void markLatestReportFailed(PresaleReportVersion version) {
        if (version.getReportId() == null) {
            return;
        }
        PresaleReport update = new PresaleReport();
        update.setStatus(PresaleGenerateStatus.FAILED.name());
        update.setUpdatedAt(LocalDateTime.now());
        reportMapper.update(update, new LambdaUpdateWrapper<PresaleReport>()
                .eq(PresaleReport::getId, version.getReportId())
                .eq(PresaleReport::getLatestVersionId, version.getId()));
    }

    private int safeBatchSize() {
        return Math.max(1, batchSize);
    }

    private int safeQueueDispatchBatchSize() {
        return Math.max(1, queueDispatchBatchSize);
    }

    private int safeMaxConcurrentReports() {
        return Math.max(1, maxConcurrentReports);
    }
}
