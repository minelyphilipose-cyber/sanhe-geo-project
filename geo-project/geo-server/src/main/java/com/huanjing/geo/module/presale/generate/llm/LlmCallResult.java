package com.huanjing.geo.module.presale.generate.llm;

/**
 * LLM 逻辑调用结果(一条逻辑调用,不含每次物理重试明细)。
 *
 * @param rawResponse      模型原始响应文本
 * @param promptTokens     输入 token
 * @param completionTokens 输出 token
 * @param durationMs       调用耗时(ms)
 * @param retryCount       重试次数;0 表示首次成功/失败
 * @param callStatus       逻辑调用状态
 */
public record LlmCallResult(String rawResponse,
                            Integer promptTokens,
                            Integer completionTokens,
                            Long durationMs,
                            Integer retryCount,
                            CallStatus callStatus) {

    public boolean isRetriedSuccess() {
        return callStatus == CallStatus.SUCCESS
                && retryCount != null
                && retryCount > 0;
    }
}

