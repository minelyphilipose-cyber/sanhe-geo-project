package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.service.SelfMediaPublishScheduleAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.self-media-schedule.alert", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SelfMediaPublishScheduleAlertJob {
    private final SelfMediaPublishScheduleAlertService alertService;

    @Scheduled(fixedDelayString = "${geo.self-media-schedule.alert.fixed-delay-ms:120000}")
    public void run() {
        int changed = alertService.scanOnce();
        if (changed > 0) {
            log.info("self media publish schedule alert scan changed count={}", changed);
        }
    }
}
