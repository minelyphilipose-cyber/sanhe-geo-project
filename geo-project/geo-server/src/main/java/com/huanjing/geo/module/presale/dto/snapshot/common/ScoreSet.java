package com.huanjing.geo.module.presale.dto.snapshot.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 五维评分集。
 * <p>Schema v1.2 $defs/scoreSet</p>
 * <p>被 {@code L1.benchmarks_frozen.industry_avg} / {@code L1.benchmarks_frozen.top1} 引用。
 * 注意:L2 {@code computed_snapshot.scores} 字段更多(含 weights),不复用本类。</p>
 * <p>数值类型:Double(决策 2B)。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScoreSet {
    /** 综合分。 */
    private Double overall;
    /** 提及分。 */
    private Double mention;
    /** 排名分。 */
    private Double ranking;
    /** 情感分。 */
    private Double sentiment;
    /** 覆盖分。 */
    private Double coverage;
}
