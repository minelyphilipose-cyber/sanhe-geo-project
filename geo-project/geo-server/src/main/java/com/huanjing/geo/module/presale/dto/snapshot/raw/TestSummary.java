package com.huanjing.geo.module.presale.dto.snapshot.raw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 测试执行汇总(L1)。
 * <p>Schema v1.2 $.raw_snapshot.test_summary</p>
 * <p>9 个字段全部 required。{@code total_calls=660} 的典型组成见 mock 的 _number_notes。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestSummary {

    /** 使用的 prompt 数量(上限 25)。 */
    @JsonProperty("total_prompts")
    private Integer totalPrompts;

    /** 参与测试的平台数量(典型 11)。 */
    @JsonProperty("total_platforms")
    private Integer totalPlatforms;

    /** LLM 调用总数(含两轮测试+分析,典型 660)。 */
    @JsonProperty("total_calls")
    private Integer totalCalls;

    /** 成功调用数。 */
    @JsonProperty("successful_calls")
    private Integer successfulCalls;

    /** 失败调用数。 */
    @JsonProperty("failed_calls")
    private Integer failedCalls;

    /** 运营手动剔除的异常结果数。 */
    @JsonProperty("excluded_count")
    private Integer excludedCount;

    /** 轮次数(schema 枚举 [1, 2])。保留为 Integer,不做 Java enum。 */
    private Integer rounds;

    /** 是否整份报告降级(降级平台 ≥4 为业务阈值)。 */
    @JsonProperty("is_degraded")
    private Boolean isDegraded;

    /** 降级平台 platform_code 列表(平台级成功率 < 50%)。 */
    @JsonProperty("degraded_platforms")
    private List<String> degradedPlatforms;
}
