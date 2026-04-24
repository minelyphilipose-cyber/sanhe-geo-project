package com.huanjing.geo.module.presale.dto.snapshot.computed;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台 × 意图交叉格子(L2)。
 * <p>Schema v1.2 $.computed_snapshot.platform_intent_breakdown[]</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlatformIntentCell {

    /** 平台编码(与 L1.platform_breakdown.platform_code 对齐)。 */
    @JsonProperty("platform_code")
    private String platformCode;

    /** 意图编码(稳定英文枚举值)。 */
    @JsonProperty("intent_code")
    private String intentCode;

    /** 意图中文展示名(由后端白名单映射)。 */
    @JsonProperty("intent_label")
    private String intentLabel;

    /**
     * 该平台该意图下被提及次数。
     * 认知/对比下无意义,始终为 0。
     */
    @JsonProperty("mention_count")
    private Integer mentionCount;

    /** 提及率(0..100 整数,HALF_UP)。 */
    @JsonProperty("mention_rate")
    private Integer mentionRate;

    /** 该意图类别总 prompt 数。 */
    @JsonProperty("total_prompts")
    private Integer totalPrompts;

    /**
     * 该平台该意图下的有效样本数(分母)。
     * null:该组合没有测试记录;0:有记录但有效样本为 0。
     */
    @JsonProperty("platform_prompt_count")
    private Integer platformPromptCount;

    /** 对比型专用站队方向:target|tie|competitor|null。 */
    @JsonProperty("stance")
    private String stance;
}
