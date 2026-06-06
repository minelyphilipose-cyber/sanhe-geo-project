package com.huanjing.geo.module.presale.dto.snapshot.computed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * L2 叙事画像。
 *
 * <p>画像由确定性规则生成,报告生成链路不得为该对象新增在线 LLM 调用。
 * L3 文案和前端展示只消费本对象,不重新判断叙事策略。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NarrativeProfile {

    /** 画像算法版本,仅用于诊断,不作为历史配置冻结依据。 */
    @JsonProperty("profile_version")
    private String profileVersion;

    /** 本次读取到的叙事配置版本,仅用于排查"哪版配置产出该报告"。 */
    @JsonProperty("config_version")
    private String configVersion;

    /** 品牌所处可见度档位。 */
    private Band band;

    /** 面向文案模板的语气标签。 */
    @JsonProperty("band_tone")
    private String bandTone;

    /** 主叙事原型,用于首条发现排序。 */
    @JsonProperty("archetype_primary")
    private Archetype archetypePrimary;

    /** 次级叙事原型。 */
    @JsonProperty("archetype_secondary")
    private List<Archetype> archetypeSecondary;

    /** 可渲染发现分层。 */
    @JsonProperty("finding_tiers")
    private List<FindingTier> findingTiers;

    /** 热力图总览句模式。 */
    @JsonProperty("heatmap_pattern")
    private HeatmapPattern heatmapPattern;

    /** 前端展示开关和指标选择。 */
    @JsonProperty("display_flags")
    private DisplayFlags displayFlags;

    /** 竞品页叙事策略,由推荐型高价值压制事实源确定。 */
    @JsonProperty("competitor_story")
    private CompetitorStory competitorStory;

    /** 行业词库是否走通用兜底。 */
    @JsonProperty("lexicon_fallback")
    private Boolean lexiconFallback;

    /** 校验失败时是否已降级为保守画像/模板。 */
    private Boolean fallback;

    /** fallback 诊断原因,不直接对客展示。 */
    @JsonProperty("fallback_reason")
    private String fallbackReason;

    /** 诊断字段,供排查使用,不得作为前端展示文案来源。 */
    private Map<String, Object> diagnostics;

    public enum Band {
        INVISIBLE,
        BEHIND,
        MIDDLE,
        STRONG,
        LEADER
    }

    public enum Archetype {
        NEGATIVE_PRESSURE,
        COMPETITOR_OVERTAKE,
        BRANDED_ONLY,
        DECISION_GAP,
        CHANNEL_BLIND,
        LEADER_WITH_HOLES
    }

    public enum FindingSource {
        RULE,
        DERIVED,
        STRENGTH
    }

    public enum FindingTierLevel {
        T1,
        T2,
        T3,
        STRENGTH
    }

    public enum HeatmapPattern {
        NEW_CUSTOMER_BLANK,
        RECO_UNSTABLE,
        RECO_EMERGING,
        BROAD_PRESENCE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FindingTier {
        private FindingSource source;
        private String code;
        @JsonProperty("dedupe_key")
        private String dedupeKey;
        private FindingTierLevel tier;
        private Integer priority;
        @JsonProperty("archetype")
        private Archetype archetype;
        @JsonProperty("primary_archetype_match")
        private Boolean primaryArchetypeMatch;
        @JsonProperty("evidence")
        private Map<String, Object> evidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DisplayFlags {
        @JsonProperty("show_negative_box")
        private Boolean showNegativeBox;
        @JsonProperty("show_advantage_box")
        private Boolean showAdvantageBox;
        @JsonProperty("comparison_metric")
        private ComparisonMetric comparisonMetric;
        @JsonProperty("show_radar_baseline_gap")
        private Boolean showRadarBaselineGap;
        @JsonProperty("hide_empty_blocks")
        private Boolean hideEmptyBlocks;
        @JsonProperty("allow_competitor_overtake_claim")
        private Boolean allowCompetitorOvertakeClaim;
    }

    public enum ComparisonMetric {
        MENTION_RATE,
        RECOMMENDATION_PRESENCE,
        COMPARISON_PREFERENCE
    }

    public enum CompetitorStoryTier {
        T1,
        T2,
        T3,
        T4
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompetitorStory {
        private CompetitorStoryTier tier;
        private String title;
        @JsonProperty("landing_copy")
        private String landingCopy;
        @JsonProperty("suppressed_scene_count")
        private Integer suppressedSceneCount;
        @JsonProperty("hv_reco_total")
        private Integer hvRecoTotal;
        @JsonProperty("client_absent_count")
        private Integer clientAbsentCount;
        @JsonProperty("absence_ratio")
        private Double absenceRatio;
        @JsonProperty("top_suppressing_competitor")
        private String topSuppressingCompetitor;
        @JsonProperty("fallback")
        private Boolean fallback;
    }
}
