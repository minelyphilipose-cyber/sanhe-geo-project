package com.huanjing.geo.module.content.constant;

import java.util.Set;

public final class ArticleTypes {
    public static final String FAQ = "faq";
    public static final String SCENARIO_CONTENT = "scenario_content";
    public static final String INDUSTRY_ARTICLE = "industry_article";
    public static final String STAGE_ADVICE = "stage_advice";

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            FAQ,
            SCENARIO_CONTENT,
            INDUSTRY_ARTICLE,
            STAGE_ADVICE
    );

    private ArticleTypes() {
    }

    public static boolean isSupported(String articleType) {
        return SUPPORTED_TYPES.contains(articleType);
    }
}
