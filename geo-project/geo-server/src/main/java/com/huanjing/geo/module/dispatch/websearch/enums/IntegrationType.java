package com.huanjing.geo.module.dispatch.websearch.enums;

public enum IntegrationType {
    OPENAI_CHAT,
    VOLCENGINE_RESPONSES_WEB,
    DASHSCOPE_NATIVE_WEB,
    TENCENT_TOKENHUB_RESPONSES_WEB;

    public boolean isWebSearch() {
        return this != OPENAI_CHAT;
    }
}
