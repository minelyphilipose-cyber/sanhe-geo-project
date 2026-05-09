package com.huanjing.geo.module.presale.dto.snapshot.raw;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * L1 原始事实层。
 * <p>Schema v1.2 $defs/rawSnapshot</p>
 * <p>
 * <b>边界契约:</b>只存 AI 测试聚合事实 + 基准值冻结副本。只读,生成时一次写入,永不修改。
 * 修订需派生新版本。
 * </p>
 * <p><b>存储:</b>MySQL {@code presale_report_version.raw_snapshot_json}(JSON 列)。</p>
 * <p><b>必填:</b>schema 要求所有 7 个子字段 required。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RawSnapshotDTO {

    /** 快照元信息(报表 ID、版本号、生成时间、公式版本)。 */
    private RawMeta meta;

    /** 客户填报信息冻结副本。 */
    @JsonProperty("client_info")
    private ClientInfo clientInfo;

    /** 本次测试的执行汇总(轮次、总调用、降级平台等)。 */
    @JsonProperty("test_summary")
    private TestSummary testSummary;

    /** 11 平台的测试事实数据。 */
    @JsonProperty("platform_breakdown")
    private List<PlatformBreakdown> platformBreakdown;

    /** Top3 竞品识别结果(maxItems=3)。 */
    private List<Competitor> competitors;

    /** 竞品组对比模式下,从组合对比回答聚合出的组级优势场景。 */
    @JsonProperty("group_scene_advantages")
    private List<String> groupSceneAdvantages;

    /** 用于报告 Page03 展示的真实决策型问题样本。 */
    @JsonProperty("sample_prompts")
    private List<SamplePrompt> samplePrompts;

    /** 情感明细(两轮合计)。 */
    @JsonProperty("sentiment_detail")
    private SentimentDetail sentimentDetail;

    /**
     * 基准值冻结副本。单个聚合对象,含 match_level / industry_avg / top1 / industry_ranking 等。
     * 生成时从 presale_benchmark 读取并冻结,后续基准值变更不影响此报告。
     */
    @JsonProperty("benchmarks_frozen")
    private BenchmarksFrozen benchmarksFrozen;
}
