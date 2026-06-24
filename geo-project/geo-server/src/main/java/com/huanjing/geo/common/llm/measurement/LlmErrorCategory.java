package com.huanjing.geo.common.llm.measurement;

public enum LlmErrorCategory {
    PERMIT_BUSY,
    INTERNAL_RATE_LIMITED,
    PLATFORM_429,
    HTTP_5XX,
    TIMEOUT,
    CONFIG_ERROR,
    BUSINESS_NON_RETRYABLE,
    INVOKE_FAILED
}
