package com.huanjing.geo.module.content.authoritymedia;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorityMediaOrderStatusJob {

    private final MeititejiaProperties properties;
    private final AuthorityMediaOrderStatusService statusService;

    @Scheduled(fixedDelayString = "${geo.meititejia.order-status-check-ms:60000}")
    public void checkDueNewsMediaOrders() {
        if (!properties.isEnabled() || !properties.isOrderStatusCheckEnabled()) {
            return;
        }
        try {
            AuthorityMediaOrderStatusService.StatusCheckResult result =
                    statusService.checkDueNewsMediaOrders(properties.getOrderStatusBatchSize());
            if (result.selected() > 0) {
                log.info("Meititejia NEWS_MEDIA order status check finished: {}", result);
            }
        } catch (Exception ex) {
            log.error("Meititejia NEWS_MEDIA order status check failed", ex);
        }
    }
}
