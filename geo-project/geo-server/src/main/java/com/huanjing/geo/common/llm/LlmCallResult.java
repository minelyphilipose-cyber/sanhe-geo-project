package com.huanjing.geo.common.llm;

import com.huanjing.geo.common.llm.router.LlmRouteResult;

public record LlmCallResult(LlmInvokeResult invokeResult,
                            LlmRouteResult routeResult,
                            String rawResponseText,
                            String platformCode,
                            String platformName,
                            String channel,
                            String modelId,
                            String modelName,
                            long durationMs,
                            int requestCount) {

    public static LlmCallResult direct(LlmInvokeResult result) {
        return new LlmCallResult(
                result,
                null,
                result.responseText(),
                result.platformCode(),
                result.platformName(),
                null,
                result.modelId(),
                result.modelName(),
                result.durationMs() == null ? 0L : result.durationMs(),
                1
        );
    }

    public static LlmCallResult routed(LlmRouteResult result) {
        return new LlmCallResult(
                result.invokeResult(),
                result,
                result.responseText(),
                result.platformCode(),
                result.platformName(),
                result.channel(),
                result.modelId(),
                result.modelName(),
                result.durationMs(),
                result.requestCount()
        );
    }

    public static LlmCallResult raw(String responseText,
                                    String platformCode,
                                    String platformName,
                                    String channel,
                                    String modelId,
                                    long durationMs,
                                    int requestCount) {
        return new LlmCallResult(
                null,
                null,
                responseText,
                platformCode,
                platformName,
                channel,
                modelId,
                null,
                durationMs,
                requestCount
        );
    }
}
