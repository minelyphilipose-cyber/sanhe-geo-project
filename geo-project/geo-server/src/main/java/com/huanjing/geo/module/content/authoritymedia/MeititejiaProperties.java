package com.huanjing.geo.module.content.authoritymedia;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "geo.meititejia")
public class MeititejiaProperties {
    private boolean enabled = false;
    private boolean mockMode = true;
    private String baseUrl = "https://www.meititejia.com/meijieapi/daili3";
    private String secretId;
    private String secretKeyRef;
    /**
     * Local development and unit tests only. Production must use secretKeyRef
     * and inject the value through the corresponding environment variable.
     */
    private String secretKey;
    private String previewUrlBase;
    private boolean syncEnabled = false;
    private boolean orderStatusCheckEnabled = false;
    private int syncPageLimit = 200;
    private int orderStatusBatchSize = 50;
    private String newsMediaIncrementalCron = "0 0 * * * *";
    private String newsMediaFullCron = "0 30 2 * * *";
    private String newsMediaReconcileCron = "0 30 3 * * *";
    private int connectTimeoutMs = 5000;
    private int requestTimeoutMs = 30000;
    private int retryMaxAttempts = 3;
    private long retryBackoffMs = 1000;
    private int rateLimitQps = 2;
    private int resourceStalenessThresholdMinutes = 60;
    private BigDecimal quotaCheckThreshold = BigDecimal.ZERO;
    private BigDecimal balanceSafetyFactor = new BigDecimal("1.1");
    private ContentMode contentMode = ContentMode.LINK_ONLY;

    public enum ContentMode {
        LINK_ONLY,
        BODY_WITH_LINK
    }
}
