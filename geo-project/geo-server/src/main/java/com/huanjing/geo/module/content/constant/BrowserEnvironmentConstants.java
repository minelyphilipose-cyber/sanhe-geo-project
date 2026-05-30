package com.huanjing.geo.module.content.constant;

import java.util.Set;

public final class BrowserEnvironmentConstants {
    public static final String PROVIDER_ADSPOWER = "adspower";

    public static final String ENV_STATUS_ACTIVE = "active";
    public static final String ENV_STATUS_DISABLED = "disabled";
    public static final String ENV_STATUS_DELETED = "deleted";

    public static final String LOGIN_UNKNOWN = "unknown";
    public static final String LOGIN_LOGGED_IN = "logged_in";
    public static final String LOGIN_REQUIRED = "login_required";
    public static final String LOGIN_MISMATCH = "mismatch";
    public static final String LOGIN_EXPIRED = "expired";
    public static final String LOGIN_ERROR = "error";

    public static final String ERR_ENVIRONMENT_DISABLED = "ENVIRONMENT_DISABLED";
    public static final String ERR_ENVIRONMENT_LOGIN_REQUIRED = "ENVIRONMENT_LOGIN_REQUIRED";
    public static final String ERR_ENVIRONMENT_ACCOUNT_MISMATCH = "ENVIRONMENT_ACCOUNT_MISMATCH";
    public static final String ERR_IDENTITY_EXPECTATION_MISSING = "IDENTITY_EXPECTATION_MISSING";

    public static final Set<String> LOGIN_STATUSES = Set.of(
            LOGIN_UNKNOWN,
            LOGIN_LOGGED_IN,
            LOGIN_REQUIRED,
            LOGIN_MISMATCH,
            LOGIN_EXPIRED,
            LOGIN_ERROR
    );

    private BrowserEnvironmentConstants() {
    }
}
