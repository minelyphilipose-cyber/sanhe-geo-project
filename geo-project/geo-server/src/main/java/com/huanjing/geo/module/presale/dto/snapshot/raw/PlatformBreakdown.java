package com.huanjing.geo.module.presale.dto.snapshot.raw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个 AI 平台的测试事实数据(L1)。
 * <p>Schema v1.2 $.raw_snapshot.platform_breakdown[]</p>
 * <p>9 字段全部 required(除 avg_ranking 可 null)。</p>
 * <p>
 * 注意:{@code sentiment_distribution} 只统计<b>第一轮</b> 25 prompt × 11 平台 的 275 次结果,
 * 与 {@code L1.sentiment_detail.*_count}(两轮合计)不同源,不应交叉校验。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlatformBreakdown {

    /** 平台 code(对应 ai_platform_config.platform_code)。 */
    @JsonProperty("platform_code")
    private String platformCode;

    /** 平台展示名。 */
    @JsonProperty("platform_name")
    private String platformName;

    /** 该平台第一轮测试条数(典型 25)。 */
    @JsonProperty("total_tests")
    private Integer totalTests;

    /** 被提及次数。 */
    @JsonProperty("mention_count")
    private Integer mentionCount;

    /** 提及率百分比(0-100),Double。 */
    @JsonProperty("mention_rate")
    private Double mentionRate;

    /** 平均排名,未被提及时为 null。 */
    @JsonProperty("avg_ranking")
    private Double avgRanking;

    /** 首推次数(排名=1)。 */
    @JsonProperty("primary_recommendation_count")
    private Integer primaryRecommendationCount;

    /** 情感分布(仅第一轮 275 次的统计)。 */
    @JsonProperty("sentiment_distribution")
    private SentimentDistribution sentimentDistribution;

    /** 该平台是否降级(成功率 < 50%)。 */
    @JsonProperty("is_degraded")
    private Boolean isDegraded;

    /** positive/neutral/negative 计数。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SentimentDistribution {
        private Integer positive;
        private Integer neutral;
        private Integer negative;
    }
}
