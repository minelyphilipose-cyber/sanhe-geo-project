package com.huanjing.geo.module.presale.export.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.presale-export")
public class PresaleExportProperties {

    private Browser browser = new Browser();
    private Worker worker = new Worker();
    private Retry retry = new Retry();
    private Storage storage = new Storage();

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

    @Data
    public static class Worker {
        private boolean enabled = true;
        private int poolSize = 1;
        private long scanIntervalMs = 1_000;
        private int claimBatchSize = 1;
        private long heartbeatIntervalMs = 30_000;
        private long staleScanIntervalMs = 60_000;
        private long staleRunningTimeoutMs = 120_000;
    }

    @Data
    public static class Retry {
        private int maxCount = 3;
    }

    @Data
    public static class Storage {
        private long inlineSnapshotMaxBytes = 1_048_576;
        private int expireDays = 7;
        private String localRoot = "target/presale-exports";
    }
}
