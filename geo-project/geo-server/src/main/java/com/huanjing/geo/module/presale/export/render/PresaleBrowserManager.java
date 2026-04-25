package com.huanjing.geo.module.presale.export.render;

import com.huanjing.geo.module.presale.export.config.PresaleExportProperties;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresaleBrowserManager {

    private final PresaleExportProperties properties;

    private Playwright playwright;
    private Browser browser;
    @Getter
    private volatile BrowserStatus status = BrowserStatus.NOT_STARTED;
    @Getter
    private volatile String lastError;
    @Getter
    private volatile Instant lastStartedAt;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void preheatOnStartup() {
        PresaleExportProperties.Browser browserProps = properties.getBrowser();
        if (!browserProps.isEnabled() || !browserProps.isPreheatOnStartup()) {
            status = BrowserStatus.DISABLED;
            return;
        }
        try {
            getBrowser();
            log.info("Presale export browser preheated");
        } catch (Exception ex) {
            status = BrowserStatus.DEGRADED;
            lastError = ex.getMessage();
            // Do not fail application startup. First export will retry lazy initialization.
            log.error("Presale export browser preheat failed; fallback to lazy initialization", ex);
        }
    }

    public synchronized Browser getBrowser() {
        if (!properties.getBrowser().isEnabled()) {
            throw new IllegalStateException("Presale export browser is disabled");
        }
        if (browser != null && browser.isConnected()) {
            status = BrowserStatus.READY;
            return browser;
        }

        status = BrowserStatus.STARTING;
        long started = System.nanoTime();
        try {
            closeQuietly();
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(properties.getBrowser().isHeadless()));
            status = BrowserStatus.READY;
            lastError = null;
            lastStartedAt = Instant.now();
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            log.info("Presale export browser started in {}ms", elapsedMs);
            return browser;
        } catch (RuntimeException ex) {
            status = BrowserStatus.DEGRADED;
            lastError = ex.getMessage();
            closeQuietly();
            throw ex;
        }
    }

    @PreDestroy
    public synchronized void shutdown() {
        closeQuietly();
        status = BrowserStatus.STOPPED;
    }

    private void closeQuietly() {
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception ignored) {
                // Shutdown best effort.
            }
            browser = null;
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception ignored) {
                // Shutdown best effort.
            }
            playwright = null;
        }
    }
}
