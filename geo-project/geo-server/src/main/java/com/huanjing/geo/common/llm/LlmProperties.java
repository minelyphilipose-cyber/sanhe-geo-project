package com.huanjing.geo.common.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "geo.llm")
public class LlmProperties {

    private int connectTimeoutMs = LlmModelConfig.DEFAULT_CONNECT_TIMEOUT_MS;
    private int requestTimeoutMs = LlmModelConfig.DEFAULT_REQUEST_TIMEOUT_MS;
    private int maxRetry = 2;
    private int rateLimitQps = 1;

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = positiveOrDefault(connectTimeoutMs, LlmModelConfig.DEFAULT_CONNECT_TIMEOUT_MS);
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        if (requestTimeoutMs > LlmModelConfig.MAX_REQUEST_TIMEOUT_MS) {
            throw new IllegalArgumentException("geo.llm.request-timeout-ms must be <= "
                    + LlmModelConfig.MAX_REQUEST_TIMEOUT_MS);
        }
        this.requestTimeoutMs = positiveOrDefault(requestTimeoutMs, LlmModelConfig.DEFAULT_REQUEST_TIMEOUT_MS);
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = Math.max(0, maxRetry);
    }

    public void setRateLimitQps(int rateLimitQps) {
        this.rateLimitQps = Math.max(1, rateLimitQps);
    }

    private int positiveOrDefault(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }
}
