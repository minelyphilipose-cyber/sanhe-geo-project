package com.huanjing.geo.module.presale.dto.snapshot.raw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.huanjing.geo.module.presale.dto.snapshot.common.MatchLevel;
import com.huanjing.geo.module.presale.dto.snapshot.common.ScoreSet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 基准值冻结副本(L1)。
 * <p>Schema v1.2 $.raw_snapshot.benchmarks_frozen</p>
 * <p>
 * <b>单个聚合对象</b>(非列表)。生成时从 presale_benchmark 按
 * {@code (industry, industry_role)} 精确查询,未命中则回退 {@code (industry, '_ALL_')},
 * 仍未命中报错 BENCHMARK_MISSING。
 * </p>
 * <p>
 * <b>匹配等级:</b>match_level 记录本次命中情况:
 * <ul>
 *   <li>EXACT:精确命中 (industry, industry_role)</li>
 *   <li>FALLBACK_INDUSTRY:回退到 (industry, '_ALL_'),此时 industry_role 字段值为 "_ALL_"</li>
 * </ul>
 * </p>
 * <p>
 * <b>位置澄清:</b>Codex 反馈后确认 match_level <b>落在 L1 benchmarks_frozen 内</b>
 * (schema v1.2 明确要求),而非 L2。原因是 benchmarks_frozen 本身就是"冻结副本",
 * match_level 是该副本的一部分元数据,表明"此副本是怎么取到的"。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BenchmarksFrozen {

    /** 基准值所属行业。 */
    private String industry;

    /** 基准值所属身份;回退时为 "_ALL_"。 */
    @JsonProperty("industry_role")
    private String industryRole;

    /** 匹配等级,必填。 */
    @JsonProperty("match_level")
    private MatchLevel matchLevel;

    /** 行业平均五维分。 */
    @JsonProperty("industry_avg")
    private ScoreSet industryAvg;

    /** 行业 Top1 五维分。 */
    private ScoreSet top1;

    /** 行业 Top10 综合分阈值。 */
    @JsonProperty("top10_score")
    private Double top10Score;

    /** 置信度(稳定枚举,使用 Java enum 决策 3C)。 */
    @JsonProperty("confidence_level")
    private ConfidenceLevel confidenceLevel;

    /** 基准值计算来源(稳定枚举,使用 Java enum 决策 3C)。 */
    private Source source;

    /** 样本量。 */
    @JsonProperty("sample_size")
    private Integer sampleSize;

    /** 该品牌在本行业的排名位置。 */
    @JsonProperty("industry_ranking")
    private IndustryRanking industryRanking;

    public enum ConfidenceLevel { HIGH, MEDIUM, LOW }

    public enum Source { MANUAL, AUTO_P50, HYBRID }

    /** 行业内排名位置。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IndustryRanking {
        /** 本品牌排名(1 起)。 */
        private Integer position;
        /** 行业已诊断品牌总数。 */
        private Integer total;
    }
}
