package com.huanjing.geo.module.content.constant;

import org.springframework.util.StringUtils;

import java.util.Set;

public final class TemplatePerspectiveCodes {
    public static final String CUSTOMER = "customer";
    public static final String INDUSTRY_NEUTRAL = "industry_neutral";
    public static final String REVIEW_RECOMMEND = "review_recommend";
    public static final String CHANNEL_SUB_ALL = "_ALL_";

    private static final Set<String> BUILTIN_CODES = Set.of(CUSTOMER, INDUSTRY_NEUTRAL, REVIEW_RECOMMEND);

    private TemplatePerspectiveCodes() {
    }

    public static String normalize(String code) {
        return StringUtils.hasText(code) ? code.trim() : CUSTOMER;
    }

    public static boolean isSpecial(String code) {
        String normalized = normalize(code);
        return !CUSTOMER.equals(normalized);
    }

    public static boolean isBuiltin(String code) {
        return BUILTIN_CODES.contains(normalize(code));
    }
}
