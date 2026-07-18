package com.huanjing.geo.module.dispatch.websearch;

import com.huanjing.geo.module.dispatch.websearch.enums.TriggerType;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;

import java.util.Set;

public record WebSearchPollCommand(Long pollResultId,
                                   Long shardItemId,
                                   Long dispatchTaskId,
                                   Long projectId,
                                   Long keywordResultId,
                                   String question,
                                   String systemPrompt,
                                   AiPlatformConfig platform,
                                   TriggerType triggerType,
                                   int connectTimeoutMs,
                                   int requestTimeoutMs,
                                   Set<String> brandNames) {
    public WebSearchPollCommand {
        brandNames = brandNames == null ? Set.of() : Set.copyOf(brandNames);
    }
}
