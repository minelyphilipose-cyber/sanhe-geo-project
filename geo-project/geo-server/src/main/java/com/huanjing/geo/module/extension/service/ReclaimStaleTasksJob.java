package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.module.system.service.SystemAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReclaimStaleTasksJob {

    private static final int ALERT_FAILURE_THRESHOLD = 3;
    private static final String LOCK_KEY = "reclaim:stale_tasks:lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final ExtensionTaskStateService taskStateService;
    private final ExtensionRedisStore redisStore;
    private final SystemAlertService systemAlertService;
    private final String lockValue = UUID.randomUUID().toString();
    private int consecutiveFailures;

    @Scheduled(fixedDelayString = "${geo.extension.tasks.reclaim-fixed-delay-ms:60000}")
    public void reclaim() {
        if (!redisStore.tryLock(LOCK_KEY, lockValue, LOCK_TTL)) {
            log.debug("skip reclaim stale semi-auto tasks because another instance holds the lock");
            return;
        }
        try {
            int reclaimed = taskStateService.reclaimStaleTasks();
            if (reclaimed > 0) {
                log.info("reclaimed stale semi-auto tasks count={}", reclaimed);
            }
            consecutiveFailures = 0;
        } catch (Exception ex) {
            consecutiveFailures++;
            log.error("reclaim stale semi-auto tasks failed consecutiveFailures={}", consecutiveFailures, ex);
            if (consecutiveFailures == ALERT_FAILURE_THRESHOLD) {
                systemAlertService.createAlert(
                        "semi_auto_reclaim_failed",
                        "error",
                        "semi_auto_task",
                        "Reclaim stale semi-auto tasks failed",
                        Map.of(
                                "consecutiveFailures", consecutiveFailures,
                                "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
                        )
                );
            }
        } finally {
            releaseLockSafely();
        }
    }

    private void releaseLockSafely() {
        try {
            redisStore.releaseLock(LOCK_KEY, lockValue);
        } catch (Exception ex) {
            log.warn("failed to release stale task reclaim lock", ex);
        }
    }
}
