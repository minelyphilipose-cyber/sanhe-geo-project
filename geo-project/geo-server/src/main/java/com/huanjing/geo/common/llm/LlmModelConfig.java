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
                             boolean normalizeJsonOutput,
                             Integer requestTimeoutMaxMs,
                             String feature,
                             Integer concurrencyLimit,
                             boolean useExecutionGateway) {

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    public static final int DEFAULT_REQUEST_TIMEOUT_MS = 30_000;
    public static final int MAX_REQUEST_TIMEOUT_MS = 60_000;
    public static final int LONG_FORM_MAX_REQUEST_TIMEOUT_MS = 180_000;

    public LlmModelConfig(String platformCode,
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
        this(platformCode, platformName, modelId, modelName, apiUrl, apiKey, systemPrompt, temperature,
                connectTimeoutMs, requestTimeoutMs, maxRetry, rateLimitQps, maxTokens, normalizeJsonOutput,
                MAX_REQUEST_TIMEOUT_MS, "generic", 1, true);
    }

    public LlmModelConfig(String platformCode,
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
                          boolean normalizeJsonOutput,
                          Integer requestTimeoutMaxMs) {
        this(platformCode, platformName, modelId, modelName, apiUrl, apiKey, systemPrompt, temperature,
                connectTimeoutMs, requestTimeoutMs, maxRetry, rateLimitQps, maxTokens, normalizeJsonOutput,
                requestTimeoutMaxMs, "generic", 1, true);
    }

    public LlmModelConfig(String platformCode,
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
                          boolean normalizeJsonOutput,
                          Integer requestTimeoutMaxMs,
                          String feature) {
        this(platformCode, platformName, modelId, modelName, apiUrl, apiKey, systemPrompt, temperature,
                connectTimeoutMs, requestTimeoutMs, maxRetry, rateLimitQps, maxTokens, normalizeJsonOutput,
                requestTimeoutMaxMs, feature, 1, true);
    }

    public LlmModelConfig(String platformCode,
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
                          boolean normalizeJsonOutput,
                          Integer requestTimeoutMaxMs,
                          String feature,
                          Integer concurrencyLimit) {
        this(platformCode, platformName, modelId, modelName, apiUrl, apiKey, systemPrompt, temperature,
                connectTimeoutMs, requestTimeoutMs, maxRetry, rateLimitQps, maxTokens, normalizeJsonOutput,
                requestTimeoutMaxMs, feature, concurrencyLimit, true);
    }

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
        requestTimeoutMaxMs = positiveOrDefault(requestTimeoutMaxMs, MAX_REQUEST_TIMEOUT_MS);
        if (requestTimeoutMs > requestTimeoutMaxMs) {
            throw new IllegalArgumentException("requestTimeoutMs must be <= " + requestTimeoutMaxMs);
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
        feature = trimToNull(feature);
        if (feature == null) {
            feature = "generic";
        }
        concurrencyLimit = Math.max(1, positiveOrDefault(concurrencyLimit, 1));
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
