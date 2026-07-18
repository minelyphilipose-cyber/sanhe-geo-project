package com.huanjing.geo.module.dispatch.websearch.enums;

public enum SearchStatus {
    NOT_CONFIRMED,
    TRIGGERED,
    EMPTY,
    NO_VALID_SOURCE,
    FAILED;

    public boolean searchActuallyExecuted() {
        return this == TRIGGERED || this == EMPTY || this == NO_VALID_SOURCE;
    }

    public boolean hasUsableSources() {
        return this == TRIGGERED;
    }
}
