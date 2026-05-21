package com.huanjing.geo.module.content.constant;

import java.util.Set;

public final class ArticleTypes {
    public static final String FAQ = "faq";
    public static final String SCENARIO_CONTENT = "scenario_content";
    public static final String INDUSTRY_ARTICLE = "industry_article";
    public static final String STAGE_ADVICE = "stage_advice";
    public static final String BUYING_GUIDE = "buying_guide";
    public static final String COMPARISON = "comparison";
    public static final String COST_ANALYSIS = "cost_analysis";
    public static final String PITFALL_GUIDE = "pitfall_guide";
    public static final String SOCIAL_NOTE = "social_note";
    public static final String NEWS_BRIEF = "news_brief";
    public static final String FORUM_DISCUSSION = "forum_discussion";

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            FAQ,
            SCENARIO_CONTENT,
            INDUSTRY_ARTICLE,
            STAGE_ADVICE,
            BUYING_GUIDE,
            COMPARISON,
            COST_ANALYSIS,
            PITFALL_GUIDE,
            SOCIAL_NOTE,
            NEWS_BRIEF,
            FORUM_DISCUSSION
    );

    private ArticleTypes() {
    }

    public static boolean isSupported(String articleType) {
        return SUPPORTED_TYPES.contains(articleType);
    }
}
