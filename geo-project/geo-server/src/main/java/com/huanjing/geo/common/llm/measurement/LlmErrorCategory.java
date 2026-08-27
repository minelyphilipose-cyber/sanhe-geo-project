package com.huanjing.geo.common.llm.measurement;

public enum LlmErrorCategory {
    PERMIT_BUSY,
    INTERNAL_RATE_LIMITED,
    PLATFORM_429,
    ACCOUNT_QUOTA_EXHAUSTED,
    AUTHENTICATION_FAILED,
    HTTP_5XX,
    TIMEOUT,
    EMPTY_RESPONSE,
    CONFIG_ERROR,
    BUSINESS_NON_RETRYABLE,
    INVOKE_FAILED
}
