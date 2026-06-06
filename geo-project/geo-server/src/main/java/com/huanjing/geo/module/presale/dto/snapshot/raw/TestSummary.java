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

    /** Prompt 测试总数(batch1 + batch2,不含 analyze/judge)。 */
    @JsonProperty("prompt_test_count")
    private Integer promptTestCount;

    /** 样本类意图原始结果行数(不含认知/对比,不乘平台权重)。 */
    @JsonProperty("sample_query_count_raw")
    private Integer sampleQueryCountRaw;

    /** 提及率计算使用的加权分母(样本类意图,豆包权重已计入)。 */
    @JsonProperty("mention_rate_weighted_denominator")
    private Integer mentionRateWeightedDenominator;

    /** Batch1 自然问询测试数。 */
    @JsonProperty("batch1_prompt_test_count")
    private Integer batch1PromptTestCount;

    /** Batch2 竞品对比测试数。 */
    @JsonProperty("batch2_prompt_test_count")
    private Integer batch2PromptTestCount;

    /** QUERY 调用数。 */
    @JsonProperty("query_call_count")
    private Integer queryCallCount;

    /** ANALYZE 调用数。 */
    @JsonProperty("analyze_call_count")
    private Integer analyzeCallCount;

    /** JUDGE 逻辑调用数。 */
    @JsonProperty("judge_call_count")
    private Integer judgeCallCount;

    /** 成功调用数。 */
    @JsonProperty("successful_calls")
    private Integer successfulCalls;

    /** QUERY + ANALYZE + JUDGE 成功调用数。 */
    @JsonProperty("success_call_count")
    private Integer successCallCount;

    /** 失败调用数。 */
    @JsonProperty("failed_calls")
    private Integer failedCalls;

    /** QUERY + ANALYZE + JUDGE 失败调用数。 */
    @JsonProperty("failed_call_count")
    private Integer failedCallCount;

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

    /** 降级平台数量。 */
    @JsonProperty("degraded_platform_count")
    private Integer degradedPlatformCount;

    /**
     * 兼容运行中旧版 Lombok Builder 的构造签名。
     * <p>新增 sampleQueryCountRaw / mentionRateWeightedDenominator 后,热加载或未 clean 的运行环境
     * 可能出现旧 TestSummaryBuilder 调用旧构造器而主类已更新的情况。</p>
     */
    public TestSummary(Integer totalPrompts,
                       Integer totalPlatforms,
                       Integer totalCalls,
                       Integer promptTestCount,
                       Integer batch1PromptTestCount,
                       Integer batch2PromptTestCount,
                       Integer queryCallCount,
                       Integer analyzeCallCount,
                       Integer judgeCallCount,
                       Integer successfulCalls,
                       Integer successCallCount,
                       Integer failedCalls,
                       Integer failedCallCount,
                       Integer excludedCount,
                       Integer rounds,
                       Boolean isDegraded,
                       List<String> degradedPlatforms,
                       Integer degradedPlatformCount) {
        this.totalPrompts = totalPrompts;
        this.totalPlatforms = totalPlatforms;
        this.totalCalls = totalCalls;
        this.promptTestCount = promptTestCount;
        this.batch1PromptTestCount = batch1PromptTestCount;
        this.batch2PromptTestCount = batch2PromptTestCount;
        this.queryCallCount = queryCallCount;
        this.analyzeCallCount = analyzeCallCount;
        this.judgeCallCount = judgeCallCount;
        this.successfulCalls = successfulCalls;
        this.successCallCount = successCallCount;
        this.failedCalls = failedCalls;
        this.failedCallCount = failedCallCount;
        this.excludedCount = excludedCount;
        this.rounds = rounds;
        this.isDegraded = isDegraded;
        this.degradedPlatforms = degradedPlatforms;
        this.degradedPlatformCount = degradedPlatformCount;
    }
}
