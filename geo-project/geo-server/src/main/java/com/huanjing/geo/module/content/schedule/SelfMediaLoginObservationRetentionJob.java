package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.mapper.SelfMediaLoginObservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SelfMediaLoginObservationRetentionJob {
    private final SelfMediaLoginObservationMapper observationMapper;

    @Value("${geo.self-media-account-health.observation-retention-days:180}")
    private int retentionDays;

    @Value("${geo.self-media-account-health.observation-cleanup-batch-size:1000}")
    private int batchSize;

    @Value("${geo.self-media-account-health.observation-cleanup-max-batches:20}")
    private int maxBatches;

    @Scheduled(
            cron = "${geo.self-media-account-health.observation-cleanup-cron:0 35 4 * * *}",
            zone = "${geo.dispatch.timezone:Asia/Shanghai}"
    )
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(retentionDays, 1));
        int safeBatchSize = Math.max(1, Math.min(batchSize, 10_000));
        int safeMaxBatches = Math.max(1, Math.min(maxBatches, 100));
        int deleted = 0;
        for (int i = 0; i < safeMaxBatches; i++) {
            int count = observationMapper.deleteExpiredBatch(cutoff, safeBatchSize);
            deleted += count;
            if (count < safeBatchSize) break;
        }
        if (deleted > 0) {
            log.info("Cleaned up {} expired self-media login observations before {}", deleted, cutoff);
        }
    }
}
