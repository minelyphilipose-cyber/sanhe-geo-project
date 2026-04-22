package com.huanjing.geo.module.presale.generate.llm;

/**
 * LLM 逻辑调用状态。
 *
 * <p>注意:RETRIED_SUCCESS 不作为独立状态持久化,由
 * {@link LlmCallResult#isRetriedSuccess()} 派生。</p>
 */
public enum CallStatus {
    SUCCESS,
    FAILED,
    SKIPPED_DEGRADED
}

