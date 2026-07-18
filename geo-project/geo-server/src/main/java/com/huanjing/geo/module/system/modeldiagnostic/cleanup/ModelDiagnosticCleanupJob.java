package com.huanjing.geo.module.system.modeldiagnostic.cleanup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "geo.model-diagnostic.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ModelDiagnosticCleanupJob {

    private final ModelDiagnosticCleanupService cleanupService;
    private final ModelDiagnosticCleanupLockService lockService;
    private final int retentionDays;
    private final int batchSize;
    private final int maxBatches;
    private final ZoneId cleanupZone;

    public ModelDiagnosticCleanupJob(
            ModelDiagnosticCleanupService cleanupService,
            ModelDiagnosticCleanupLockService lockService,
            @Value("${geo.model-diagnostic.cleanup.retention-days:${MODEL_DIAGNOSTIC_RETENTION_DAYS:30}}")
            int retentionDays,
            @Value("${geo.model-diagnostic.cleanup.batch-size:500}") int batchSize,
            @Value("${geo.model-diagnostic.cleanup.max-batches:20}") int maxBatches,
            @Value("${geo.model-diagnostic.cleanup.zone:Asia/Shanghai}") String cleanupZone) {
        this.cleanupService = cleanupService;
        this.lockService = lockService;
        this.retentionDays = Math.max(1, retentionDays);
        this.batchSize = Math.max(1, Math.min(batchSize, 1_000));
        this.maxBatches = Math.max(1, Math.min(maxBatches, 100));
        this.cleanupZone = ZoneId.of(cleanupZone);
    }

    @Scheduled(
            cron = "${geo.model-diagnostic.cleanup.cron:0 30 3 * * *}",
            zone = "${geo.model-diagnostic.cleanup.zone:Asia/Shanghai}")
    public void cleanupScheduled() {
        String ownerToken = UUID.randomUUID().toString();
        if (!lockService.tryAcquire(ownerToken)) {
            return;
        }
        int runsDeleted = 0;
        int sessionsDeleted = 0;
        try {
            LocalDateTime cutoff = LocalDateTime.now(cleanupZone).minusDays(retentionDays);
            for (int batch = 0; batch < maxBatches; batch++) {
                ModelDiagnosticCleanupBatch result =
                        cleanupService.cleanupExpiredBatch(cutoff, batchSize);
                runsDeleted += result.runsDeleted();
                sessionsDeleted += result.sessionsDeleted();
                if (!result.incomplete(batchSize)) {
                    break;
                }
            }
            if (runsDeleted > 0 || sessionsDeleted > 0) {
                log.info("Diagnostic retention cleanup deleted {} runs and {} sessions",
                        runsDeleted, sessionsDeleted);
            }
        } catch (RuntimeException ex) {
            log.error("Diagnostic retention cleanup failed", ex);
        } finally {
            lockService.release(ownerToken);
        }
    }
}
