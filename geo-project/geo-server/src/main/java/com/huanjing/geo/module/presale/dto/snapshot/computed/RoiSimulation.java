package com.huanjing.geo.module.presale.dto.snapshot.computed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 预期收益模拟 · 数值部分(L2)。
 * <p>Schema v1.2 $.computed_snapshot.roi_simulation</p>
 * <p>
 * {@code phases} 严格 3 条(minItems=3, maxItems=3),phase_no 分别为 1/2/3。
 * 对客文案(phase 的 title/description)在 L3 {@code phase_descriptions} 通过 phase_no 关联。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoiSimulation {

    /** 当前总分(等于 L2.scores.overall)。冗余存此方便 ROI 单独展示。 */
    @JsonProperty("current_score")
    private Double currentScore;

    /** 6 个月后目标总分。 */
    @JsonProperty("target_score")
    private Double targetScore;

    /** 目标总分区间下限(情景测算,非保证)。 */
    @JsonProperty("target_score_low")
    private Double targetScoreLow;

    /** 目标总分区间上限(情景测算,非保证)。 */
    @JsonProperty("target_score_high")
    private Double targetScoreHigh;

    /** 预计提升百分比。 */
    @JsonProperty("estimated_uplift_percent")
    private Double estimatedUpliftPercent;

    /** 预计提升百分比区间下限。 */
    @JsonProperty("estimated_uplift_percent_low")
    private Double estimatedUpliftPercentLow;

    /** 预计提升百分比区间上限。 */
    @JsonProperty("estimated_uplift_percent_high")
    private Double estimatedUpliftPercentHigh;

    /** 预计曝光倍数(target/current 的近似值或业务算法)。 */
    @JsonProperty("estimated_exposure_multiplier")
    private Double estimatedExposureMultiplier;

    /** 真实案例提升区间。无真实案例时为 null,前端隐藏。 */
    @JsonProperty("case_study_range")
    private CaseStudyRange caseStudyRange;

    /** 3 阶段节点。 */
    private List<RoiPhase> phases;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RoiPhase {
        /** 阶段编号 1/2/3(schema enum)。保留 Integer,不做 Java enum。 */
        @JsonProperty("phase_no")
        private Integer phaseNo;
        /** 时长标签,如 "M1" / "M2-3" / "M4-6"。 */
        @JsonProperty("duration_label")
        private String durationLabel;
        /** 本阶段目标总分。 */
        @JsonProperty("target_score")
        private Double targetScore;
        /** 本阶段目标总分区间下限。 */
        @JsonProperty("target_score_low")
        private Double targetScoreLow;
        /** 本阶段目标总分区间上限。 */
        @JsonProperty("target_score_high")
        private Double targetScoreHigh;
        /** 相对上一阶段的提升分值。 */
        @JsonProperty("uplift_from_previous")
        private Double upliftFromPrevious;
        /** 相对上一阶段提升区间下限。 */
        @JsonProperty("uplift_from_previous_low")
        private Double upliftFromPreviousLow;
        /** 相对上一阶段提升区间上限。 */
        @JsonProperty("uplift_from_previous_high")
        private Double upliftFromPreviousHigh;
        /** 是否展示本阶段分数测算。无计划项阶段为 false。 */
        @JsonProperty("projection_enabled")
        private Boolean projectionEnabled;
        /** 本阶段已完成优化项数。 */
        @JsonProperty("completed_optimization_count")
        private Integer completedOptimizationCount;
        /** 本阶段目标优化项总数。 */
        @JsonProperty("total_optimization_count")
        private Integer totalOptimizationCount;
        /** Presale 场景展示用:本阶段计划优化项数。 */
        @JsonProperty("planned_optimization_count")
        private Integer plannedOptimizationCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CaseStudyRange {
        /** 案例标签,如 "同城装修案例"。 */
        private String label;
        /** 案例优化前分数。 */
        @JsonProperty("before_score")
        private Double beforeScore;
        /** 案例优化后分数区间下限。 */
        @JsonProperty("after_score_low")
        private Double afterScoreLow;
        /** 案例优化后分数区间上限。 */
        @JsonProperty("after_score_high")
        private Double afterScoreHigh;
        /** 数据来源说明。 */
        private String source;
        /** 样本周期。 */
        @JsonProperty("sample_period")
        private String samplePeriod;
    }
}
