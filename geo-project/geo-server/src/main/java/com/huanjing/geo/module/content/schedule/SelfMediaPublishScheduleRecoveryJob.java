package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.service.SelfMediaPublishScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.self-media-schedule.recovery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SelfMediaPublishScheduleRecoveryJob {
    private final SelfMediaPublishScheduleService scheduleService;

    @Value("${geo.self-media-schedule.recovery.limit:50}")
    private int limit;

    @Scheduled(fixedDelayString = "${geo.self-media-schedule.recovery.fixed-delay-ms:60000}")
    public void run() {
        int recovered = scheduleService.recoverTimedOutLocalAgentSchedules(limit);
        if (recovered > 0) {
            log.info("self media publish schedule recovery recovered count={}", recovered);
        }
    }
}
