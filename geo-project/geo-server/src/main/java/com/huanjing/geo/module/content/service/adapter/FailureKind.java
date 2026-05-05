package com.huanjing.geo.module.content.service.adapter;

public final class FailureKind {
    public static final String AUTH = "AUTH";
    public static final String AUTH_EXPIRED = "AUTH_EXPIRED";
    public static final String PERMISSION = "PERMISSION";
    public static final String RATE_LIMIT = "RATE_LIMIT";
    public static final String VALIDATION = "VALIDATION";
    public static final String PLATFORM = "PLATFORM";
    public static final String CLIENT_ERROR = "CLIENT_ERROR";
    public static final String SERVER_ERROR = "SERVER_ERROR";
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String UNKNOWN = "UNKNOWN";

    private FailureKind() {
    }
}
