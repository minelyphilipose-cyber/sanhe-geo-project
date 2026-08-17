package com.huanjing.geo.module.retention.detailcleanup;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DataRetentionDetailCleanupScheduler {
    private final DataRetentionDetailCleanupService service;
    public DataRetentionDetailCleanupScheduler(DataRetentionDetailCleanupService service) { this.service = service; }

    @Scheduled(cron = "${geo.retention.detail-cleanup.cron:0 15 2 * * *}",
            zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void sweep() { service.runScheduled(); }
}
