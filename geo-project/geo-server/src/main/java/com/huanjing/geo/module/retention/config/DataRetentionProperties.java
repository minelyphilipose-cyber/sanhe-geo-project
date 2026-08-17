package com.huanjing.geo.module.retention.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.retention")
public class DataRetentionProperties {
    private Scheduler scheduler = new Scheduler();
    private WebsitePublishedCleanup websitePublishedCleanup = new WebsitePublishedCleanup();
    private ArticleArchive articleArchive = new ArticleArchive();
    private ArticlePurge articlePurge = new ArticlePurge();
    private PollResults pollResults = new PollResults();
    private ContentUrlRewrite contentUrlRewrite = new ContentUrlRewrite();
    private ExecutePromotion executePromotion = new ExecutePromotion();
    private Execute execute = new Execute();
    private DynamicSwitch dynamicSwitch = new DynamicSwitch();
    private DetailCleanup detailCleanup = new DetailCleanup();

    @Data
    public static class Scheduler {
        private boolean enabled = false;
        private boolean executeEnabled = false;
        private String cron = "0 30 3 * * *";
        private int limitPerDomain = 100;
        private int maxBatchesPerRun = 20;
        private int pollHotRetentionDays = 14;
        private int websitePublishedRetentionHours = 24;
        private int articleRetentionDays = 90;
        private int articleArchiveGraceHours = 24;
        private int objectSafetyAgeHours = 24;
        private long operatorUserId = 0L;
        private String lockKey = "geo:retention:scheduler:lock";
        private long lockTtlSeconds = 7200L;
    }

    @Data
    public static class WebsitePublishedCleanup {
        private boolean executeEnabled = false;
    }

    @Data
    public static class ArticleArchive {
        private boolean executeEnabled = false;
    }

    @Data
    public static class ArticlePurge {
        private boolean executeEnabled = false;
    }

    @Data
    public static class PollResults {
        private boolean executeEnabled = false;
    }

    @Data
    public static class ContentUrlRewrite {
        private boolean executeEnabled = false;
    }

    @Data
    public static class ExecutePromotion {
        private boolean enabled = false;
        private int consecutiveDryRunDays = 7;
        private boolean requirePerfectReconciliation = true;
        private boolean requireNoP0Alerts = true;
        private boolean requireDualApproval = true;
    }

    @Data
    public static class Execute {
        private int mutationTimeoutSeconds = 120;
        private int maxRunSeconds = 3600;
        private int maxDeferredRetriesPerDomain = 20;
        private int maxMutationTransactions = 20;
        private long maxCandidateItems = 100000L;
    }

    @Data
    public static class DynamicSwitch {
        private boolean enableWriteEnabled = false;
        private boolean reconcilerEnabled = true;
        private long reconcileOperatorUserId = 0L;
        private long reconcileInitialDelayMs = 30000L;
        private long reconcileFixedDelayMs = 30000L;
        private int reconcileBatchSize = 100;
    }

    @Data
    public static class DetailCleanup {
        private boolean enabled = false;
        private String cron = "0 15 2 * * *";
        private int maxOperationsPerRun = 20;
        private int maxOperationlessRunsPerRun = 20;
        private int maxAttemptsPerOperation = 1000;
        private int maxOperationlessAttemptsPerRun = 1000;
        private int maxRunSeconds = 3600;
        private String lockKey = "geo:retention:detail-cleanup:lock";
        private long lockTtlSeconds = 7200L;
    }
}
