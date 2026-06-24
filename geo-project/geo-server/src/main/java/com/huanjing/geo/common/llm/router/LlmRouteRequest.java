package com.huanjing.geo.common.llm.router;

import com.huanjing.geo.common.llm.LlmRoutingStrategy;
import com.huanjing.geo.common.llm.measurement.LlmCallMeasurementContext;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;

import java.util.List;

public record LlmRouteRequest(String feature,
                              String systemPrompt,
                              String userPrompt,
                              Double temperature,
                              Integer connectTimeoutMs,
                              Integer requestTimeoutMs,
                              Integer requestTimeoutMaxMs,
                              Integer maxRetry,
                              Integer maxTokens,
                              boolean normalizeJsonOutput,
                              int tokenCost,
                              int cursor,
                              List<AiPlatformConfig> platformConfigs,
                              boolean waitForPermit,
                              LlmRoutingStrategy routingStrategy,
                              LlmCallMeasurementContext measurementContext) {

    public LlmRouteRequest(String feature,
                           String systemPrompt,
                           String userPrompt,
                           Double temperature,
                           Integer connectTimeoutMs,
                           Integer requestTimeoutMs,
                           Integer requestTimeoutMaxMs,
                           Integer maxRetry,
                           Integer maxTokens,
                           boolean normalizeJsonOutput,
                           int tokenCost,
                           int cursor,
                           List<AiPlatformConfig> platformConfigs,
                           boolean waitForPermit) {
        this(feature, systemPrompt, userPrompt, temperature, connectTimeoutMs, requestTimeoutMs, requestTimeoutMaxMs,
                maxRetry, maxTokens, normalizeJsonOutput, tokenCost, cursor, platformConfigs, waitForPermit,
                LlmRoutingStrategy.FAILOVER, LlmCallMeasurementContext.empty());
    }

    public LlmRouteRequest(String feature,
                           String systemPrompt,
                           String userPrompt,
                           Double temperature,
                           Integer connectTimeoutMs,
                           Integer requestTimeoutMs,
                           Integer requestTimeoutMaxMs,
                           Integer maxRetry,
                           Integer maxTokens,
                           boolean normalizeJsonOutput,
                           int tokenCost,
                           int cursor,
                           List<AiPlatformConfig> platformConfigs) {
        this(feature, systemPrompt, userPrompt, temperature, connectTimeoutMs, requestTimeoutMs, requestTimeoutMaxMs,
                maxRetry, maxTokens, normalizeJsonOutput, tokenCost, cursor, platformConfigs, false,
                LlmRoutingStrategy.FAILOVER, LlmCallMeasurementContext.empty());
    }

    public LlmRouteRequest(String feature,
                           String systemPrompt,
                           String userPrompt,
                           Double temperature,
                           Integer connectTimeoutMs,
                           Integer requestTimeoutMs,
                           Integer requestTimeoutMaxMs,
                           Integer maxRetry,
                           Integer maxTokens,
                           boolean normalizeJsonOutput,
                           int tokenCost,
                           int cursor,
                           List<AiPlatformConfig> platformConfigs,
                           boolean waitForPermit,
                           LlmRoutingStrategy routingStrategy) {
        this(feature, systemPrompt, userPrompt, temperature, connectTimeoutMs, requestTimeoutMs, requestTimeoutMaxMs,
                maxRetry, maxTokens, normalizeJsonOutput, tokenCost, cursor, platformConfigs, waitForPermit,
                routingStrategy, LlmCallMeasurementContext.empty());
    }

    public LlmRouteRequest withMeasurementContext(LlmCallMeasurementContext context) {
        return new LlmRouteRequest(
                feature,
                systemPrompt,
                userPrompt,
                temperature,
                connectTimeoutMs,
                requestTimeoutMs,
                requestTimeoutMaxMs,
                maxRetry,
                maxTokens,
                normalizeJsonOutput,
                tokenCost,
                cursor,
                platformConfigs,
                waitForPermit,
                routingStrategy,
                context
        );
    }

    public LlmRouteRequest {
        feature = feature == null || feature.isBlank() ? LlmFeature.GENERIC : feature.trim();
        userPrompt = userPrompt == null ? "" : userPrompt;
        tokenCost = Math.max(tokenCost, 1);
        cursor = Math.max(cursor, 0);
        platformConfigs = platformConfigs == null ? List.of() : List.copyOf(platformConfigs);
        routingStrategy = routingStrategy == null ? LlmRoutingStrategy.FAILOVER : routingStrategy;
        measurementContext = measurementContext == null ? LlmCallMeasurementContext.empty() : measurementContext;
    }
}
