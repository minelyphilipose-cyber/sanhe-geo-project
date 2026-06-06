package com.huanjing.geo.module.presale.dto.snapshot.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 场景覆盖分组。
 * <p>Schema v1.2 $defs/sceneCoverageGroup</p>
 * <p>被 {@code L2.computed_snapshot.scene_coverage.{high_value, mid_value, low_value}} 引用。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SceneCoverageGroup {
    /** 该档位下的总 prompt 数。 */
    private Integer total;
    /** 已覆盖数。 */
    private Integer covered;
    /** 覆盖率(0-100)。 */
    @JsonProperty("coverage_rate")
    private Double coverageRate;

    /** 合成覆盖度,与 total/covered/coverage_rate 同口径。 */
    private CoverageStats coverage;

    /** 自然问询覆盖度:推荐/问题/场景。 */
    @JsonProperty("natural_coverage")
    private CoverageStats naturalCoverage;

    /** 裁判覆盖度:认知/对比。 */
    @JsonProperty("judge_coverage")
    private CoverageStats judgeCoverage;

    /** 已覆盖的查询明细(可选)。 */
    @JsonProperty("covered_queries")
    private List<SceneQueryItem> coveredQueries;
    /** 未覆盖的查询明细 + 竞品覆盖情况(可选)。 */
    @JsonProperty("missing_queries")
    private List<SceneQueryMissing> missingQueries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CoverageStats {
        private Integer total;
        private Integer covered;
        @JsonProperty("coverage_rate")
        private Double coverageRate;
    }
}
