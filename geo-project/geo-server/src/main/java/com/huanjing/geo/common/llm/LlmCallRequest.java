package com.huanjing.geo.common.llm;

import com.huanjing.geo.common.llm.router.LlmRouteRequest;
import com.huanjing.geo.common.llm.measurement.LlmCallMeasurementContext;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import org.springframework.util.StringUtils;

public record LlmCallRequest(LlmGovernanceStack governanceStack,
                             LlmRoutingStrategy routingStrategy,
                             LlmWaitSemantics waitSemantics,
                             String feature,
                             String prompt,
                             LlmModelConfig modelConfig,
                             LlmRouteRequest routeRequest,
                             AiPlatformConfig legacyPlatformConfig,
                             String legacyPlatformCode,
                             String legacyPlatformName,
                             String legacyChannel,
                             String legacyApiUrl,
                             String legacyModelId,
                             String legacyApiKey,
                             String legacySystemPrompt,
                             String legacyUserPrompt,
                             double legacyTemperature,
                             int legacyTokenCost,
                             int legacyConnectTimeoutMs,
                             int legacyRequestTimeoutMs,
                             int requestCount,
                             LlmCallMeasurementContext measurementContext) {

    public static LlmCallRequest direct(String prompt, LlmModelConfig modelConfig) {
        return new LlmCallRequest(
                LlmGovernanceStack.GATEWAY,
                LlmRoutingStrategy.PINNED,
                modelConfig != null && modelConfig.useExecutionGateway() ? LlmWaitSemantics.BLOCKING : LlmWaitSemantics.FAST_FAIL,
                modelConfig == null ? null : modelConfig.feature(),
                prompt,
                modelConfig,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0D,
                1,
                0,
                0,
                1,
                LlmCallMeasurementContext.empty()
        );
    }

    public static LlmCallRequest routed(LlmRouteRequest routeRequest) {
        return new LlmCallRequest(
                LlmGovernanceStack.GATEWAY,
                routeRequest == null ? LlmRoutingStrategy.FAILOVER : routeRequest.routingStrategy(),
                routeRequest != null && routeRequest.waitForPermit() ? LlmWaitSemantics.BLOCKING : LlmWaitSemantics.FAST_FAIL,
                routeRequest == null ? null : routeRequest.feature(),
                null,
                null,
                routeRequest,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0D,
                1,
                0,
                0,
                1,
                LlmCallMeasurementContext.empty()
        );
    }

    public static LlmCallRequest legacy(AiPlatformConfig platformConfig,
                                        String platformCode,
                                        String platformName,
                                        String channel,
                                        String apiUrl,
                                        String modelId,
                                        String apiKey,
                                        String systemPrompt,
                                        String userPrompt,
                                        double temperature,
                                        int tokenCost,
                                        int connectTimeoutMs,
                                        int requestTimeoutMs,
                                        String feature,
                                        int requestCount) {
        return new LlmCallRequest(
                LlmGovernanceStack.LEGACY_LIMITER,
                LlmRoutingStrategy.LEGACY_DISPATCH_ROUTING,
                LlmWaitSemantics.BLOCKING,
                feature,
                null,
                null,
                null,
                platformConfig,
                platformCode,
                platformName,
                channel,
                apiUrl,
                modelId,
                apiKey,
                systemPrompt,
                userPrompt,
                temperature,
                tokenCost,
                connectTimeoutMs,
                requestTimeoutMs,
                Math.max(requestCount, 1),
                LlmCallMeasurementContext.empty()
        );
    }

    public LlmCallRequest withMeasurementContext(LlmCallMeasurementContext context) {
        return new LlmCallRequest(
                governanceStack,
                routingStrategy,
                waitSemantics,
                feature,
                prompt,
                modelConfig,
                routeRequest,
                legacyPlatformConfig,
                legacyPlatformCode,
                legacyPlatformName,
                legacyChannel,
                legacyApiUrl,
                legacyModelId,
                legacyApiKey,
                legacySystemPrompt,
                legacyUserPrompt,
                legacyTemperature,
                legacyTokenCost,
                legacyConnectTimeoutMs,
                legacyRequestTimeoutMs,
                requestCount,
                context
        );
    }

    public LlmCallRequest {
        governanceStack = governanceStack == null ? LlmGovernanceStack.GATEWAY : governanceStack;
        routingStrategy = routingStrategy == null ? LlmRoutingStrategy.FAILOVER : routingStrategy;
        waitSemantics = waitSemantics == null ? LlmWaitSemantics.CUSTOM : waitSemantics;
        feature = StringUtils.hasText(feature) ? feature.trim() : "generic";
        legacyTokenCost = Math.max(legacyTokenCost, 1);
        requestCount = Math.max(requestCount, 1);
        measurementContext = measurementContext == null ? LlmCallMeasurementContext.empty() : measurementContext;
    }
}
