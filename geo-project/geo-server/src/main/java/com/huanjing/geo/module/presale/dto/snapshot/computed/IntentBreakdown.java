package com.huanjing.geo.module.presale.dto.snapshot.computed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 意图类型聚合条目(L2)。
 * <p>Schema v1.2 $.computed_snapshot.intent_breakdown[]</p>
 * <p>
 * {@code category} schema 枚举:推荐型/对比型/问题型/认知型/场景型(中文字面值)。
 * {@code business_value} schema 枚举:高/中/低(中文字面值)。
 * 按决策 3C,含中文值的枚举保留 String,不定义 Java enum。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntentBreakdown {

    /** 意图分类(中文字面值,如"推荐型")。 */
    private String category;

    /** 业务价值档位(中文字面值:高/中/低)。 */
    @JsonProperty("business_value")
    private String businessValue;

    /** 该意图下的总 prompt 数。 */
    @JsonProperty("total_prompts")
    private Integer totalPrompts;

    /** 覆盖到的 prompt 数。 */
    @JsonProperty("covered_prompts")
    private Integer coveredPrompts;

    /** 覆盖率(0-100)。 */
    @JsonProperty("coverage_rate")
    private Double coverageRate;

    /** 该意图下的平均排名,可 null。 */
    @JsonProperty("avg_ranking")
    private Double avgRanking;
}
