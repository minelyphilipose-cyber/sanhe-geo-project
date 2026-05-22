package com.huanjing.geo.module.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
    private String queueKey = "geo:dispatch:queue:zset";
    private String retryDelays = "1m,5m,15m";
    private int taskTimeoutMinutes = 60;
    private int recoverBatchSize = 500;
    private int taskRetentionDays = 90;
    private int modelConnectTimeoutMs = 10000;
    private int modelRequestTimeoutMs = 45000;
    private int questionPollShardSize = 20;
    private int resourceBusyRetryMinSeconds = 30;
    private int resourceBusyRetryJitterSeconds = 30;
    private int resourceBusyMaxAttempts = 60;
    private boolean autoContentGenerationEnabled = false;

    public void setQuestionPollShardSize(int questionPollShardSize) {
        this.questionPollShardSize = Math.max(1, questionPollShardSize);
    }

    public void setResourceBusyRetryMinSeconds(int resourceBusyRetryMinSeconds) {
        this.resourceBusyRetryMinSeconds = Math.max(1, resourceBusyRetryMinSeconds);
    }

    public void setResourceBusyRetryJitterSeconds(int resourceBusyRetryJitterSeconds) {
        this.resourceBusyRetryJitterSeconds = Math.max(0, resourceBusyRetryJitterSeconds);
    }

    public void setResourceBusyMaxAttempts(int resourceBusyMaxAttempts) {
        this.resourceBusyMaxAttempts = Math.max(1, resourceBusyMaxAttempts);
    }
}
