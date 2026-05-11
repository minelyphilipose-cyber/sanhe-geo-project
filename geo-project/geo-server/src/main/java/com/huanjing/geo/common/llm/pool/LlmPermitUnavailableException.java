package com.huanjing.geo.common.llm.pool;

public class LlmPermitUnavailableException extends RuntimeException {
    private final LlmPermitScope scope;
    private final String platformCode;

    public LlmPermitUnavailableException(LlmPermitScope scope, String platformCode) {
        super("LLM permit unavailable: " + scope + (platformCode == null ? "" : ":" + platformCode));
        this.scope = scope;
        this.platformCode = platformCode;
    }

    public LlmPermitScope getScope() {
        return scope;
    }

    public String getPlatformCode() {
        return platformCode;
    }
}
