package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.module.extension.mapper.LocalAgentBrowserMetricSampleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrowserMetricSampleRetentionJob {

    private final LocalAgentBrowserMetricSampleMapper sampleMapper;

    @Value("${geo.extension.browser-metrics.retention-days:14}")
    private int retentionDays;

    @Value("${geo.extension.browser-metrics.cleanup-batch-size:1000}")
    private int cleanupBatchSize;

    @Value("${geo.extension.browser-metrics.cleanup-max-batches:20}")
    private int cleanupMaxBatches;

    @Scheduled(
            cron = "${geo.extension.browser-metrics.cleanup-cron:0 15 4 * * *}",
            zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void cleanupExpiredSamples() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
        int batchSize = Math.max(100, Math.min(cleanupBatchSize, 5000));
        int maxBatches = Math.max(1, Math.min(cleanupMaxBatches, 100));
        int deleted = 0;
        try {
            for (int batch = 0; batch < maxBatches; batch++) {
                int current = sampleMapper.deleteExpiredBatch(cutoff, batchSize);
                deleted += current;
                if (current < batchSize) {
                    break;
                }
            }
            if (deleted > 0) {
                log.info("Browser metric sample retention deleted {} rows older than {}", deleted, cutoff);
            }
        } catch (RuntimeException ex) {
            log.error("Browser metric sample retention cleanup failed", ex);
        }
    }
}
