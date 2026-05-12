package com.huanjing.geo.module.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.douyin.client")
public class DouyinClientProperties {
    private String mode = "mock";
    private String baseUrl = "https://open.douyin.com";
    private int connectTimeoutMs = 5000;
    private int requestTimeoutMs = 30000;
    private Fault fault = new Fault();

    @Data
    public static class Fault {
        private boolean tokenExpired;
        private boolean uploadFailed;
        private boolean createFailed;
        private boolean rateLimit;
        private boolean permissionDenied;
        private String reviewOutcome = "passed";
    }
}
