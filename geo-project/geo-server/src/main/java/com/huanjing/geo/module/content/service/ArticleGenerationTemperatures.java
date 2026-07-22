package com.huanjing.geo.module.content.service;

/**
 * Centralizes article generation temperatures so routed and direct calls share
 * the same effective value.
 */
public final class ArticleGenerationTemperatures {

    public static final double DEFAULT = 0.4D;
    public static final double V2_STANDARD = 0.5D;

    private ArticleGenerationTemperatures() {
    }

    public static double resolve(boolean v2, boolean specialIndustry) {
        return v2 && !specialIndustry ? V2_STANDARD : DEFAULT;
    }
}
