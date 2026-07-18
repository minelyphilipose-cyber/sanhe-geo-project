package com.huanjing.geo.module.system.modeldiagnostic.recovery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "geo.model-diagnostic.recovery.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ModelDiagnosticStateRecoveryJob {

    static final int BATCH_SIZE = 100;
    private static final int STARTUP_MAX_BATCHES = 10;

    private final ModelDiagnosticStateRecoveryService recoveryService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recover("startup", STARTUP_MAX_BATCHES);
    }

    @Scheduled(
            fixedDelayString = "${geo.model-diagnostic.recovery.fixed-delay-ms:30000}",
            initialDelayString = "${geo.model-diagnostic.recovery.initial-delay-ms:30000}")
    public void recoverScheduled() {
        recover("scheduled", 1);
    }

    private void recover(String trigger, int maxBatches) {
        int totalAbandoned = 0;
        try {
            for (int batch = 0; batch < maxBatches; batch++) {
                ModelDiagnosticRecoveryBatch result =
                        recoveryService.recoverExpiredBatch(BATCH_SIZE);
                totalAbandoned += result.abandoned();
                if (result.scanned() < BATCH_SIZE) {
                    break;
                }
            }
            if (totalAbandoned > 0) {
                log.info("Recovered {} expired diagnostic runs during {} scan",
                        totalAbandoned, trigger);
            }
        } catch (RuntimeException ex) {
            log.error("Diagnostic state recovery failed during {} scan", trigger, ex);
        }
    }
}
