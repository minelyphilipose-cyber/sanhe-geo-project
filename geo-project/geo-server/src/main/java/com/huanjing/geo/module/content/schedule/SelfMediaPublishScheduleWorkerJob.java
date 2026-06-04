package com.huanjing.geo.module.content.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.self-media-schedule.worker", name = "enabled", havingValue = "true")
public class SelfMediaPublishScheduleWorkerJob {
    private final SelfMediaPublishScheduleWorker worker;

    @Value("${geo.self-media-schedule.worker.batch-size:5}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${geo.self-media-schedule.worker.fixed-delay-ms:60000}")
    public void run() {
        int processed = worker.runBatch(batchSize);
        if (processed > 0) {
            log.info("self media publish schedule worker processed count={}", processed);
        }
    }
}
