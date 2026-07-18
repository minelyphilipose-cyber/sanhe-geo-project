package com.huanjing.geo.module.dispatch.websearch.enums;

public enum AttemptStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    ABANDONED;

    public boolean canTransitionTo(AttemptStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == RUNNING || target == ABANDONED;
            case RUNNING -> target == SUCCEEDED || target == FAILED || target == ABANDONED;
            case SUCCEEDED, FAILED, ABANDONED -> false;
        };
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == ABANDONED;
    }
}
