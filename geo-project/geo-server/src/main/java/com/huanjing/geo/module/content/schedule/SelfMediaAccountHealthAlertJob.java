package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.service.AccountAuthHealthScanCoordinator;
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
    private final AccountAuthHealthScanCoordinator scanCoordinator;

    @Scheduled(cron = "${geo.self-media-account-health.alert.cron:0 20 8 * * *}", zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void run() {
        if (!scanCoordinator.runScheduled("self_media")) {
            log.info("self media account health scan skipped because another authorization scan is running");
        }
    }
}
