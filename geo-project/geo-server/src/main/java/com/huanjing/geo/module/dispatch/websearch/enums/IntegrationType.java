package com.huanjing.geo.module.dispatch.websearch.enums;

public enum IntegrationType {
    OPENAI_CHAT,
    VOLCENGINE_RESPONSES_WEB,
    DASHSCOPE_NATIVE_WEB,
    TENCENT_TOKENHUB_RESPONSES_WEB,
    QIANFAN_ERNIE_CHAT_WEB,
    /**
     * Kept only so databases that already applied V327 can still deserialize
     * the retired configuration. No runtime adapter is registered for it.
     */
    @Deprecated
    ZHIPU_CHAT_WEB;

    public boolean isWebSearch() {
        return switch (this) {
            case VOLCENGINE_RESPONSES_WEB,
                 DASHSCOPE_NATIVE_WEB,
                 TENCENT_TOKENHUB_RESPONSES_WEB,
                 QIANFAN_ERNIE_CHAT_WEB -> true;
            case OPENAI_CHAT, ZHIPU_CHAT_WEB -> false;
        };
    }
}
