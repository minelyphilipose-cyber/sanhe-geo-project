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

    /** 预计提升百分比。 */
    @JsonProperty("estimated_uplift_percent")
    private Double estimatedUpliftPercent;

    /** 预计曝光倍数(target/current 的近似值或业务算法)。 */
    @JsonProperty("estimated_exposure_multiplier")
    private Double estimatedExposureMultiplier;

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
        /** 相对上一阶段的提升分值。 */
        @JsonProperty("uplift_from_previous")
        private Double upliftFromPrevious;
        /** 本阶段已完成优化项数。 */
        @JsonProperty("completed_optimization_count")
        private Integer completedOptimizationCount;
        /** 本阶段目标优化项总数。 */
        @JsonProperty("total_optimization_count")
        private Integer totalOptimizationCount;
    }
}
