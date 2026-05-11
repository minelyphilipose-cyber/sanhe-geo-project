package com.huanjing.geo.common.llm.router;

import com.huanjing.geo.common.llm.LlmInvokeResult;

public record LlmRouteResult(String platformCode,
                             String platformName,
                             String channel,
                             String modelId,
                             String modelName,
                             String responseText,
                             long durationMs,
                             int requestCount,
                             LlmInvokeResult invokeResult) {
}
