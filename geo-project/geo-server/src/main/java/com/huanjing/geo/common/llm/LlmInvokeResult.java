package com.huanjing.geo.common.llm;

public record LlmInvokeResult(String responseText,
                              Integer promptTokens,
                              Integer completionTokens,
                              Long durationMs,
                              Integer retryCount,
                              LlmCallStatus callStatus,
                              String platformCode,
                              String platformName,
                              String modelId,
                              String modelName) {

    public boolean isRetriedSuccess() {
        return callStatus == LlmCallStatus.SUCCESS
                && retryCount != null
                && retryCount > 0;
    }
}
