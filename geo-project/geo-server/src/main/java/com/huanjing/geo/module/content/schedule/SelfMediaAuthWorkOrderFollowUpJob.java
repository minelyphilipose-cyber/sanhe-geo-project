package com.huanjing.geo.module.content.schedule;

import com.huanjing.geo.module.content.service.SelfMediaAuthWorkOrderFollowUpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.self-media-auth-work-order.follow-up", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SelfMediaAuthWorkOrderFollowUpJob {
    private final SelfMediaAuthWorkOrderFollowUpService followUpService;

    @Scheduled(fixedDelayString = "${geo.self-media-auth-work-order.follow-up.fixed-delay-ms:300000}")
    public void run() {
        int dueCount = followUpService.scanOnce();
        if (dueCount > 0) {
            log.info("self media auth work order follow-up due count={}", dueCount);
        }
    }
}
