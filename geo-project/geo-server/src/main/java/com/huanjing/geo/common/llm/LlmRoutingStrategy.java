package com.huanjing.geo.common.llm;

public enum LlmRoutingStrategy {
    FAILOVER,
    PINNED,
    CANDIDATE_LIST,
    LEGACY_DISPATCH_ROUTING
}
