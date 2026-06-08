package com.huanjing.geo.module.presale.ruleengine;

/**
 * 规则编码常量。与 V64 种子数据和 P1·D README 的 rule_code 清单一一对应。
 *
 * <p>每个常量对应一个 {@link EvidenceDataBuilder} 实现。若新增规则(如 P1·E 之后的扩展),
 * 必须同时在此新增常量、新增 Builder、在 V64+ SQL 里 INSERT 规则行,三者缺一不可。</p>
 */
public final class RuleCodes {

    private RuleCodes() {
    }

    // 基础设施(2)
    public static final String RULE_COVERAGE_LOW_RECOMMEND = "RULE_COVERAGE_LOW_RECOMMEND";
    public static final String RULE_BRAND_AWARENESS_LOW    = "RULE_BRAND_AWARENESS_LOW";
    public static final String RULE_RECOMMENDATION_ABSENT  = "RULE_RECOMMENDATION_ABSENT";

    // 内容建设(3)
    public static final String RULE_COMPARE_GAP            = "RULE_COMPARE_GAP";
    public static final String RULE_PLATFORM_IMBALANCE     = "RULE_PLATFORM_IMBALANCE";
    public static final String RULE_SCENE_MISS_HIGH_VALUE  = "RULE_SCENE_MISS_HIGH_VALUE";
    public static final String RULE_COMPETITOR_PRESENT_CLIENT_ABSENT = "RULE_COMPETITOR_PRESENT_CLIENT_ABSENT";
    public static final String RULE_NATURAL_RECO_WEAK_BRAND_KNOWN = "RULE_NATURAL_RECO_WEAK_BRAND_KNOWN";
    public static final String RULE_HIGH_VALUE_RECO_GAP = "RULE_HIGH_VALUE_RECO_GAP";

    // 关系建设(2)
    public static final String RULE_NEGATIVE_EVIDENCE      = "RULE_NEGATIVE_EVIDENCE";
    public static final String RULE_LOW_SENTIMENT_SCORE    = "RULE_LOW_SENTIMENT_SCORE";
    public static final String RULE_BRAND_SENTIMENT_SAMPLE_THIN = "RULE_BRAND_SENTIMENT_SAMPLE_THIN";

    // 平台扩展(3)
    public static final String RULE_PLATFORM_COVERAGE_NARROW = "RULE_PLATFORM_COVERAGE_NARROW";
    public static final String RULE_PLATFORM_COUNT_LOW     = "RULE_PLATFORM_COUNT_LOW";
    public static final String RULE_SINGLE_PLATFORM_DOMINANT = "RULE_SINGLE_PLATFORM_DOMINANT";
    public static final String RULE_PLATFORM_NEW_CUSTOMER_BLANK = "RULE_PLATFORM_NEW_CUSTOMER_BLANK";
    public static final String RULE_PLATFORM_DEPTH_SHALLOW = "RULE_PLATFORM_DEPTH_SHALLOW";
    public static final String RULE_LONG_TAIL_SCENE_GAP = "RULE_LONG_TAIL_SCENE_GAP";
    public static final String RULE_CONTENT_CONSISTENCY_CHECK = "RULE_CONTENT_CONSISTENCY_CHECK";
    public static final String RULE_PERIODIC_RETEST_MONITORING = "RULE_PERIODIC_RETEST_MONITORING";
}
