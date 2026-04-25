package com.huanjing.geo.module.presale.export.render;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("presaleExportBrowser")
@RequiredArgsConstructor
public class PresaleBrowserHealthIndicator implements HealthIndicator {

    private final PresaleBrowserManager browserManager;

    @Override
    public Health health() {
        BrowserStatus status = browserManager.getStatus();
        Health.Builder builder = switch (status) {
            case READY, DISABLED -> Health.up();
            case NOT_STARTED, STARTING -> Health.unknown();
            case DEGRADED, STOPPED -> Health.down();
        };
        builder.withDetail("status", status.name());
        if (browserManager.getLastStartedAt() != null) {
            builder.withDetail("lastStartedAt", browserManager.getLastStartedAt().toString());
        }
        if (browserManager.getLastError() != null) {
            builder.withDetail("lastError", browserManager.getLastError());
        }
        return builder.build();
    }
}
