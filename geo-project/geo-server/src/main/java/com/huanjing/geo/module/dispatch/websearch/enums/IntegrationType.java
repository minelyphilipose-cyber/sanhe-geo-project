package com.huanjing.geo.module.dispatch.websearch.enums;

public enum IntegrationType {
    OPENAI_CHAT,
    VOLCENGINE_RESPONSES_WEB,
    DASHSCOPE_NATIVE_WEB,
    TENCENT_TOKENHUB_RESPONSES_WEB,
    QIANFAN_ERNIE_CHAT_WEB,
    ZHIPU_CHAT_WEB;

    public boolean isWebSearch() {
        return switch (this) {
            case VOLCENGINE_RESPONSES_WEB,
                 DASHSCOPE_NATIVE_WEB,
                 TENCENT_TOKENHUB_RESPONSES_WEB,
                 QIANFAN_ERNIE_CHAT_WEB,
                 ZHIPU_CHAT_WEB -> true;
            case OPENAI_CHAT -> false;
        };
    }
}
