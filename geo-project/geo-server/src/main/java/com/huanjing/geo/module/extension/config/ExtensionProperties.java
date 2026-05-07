package com.huanjing.geo.module.extension.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.extension")
public class ExtensionProperties {

    private FillToken fillToken = new FillToken();
    private LongToken longToken = new LongToken();
    private BindCode bindCode = new BindCode();
    private Version version = new Version();

    @Data
    public static class FillToken {
        private String hmacSecret = "__REQUIRED_FILL_TOKEN_HMAC_SECRET__";
        private long ttlSeconds = 300;
    }

    @Data
    public static class LongToken {
        private long ttlDays = 7;
        private long slideRenewThresholdDays = 1;
    }

    @Data
    public static class BindCode {
        private int length = 8;
        private long ttlSeconds = 600;
        private int brandRateLimitPer5min = 5;
        private int ipRateLimitPer5min = 20;
    }

    @Data
    public static class Version {
        private boolean allowPrerelease = false;
    }
}
