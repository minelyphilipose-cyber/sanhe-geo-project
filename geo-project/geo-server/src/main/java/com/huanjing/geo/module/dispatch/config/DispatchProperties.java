package com.huanjing.geo.module.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "geo.dispatch")
public class DispatchProperties {
    private String cron = "0 5 0 * * *";
    private String timezone = "Asia/Shanghai";
    private String lockKey = "geo:dispatch:scan:lock";
    private long lockTtlSeconds = 600;
    private int workerPollConcurrency = 4;
    private long workerPollMs = 1000;
    private boolean workerPopAdmissionEnabled = true;
    private int workerMaxInFlight = 0;
    private boolean workerPermitGovernorEnabled = false;
    private double workerPermitGovernorBusyRatio = 0.9D;
    private String queueKey = "geo:dispatch:queue:zset";
    private String retryDelays = "1m,5m,15m";
    private int taskTimeoutMinutes = 60;
    private int recoverBatchSize = 500;
    private int taskRetentionDays = 90;
    private boolean taskCleanupEnabled = false;
    private int modelConnectTimeoutMs = 10000;
    private int modelRequestTimeoutMs = 45000;
    private int questionPollRequestTimeoutCapMs = 120000;
    private int questionPollShardSize = 20;
    private int questionPollMaxShardSize = 20;
    private int questionPollCycleDays = 1;
    private String questionPollModelTier = "primary";
    private int resourceBusyRetryMinSeconds = 30;
    private int resourceBusyRetryJitterSeconds = 30;
    private int resourceBusyRetryMaxSeconds = 900;
    private int resourceBusyMaxAttempts = 60;
    private boolean capacityFailureClassificationEnabled = false;
    private boolean resourceBusyRetryAfterEnabled = false;
    private boolean autoContentGenerationEnabled = false;
    private Stagger stagger = new Stagger();

    public void setQuestionPollShardSize(int questionPollShardSize) {
        this.questionPollShardSize = Math.max(1, questionPollShardSize);
    }

    public void setWorkerPollConcurrency(int workerPollConcurrency) {
        this.workerPollConcurrency = Math.max(1, workerPollConcurrency);
    }

    public void setWorkerMaxInFlight(int workerMaxInFlight) {
        this.workerMaxInFlight = Math.max(0, workerMaxInFlight);
    }

    public void setWorkerPermitGovernorBusyRatio(double workerPermitGovernorBusyRatio) {
        if (Double.isNaN(workerPermitGovernorBusyRatio) || Double.isInfinite(workerPermitGovernorBusyRatio)) {
            this.workerPermitGovernorBusyRatio = 0.9D;
            return;
        }
        this.workerPermitGovernorBusyRatio = Math.max(0.0D, Math.min(1.0D, workerPermitGovernorBusyRatio));
    }

    public void setQuestionPollMaxShardSize(int questionPollMaxShardSize) {
        this.questionPollMaxShardSize = Math.max(1, questionPollMaxShardSize);
    }

    public void setQuestionPollRequestTimeoutCapMs(int questionPollRequestTimeoutCapMs) {
        this.questionPollRequestTimeoutCapMs = Math.max(1_000, questionPollRequestTimeoutCapMs);
    }

    public void setQuestionPollCycleDays(int questionPollCycleDays) {
        this.questionPollCycleDays = Math.max(1, questionPollCycleDays);
    }

    public void setQuestionPollModelTier(String questionPollModelTier) {
        this.questionPollModelTier = questionPollModelTier == null || questionPollModelTier.isBlank()
                ? "primary"
                : questionPollModelTier.trim();
    }

    public void setResourceBusyRetryMinSeconds(int resourceBusyRetryMinSeconds) {
        this.resourceBusyRetryMinSeconds = Math.max(1, resourceBusyRetryMinSeconds);
    }

    public void setResourceBusyRetryJitterSeconds(int resourceBusyRetryJitterSeconds) {
        this.resourceBusyRetryJitterSeconds = Math.max(0, resourceBusyRetryJitterSeconds);
    }

    public void setResourceBusyRetryMaxSeconds(int resourceBusyRetryMaxSeconds) {
        this.resourceBusyRetryMaxSeconds = Math.max(1, resourceBusyRetryMaxSeconds);
    }

    public void setResourceBusyMaxAttempts(int resourceBusyMaxAttempts) {
        this.resourceBusyMaxAttempts = Math.max(1, resourceBusyMaxAttempts);
    }

    @Data
    public static class Stagger {
        private boolean enabled = false;
        private String taskTypes = "QUESTION_POLL";
        private int windowMinutes = 60;
        private int maxDelayMinutes = 60;
        private int jitterSeconds = 60;
        private int capJitterSeconds = 60;
        private long maxQueueSize = 0L;
        private String overflowPolicy = "ALERT_ONLY";
        private Map<String, PlatformOverride> platforms = new LinkedHashMap<>();

        public void setWindowMinutes(int windowMinutes) {
            this.windowMinutes = Math.max(1, windowMinutes);
        }

        public void setMaxDelayMinutes(int maxDelayMinutes) {
            this.maxDelayMinutes = Math.max(1, maxDelayMinutes);
        }

        public void setJitterSeconds(int jitterSeconds) {
            this.jitterSeconds = Math.max(0, jitterSeconds);
        }

        public void setCapJitterSeconds(int capJitterSeconds) {
            this.capJitterSeconds = Math.max(0, capJitterSeconds);
        }

        public void setMaxQueueSize(long maxQueueSize) {
            this.maxQueueSize = Math.max(0L, maxQueueSize);
        }
    }

    @Data
    public static class PlatformOverride {
        private Integer windowMinutes;
        private Integer maxDelayMinutes;
        private Integer jitterSeconds;
        private Integer capJitterSeconds;

        public void setWindowMinutes(Integer windowMinutes) {
            this.windowMinutes = windowMinutes == null ? null : Math.max(1, windowMinutes);
        }

        public void setMaxDelayMinutes(Integer maxDelayMinutes) {
            this.maxDelayMinutes = maxDelayMinutes == null ? null : Math.max(1, maxDelayMinutes);
        }

        public void setJitterSeconds(Integer jitterSeconds) {
            this.jitterSeconds = jitterSeconds == null ? null : Math.max(0, jitterSeconds);
        }

        public void setCapJitterSeconds(Integer capJitterSeconds) {
            this.capJitterSeconds = capJitterSeconds == null ? null : Math.max(0, capJitterSeconds);
        }
    }
}
