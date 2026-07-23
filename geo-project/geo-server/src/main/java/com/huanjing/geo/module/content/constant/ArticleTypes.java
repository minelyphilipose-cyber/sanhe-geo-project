package com.huanjing.geo.module.content.constant;

import java.util.Map;
import java.util.Set;

public final class ArticleTypes {
    public static final String GENERAL_ARTICLE = "general_article";
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

    public static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(GENERAL_ARTICLE, "通用文章"),
            Map.entry(FAQ, "问答文章"),
            Map.entry(SCENARIO_CONTENT, "场景内容"),
            Map.entry(INDUSTRY_ARTICLE, "行业文章"),
            Map.entry(STAGE_ADVICE, "阶段建议"),
            Map.entry(BUYING_GUIDE, "选择指南"),
            Map.entry(COMPARISON, "对比评测"),
            Map.entry(COST_ANALYSIS, "费用解析"),
            Map.entry(PITFALL_GUIDE, "避坑指南"),
            Map.entry(SOCIAL_NOTE, "经验笔记"),
            Map.entry(NEWS_BRIEF, "资讯简讯"),
            Map.entry(FORUM_DISCUSSION, "讨论帖")
    );

    private static final Set<String> SUPPORTED_TYPES = Set.copyOf(LABELS.keySet());

    private static final Set<String> GENERATED_CONTENT_TYPES = Set.of(
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

    public static boolean isGeneratedContentType(String articleType) {
        return GENERATED_CONTENT_TYPES.contains(articleType);
    }

    public static String label(String articleType) {
        if (articleType == null) {
            return null;
        }
        return LABELS.getOrDefault(articleType, articleType);
    }
}
