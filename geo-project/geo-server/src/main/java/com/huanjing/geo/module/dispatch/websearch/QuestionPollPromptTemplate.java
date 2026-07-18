package com.huanjing.geo.module.dispatch.websearch;

public final class QuestionPollPromptTemplate {

    public static final String VERSION = "poll-template-v1";
    public static final String SYSTEM_PROMPT =
            "你是GEO监测助手。必须优先联网核查，并在回答中保留可验证的来源引用。";

    private QuestionPollPromptTemplate() {
    }
}
