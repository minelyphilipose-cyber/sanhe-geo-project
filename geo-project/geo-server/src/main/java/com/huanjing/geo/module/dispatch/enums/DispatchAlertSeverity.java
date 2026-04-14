package com.huanjing.geo.module.dispatch.enums;

public enum DispatchAlertSeverity {
    INFO("info"),
    WARN("warn"),
    ERROR("error"),
    CRITICAL("critical");

    private final String value;

    DispatchAlertSeverity(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
