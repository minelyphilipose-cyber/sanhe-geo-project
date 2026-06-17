package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.service.SelfMediaAccountHealthAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.self-media-account-health.alert", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SelfMediaAccountHealthAlertJob {
    private final SelfMediaAccountHealthAlertService alertService;

    @Scheduled(cron = "${geo.self-media-account-health.alert.cron:0 20 8 * * *}", zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void run() {
        int changed = alertService.scanOnce();
        if (changed > 0) {
            log.info("self media account health alert scan checked count={}", changed);
        }
    }
}
