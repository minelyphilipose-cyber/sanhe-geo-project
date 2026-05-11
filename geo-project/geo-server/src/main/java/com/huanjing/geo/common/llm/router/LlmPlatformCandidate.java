package com.huanjing.geo.common.llm.router;

import com.huanjing.geo.module.system.entity.AiPlatformConfig;

public record LlmPlatformCandidate(AiPlatformConfig platformConfig,
                                   String platformCode,
                                   String platformName,
                                   String channel,
                                   String apiUrl,
                                   String modelId,
                                   String modelName,
                                   String apiKey) {
}
