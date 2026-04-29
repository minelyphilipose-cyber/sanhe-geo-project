package com.huanjing.geo.module.content.service.adapter;

public class AuthCheckResult {
    private final boolean success;
    private final String failureKind;
    private final String message;

    private AuthCheckResult(boolean success, String failureKind, String message) {
        this.success = success;
        this.failureKind = failureKind;
        this.message = message;
    }

    public static AuthCheckResult success() {
        return new AuthCheckResult(true, null, null);
    }

    public static AuthCheckResult failure(String kind, String message) {
        return new AuthCheckResult(false, kind, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getFailureKind() {
        return failureKind;
    }

    public String getMessage() {
        return message;
    }
}
