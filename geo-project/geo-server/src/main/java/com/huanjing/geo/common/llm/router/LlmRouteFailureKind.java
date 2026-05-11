package com.huanjing.geo.common.llm.router;

public enum LlmRouteFailureKind {
    NO_CANDIDATE,
    ALL_RATE_LIMITED,
    ALL_PERMIT_BUSY,
    ALL_CIRCUIT_OPEN,
    ALL_FAILED,
    INTERRUPTED
}
