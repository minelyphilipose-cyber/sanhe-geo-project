package com.huanjing.geo.module.dispatch.enums;

public enum DispatchTaskStatus {
    PENDING("pending"),
    RUNNING("running"),
    RETRY_PENDING("retry_pending"),
    COMPLETED("completed"),
    FAILED("failed"),
    DEAD_LETTER("dead_letter"),
    CANCELLED("cancelled");

    private final String value;

    DispatchTaskStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
