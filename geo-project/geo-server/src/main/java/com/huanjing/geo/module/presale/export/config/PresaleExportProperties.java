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
    private Quality quality = new Quality();
    private Cleanup cleanup = new Cleanup();

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
        private long scanIntervalMs = 1_000;
        private int claimBatchSize = 1;
        private long heartbeatIntervalMs = 30_000;
        private long staleScanIntervalMs = 60_000;
        private long staleRunningTimeoutMs = 90_000;
    }

    @Data
    public static class Retry {
        private int maxCount = 3;
    }

    @Data
    public static class Storage {
        private long inlineSnapshotMaxBytes = 1_048_576;
        private int expireDays = 7;
        private int debugRetentionDays = 7;
        private String localRoot = "target/presale-exports";
    }

    @Data
    public static class Quality {
        private boolean enforceBottomBand = true;
        private boolean enforceChartNonBlank = false;
        private boolean enforcePageCount = true;
        private int bottomBandPx = 110;
    }

    @Data
    public static class Cleanup {
        private boolean enabled = true;
        private String cron = "0 15 3 * * *";
        private int batchSize = 1_000;
        private String lockKey = "lock:presale-export-cleanup";
        private long lockTtlMs = 1_800_000;
        private long cronJitterMs = 60_000;
        private int maxRetryCount = 5;
    }
}
