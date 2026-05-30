package com.huanjing.geo.module.extension;

import java.util.Set;

public final class ExtensionTaskErrorCode {
    public static final String COOKIE_MISSING = "COOKIE_MISSING";
    public static final String LOGIN_REQUIRED = "LOGIN_REQUIRED";
    public static final String PAGE_CHANGED = "PAGE_CHANGED";
    public static final String FILL_FAILED = "FILL_FAILED";
    public static final String PUBLISH_BUTTON_NOT_FOUND = "PUBLISH_BUTTON_NOT_FOUND";
    public static final String TASK_EXPIRED = "TASK_EXPIRED";
    public static final String UNKNOWN = "UNKNOWN";

    public static final Set<String> SUPPORTED = Set.of(
            COOKIE_MISSING,
            LOGIN_REQUIRED,
            PAGE_CHANGED,
            FILL_FAILED,
            PUBLISH_BUTTON_NOT_FOUND,
            TASK_EXPIRED,
            UNKNOWN
    );

    private ExtensionTaskErrorCode() {
    }
}
