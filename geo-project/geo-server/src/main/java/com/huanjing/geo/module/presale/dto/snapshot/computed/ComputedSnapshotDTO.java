package com.huanjing.geo.module.presale.dto.snapshot.computed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.huanjing.geo.module.presale.dto.snapshot.common.SceneCoverageGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * L2 计算结果层。
 * <p>Schema v1.2 $defs/computedSnapshot</p>
 * <p>
 * <b>边界契约:</b>基于 L1 跑出的评分、聚合和规则引擎命中结果。只读,生成时一次写入。
 * 评分公式或规则库变更需派生新版本回填。
 * </p>
 * <p><b>存储:</b>MySQL {@code presale_report_version.computed_snapshot_json}(JSON 列)。</p>
 * <p><b>必填:</b>schema 要求所有 6 个子字段 required。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComputedSnapshotDTO {

    /** 计算快照元信息。 */
    private Meta meta;

    /** 综合评分 + 冻结权重。 */
    private Scores scores;

    /** 按意图类型的覆盖度聚合。 */
    @JsonProperty("intent_breakdown")
    private List<IntentBreakdown> intentBreakdown;

    /** 按平台 × 意图的二维交叉聚合(固定 平台数×5 条)。 */
    @JsonProperty("platform_intent_breakdown")
    private List<PlatformIntentCell> platformIntentBreakdown;

    /** 按业务价值分档的场景覆盖度。 */
    @JsonProperty("scene_coverage")
    private SceneCoverage sceneCoverage;

    /**
     * 规则引擎命中的优化发现。
     * L2 只存命中结果和规则触发数据,对客文案在 L3 {@code optimization_findings_content}。
     */
    @JsonProperty("optimization_findings")
    private List<OptimizationFinding> optimizationFindings;

    /** 预期收益模拟(数值部分,文案在 L3 phase_descriptions)。 */
    @JsonProperty("roi_simulation")
    private RoiSimulation roiSimulation;

    /** 场景覆盖三档容器。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SceneCoverage {
        @JsonProperty("high_value")
        private SceneCoverageGroup highValue;
        @JsonProperty("mid_value")
        private SceneCoverageGroup midValue;
        @JsonProperty("low_value")
        private SceneCoverageGroup lowValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Meta {
        @JsonProperty("algorithm_version")
        private String algorithmVersion;
    }
}
