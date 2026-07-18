package com.huanjing.geo.module.dispatch.websearch.enums;

public enum ErrorCategory {
    NETWORK,
    TIMEOUT,
    RATE_LIMIT,
    SERVER_ERROR,
    STREAM_INTERRUPTED,
    AUTHENTICATION,
    BALANCE,
    PERMISSION,
    MODEL_UNAVAILABLE,
    UNSUPPORTED_PARAMETER,
    SAFETY_REJECTION,
    INVALID_REQUEST,
    PARSE_ERROR,
    WORKER_INTERRUPTED,
    INTERNAL_ERROR;

    public boolean retryable() {
        return this == NETWORK
                || this == TIMEOUT
                || this == RATE_LIMIT
                || this == SERVER_ERROR
                || this == STREAM_INTERRUPTED;
    }
}
