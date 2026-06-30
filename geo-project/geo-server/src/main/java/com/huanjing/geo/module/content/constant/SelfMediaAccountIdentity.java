package com.huanjing.geo.module.content.constant;

import org.springframework.util.StringUtils;

public final class SelfMediaAccountIdentity {
    public static final String PERSONAL = "personal";
    public static final String ENTERPRISE = "enterprise";

    private SelfMediaAccountIdentity() {
    }

    public static String normalize(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase();
        if (ENTERPRISE.equals(normalized)) {
            return ENTERPRISE;
        }
        if (PERSONAL.equals(normalized)) {
            return PERSONAL;
        }
        return fallback;
    }
}
