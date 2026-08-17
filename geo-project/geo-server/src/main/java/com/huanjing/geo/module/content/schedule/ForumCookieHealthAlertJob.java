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
@ConditionalOnProperty(prefix = "geo.forum-cookie-health.alert", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ForumCookieHealthAlertJob {
    private final AccountAuthHealthScanCoordinator scanCoordinator;

    @Scheduled(cron = "${geo.forum-cookie-health.alert.cron:0 30 8 * * *}", zone = "${geo.dispatch.timezone:Asia/Shanghai}")
    public void run() {
        if (!scanCoordinator.runScheduled("forum")) {
            log.info("forum cookie health scan skipped because another authorization scan is running");
        }
    }
}
