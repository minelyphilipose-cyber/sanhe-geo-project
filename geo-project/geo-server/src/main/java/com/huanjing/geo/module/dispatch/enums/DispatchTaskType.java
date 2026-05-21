package com.huanjing.geo.module.dispatch.enums;

import java.util.Arrays;

public enum DispatchTaskType {
    QUARTERLY_REPORT(0, true),
    MONTHLY_REPORT(1, true),
    BRAND_STATEMENT_GENERATION(1, true),
    BIWEEKLY_REPORT(2, true),
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
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown task type: " + value));
    }
}
