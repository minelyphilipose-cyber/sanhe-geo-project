package com.huanjing.geo.common.llm;

import org.springframework.util.StringUtils;

public record LlmModelConfig(String platformCode,
                             String platformName,
                             String modelId,
                             String modelName,
                             String apiUrl,
                             String apiKey,
                             String systemPrompt,
                             Double temperature,
                             Integer connectTimeoutMs,
                             Integer requestTimeoutMs,
                             Integer maxRetry,
                             Integer rateLimitQps,
                             Integer maxTokens,
                             boolean normalizeJsonOutput) {

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    public static final int DEFAULT_REQUEST_TIMEOUT_MS = 30_000;
    public static final int MAX_REQUEST_TIMEOUT_MS = 60_000;

    public LlmModelConfig {
        if (!StringUtils.hasText(platformCode)) {
            throw new IllegalArgumentException("platformCode must not be blank");
        }
        if (!StringUtils.hasText(modelId)) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        if (!StringUtils.hasText(apiUrl)) {
            throw new IllegalArgumentException("apiUrl must not be blank");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        connectTimeoutMs = positiveOrDefault(connectTimeoutMs, DEFAULT_CONNECT_TIMEOUT_MS);
        requestTimeoutMs = positiveOrDefault(requestTimeoutMs, DEFAULT_REQUEST_TIMEOUT_MS);
        if (requestTimeoutMs > MAX_REQUEST_TIMEOUT_MS) {
            throw new IllegalArgumentException("requestTimeoutMs must be <= " + MAX_REQUEST_TIMEOUT_MS);
        }
        maxRetry = Math.max(0, positiveOrDefault(maxRetry, 2));
        rateLimitQps = Math.max(1, positiveOrDefault(rateLimitQps, 1));
        temperature = temperature == null ? 0D : temperature;
        maxTokens = maxTokens == null || maxTokens <= 0 ? null : maxTokens;
        platformCode = platformCode.trim();
        platformName = trimToNull(platformName);
        modelId = modelId.trim();
        modelName = trimToNull(modelName);
        apiUrl = apiUrl.trim();
        systemPrompt = trimToNull(systemPrompt);
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
