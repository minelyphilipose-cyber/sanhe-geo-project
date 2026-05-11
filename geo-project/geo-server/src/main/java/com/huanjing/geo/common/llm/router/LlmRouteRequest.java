package com.huanjing.geo.common.llm.router;

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
                              List<AiPlatformConfig> platformConfigs) {

    public LlmRouteRequest {
        feature = feature == null || feature.isBlank() ? LlmFeature.GENERIC : feature.trim();
        userPrompt = userPrompt == null ? "" : userPrompt;
        tokenCost = Math.max(tokenCost, 1);
        cursor = Math.max(cursor, 0);
        platformConfigs = platformConfigs == null ? List.of() : List.copyOf(platformConfigs);
    }
}
