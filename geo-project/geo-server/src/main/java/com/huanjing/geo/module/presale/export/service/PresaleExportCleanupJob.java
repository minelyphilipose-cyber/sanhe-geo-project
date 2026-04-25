package com.huanjing.geo.module.presale.export.service;

import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresaleExportCleanupJob {
    private final PresaleExportCleanupService cleanupService;
    private final PresaleExportCleanupLockService lockService;
    private final PresaleExportProperties properties;

    @Scheduled(cron = "${geo.presale-export.cleanup.cron:0 15 3 * * *}")
    public void cleanupExpiredExports() {
        PresaleExportProperties.Cleanup cleanup = properties.getCleanup();
        if (!cleanup.isEnabled()) {
            return;
        }

        applyCronJitter(cleanup.getCronJitterMs());
        String lockValue = buildLockValue();
        if (!lockService.tryAcquire(lockValue)) {
            log.info("Presale export cleanup skipped because lock is unavailable or held by another instance");
            return;
        }

        try {
            cleanupService.cleanupExpiredOnce();
        } finally {
            lockService.release(lockValue);
        }
    }

    private void applyCronJitter(long cronJitterMs) {
        if (cronJitterMs <= 0) {
            return;
        }
        long delayMs = ThreadLocalRandom.current().nextLong(cronJitterMs + 1);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildLockValue() {
        return resolveHost() + ":" + ManagementFactory.getRuntimeMXBean().getName() + ":" + UUID.randomUUID();
    }

    private String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown-host";
        }
    }
}
