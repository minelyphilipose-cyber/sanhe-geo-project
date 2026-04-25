package com.huanjing.geo.module.presale.export.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.presale-export")
public class PresaleExportProperties {

    private Browser browser = new Browser();

    @Data
    public static class Browser {
        private boolean enabled = true;
        private boolean preheatOnStartup = true;
        private boolean headless = true;
        private double deviceScaleFactor = 2;
        private int viewportWidth = 1440;
        private int viewportHeight = 2200;
        private int browserStartTimeoutMs = 10_000;
        private int pageLoadTimeoutMs = 30_000;
        private int readyTimeoutMs = 60_000;
        private int pdfTimeoutMs = 60_000;
        private int acquireTimeoutMs = 5_000;
        private int maxConcurrency = 1;
    }
}
