package com.huanjing.geo.common.llm.router;

public class LlmRouteException extends RuntimeException {
    private final LlmRouteFailureKind failureKind;
    private final int requestCount;

    public LlmRouteException(LlmRouteFailureKind failureKind, String message, int requestCount, Throwable cause) {
        super(message, cause);
        this.failureKind = failureKind;
        this.requestCount = requestCount;
    }

    public LlmRouteFailureKind failureKind() {
        return failureKind;
    }

    public int requestCount() {
        return requestCount;
    }
}
