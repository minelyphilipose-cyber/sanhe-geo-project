package com.huanjing.geo.module.dispatch.enums;

import java.util.Arrays;

public enum DispatchTaskType {
    @Deprecated
    QUARTERLY_REPORT(0, true),
    @Deprecated
    MONTHLY_REPORT(1, true),
    BRAND_STATEMENT_GENERATION(1, true),
    BIWEEKLY_REPORT(2, true),
    QUESTION_POLL(3, true),
    @Deprecated
    BI_DAILY_POLL(3, true),
    CONTENT_GENERATION(3, true),
    CUSTOMER_EXPIRE_CHECK(4, false);

    private final int priorityLevel;
    private final boolean queueTask;

    DispatchTaskType(int priorityLevel, boolean queueTask) {
        this.priorityLevel = priorityLevel;
        this.queueTask = queueTask;
    }

    public int getPriorityLevel() {
        return priorityLevel;
    }

    public boolean isQueueTask() {
        return queueTask;
    }

    public static DispatchTaskType fromValue(String value) {
        if ("BI_DAILY_POLL".equalsIgnoreCase(value)) {
            return QUESTION_POLL;
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown task type: " + value));
    }

    public static boolean isQuestionPoll(String value) {
        return "QUESTION_POLL".equalsIgnoreCase(value) || "BI_DAILY_POLL".equalsIgnoreCase(value);
    }

    public static boolean isRetiredReport(String value) {
        return MONTHLY_REPORT.name().equalsIgnoreCase(value)
                || QUARTERLY_REPORT.name().equalsIgnoreCase(value);
    }
}
