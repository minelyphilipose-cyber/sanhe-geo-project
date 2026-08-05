package com.huanjing.geo.module.presale.dto.snapshot.merged;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.huanjing.geo.module.presale.dto.snapshot.computed.IntentBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.computed.NarrativeProfile;
import com.huanjing.geo.module.presale.dto.snapshot.computed.OptimizationFinding;
import com.huanjing.geo.module.presale.dto.snapshot.computed.PlatformIntentCell;
import com.huanjing.geo.module.presale.dto.snapshot.computed.RoiSimulation;
import com.huanjing.geo.module.presale.dto.snapshot.computed.SceneCompetitorPressure;
import com.huanjing.geo.module.presale.dto.snapshot.computed.Scores;
import com.huanjing.geo.module.presale.dto.snapshot.computed.ComputedSnapshotDTO.SceneCoverage;
import com.huanjing.geo.module.presale.dto.snapshot.editable.ExecutiveSummary;
import com.huanjing.geo.module.presale.dto.snapshot.editable.HeatmapSummary;
import com.huanjing.geo.module.presale.dto.snapshot.editable.KeyTakeaway;
import com.huanjing.geo.module.presale.dto.snapshot.raw.BenchmarksFrozen;
import com.huanjing.geo.module.presale.dto.snapshot.raw.PlatformBreakdown;
import com.huanjing.geo.module.presale.dto.snapshot.raw.SentimentDetail;
import com.huanjing.geo.module.presale.dto.snapshot.raw.TestSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 合并视图(前端 / PDF 消费的权威扁平化视图)。
 * <p>
 * <b>方案 A(扁平化):</b>按"前端消费视角"重新组织字段,前端不感知三层。
 * 三层原始数据需要排查时走 ops 调试接口或直接查 raw/computed/editable 的 JSON 列。
 * </p>
 * <p>
 * <b>权威性:</b>后端 {@code /api/presale/versions/{versionNo}/merged-view} 返回此 DTO。
 * 前端本地 {@code mergeSnapshot} 仅 P1 mock 期过渡,联调后标记 {@code @deprecated}。
 * </p>
 * <p>
 * <b>合并规则(字段级):</b>
 * <ul>
 *   <li>文案字段(report_title / report_subtitle / executive_summary / roi_disclaimer):
 *       L3 非 null → 用 L3;null → 默认模板(含 {brand_name} / {total_platforms} / {total_prompts} 变量渲染)</li>
 *   <li>key_takeaways:L3 直出(规则引擎在生成时已写入默认文案)</li>
 *   <li>L1/L2 结构化数据(test_summary / platform_breakdown / scores / intent_breakdown 等):直接透传,L3 不覆盖事实</li>
 *   <li>benchmarks_frozen:来自 L1(含 match_level)</li>
 *   <li>merged_findings:L2.optimization_findings × L3.optimization_findings_content 按 finding_id 合并;
 *       is_hidden=true 跳过;sort_order 不为 null 时按其排序,否则按 L2 原序</li>
 *   <li>merged_phases:L2.roi_simulation.phases × L3.phase_descriptions 按 phase_no 合并</li>
 *   <li>merged_competitors:L1.competitors × L3.competitor_scene_descriptions 按 rank 合并;
 *       scene_advantages_polished=null 时回退 L1 的 scene_advantages_raw</li>
 * </ul>
 * </p>
 * <p>
 * <b>MergedViewDTO 不落库,不上 version_id 外键。每次请求由 MergeService 实时计算。</b>
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MergedViewDTO {

    /** 合并视图元数据(版本号、冻结、降级、match_level 等)。 */
    private MergedViewMeta meta;

    // ─────────────────────── 客户信息(L1.client_info 直出) ───────────────────────

    @JsonProperty("brand_name")
    private String brandName;

    private String industry;

    @JsonProperty("industry_role")
    private String industryRole;

    @JsonProperty("represented_brands")
    private List<String> representedBrands;

    private String region;

    @JsonProperty("user_demand")
    private String userDemand;

    // ─────────────────────── L1 事实直出 ───────────────────────

    @JsonProperty("test_summary")
    private TestSummary testSummary;

    @JsonProperty("platform_breakdown")
    private List<PlatformBreakdown> platformBreakdown;

    @JsonProperty("sentiment_detail")
    private SentimentDetail sentimentDetail;

    /** 基准值冻结副本(含 match_level)。前端按 match_level 展示警示条。 */
    @JsonProperty("benchmarks_frozen")
    private BenchmarksFrozen benchmarksFrozen;

    // ─────────────────────── L2 计算结果直出 ───────────────────────

    private Scores scores;

    @JsonProperty("intent_breakdown")
    private List<IntentBreakdown> intentBreakdown;

    @JsonProperty("scene_coverage")
    private SceneCoverage sceneCoverage;

    @JsonProperty("scene_competitor_pressure")
    private SceneCompetitorPressure sceneCompetitorPressure;

    @JsonProperty("platform_intent_breakdown")
    private List<PlatformIntentCell> platformIntentBreakdown;

    @JsonProperty("narrative_profile")
    private NarrativeProfile narrativeProfile;

    @JsonProperty("roi_simulation")
    private RoiSimulation roiSimulation;

    // ─────────────────────── L3 文案(已应用默认回退) ───────────────────────

    @JsonProperty("report_title")
    private String reportTitle;

    @JsonProperty("report_subtitle")
    private String reportSubtitle;

    @JsonProperty("executive_summary")
    private ExecutiveSummary executiveSummary;

    @JsonProperty("key_takeaways")
    private List<KeyTakeaway> keyTakeaways;

    /** 热力图总览句,由 L3 从 presale_heatmap_summary 配置表渲染。 */
    @JsonProperty("heatmap_summary")
    private HeatmapSummary heatmapSummary;

    @JsonProperty("roi_disclaimer")
    private String roiDisclaimer;

    // ─────────────────────── 合并产物(L2 × L3 按 ID 合并后的新结构) ───────────────────────

    /**
     * 优化发现合并列表。按 sort_order 排序(无 sort_order 保 L2 原序),
     * is_hidden=true 已剔除。
     */
    @JsonProperty("merged_findings")
    private List<MergedFinding> mergedFindings;

    /** 阶段合并列表(严格 3 条,按 phase_no 1/2/3 顺序)。 */
    @JsonProperty("merged_phases")
    private List<MergedPhase> mergedPhases;

    /** 竞品合并列表(按 rank 1/2/3 顺序)。 */
    @JsonProperty("merged_competitors")
    private List<MergedCompetitor> mergedCompetitors;

    /** 竞品组对比模式下的组级优势场景;为空时前端回退 Top1 竞品场景。 */
    @JsonProperty("group_scene_advantages")
    private List<String> groupSceneAdvantages;

    // ─────────────────────── 合并专用嵌套类型 ───────────────────────

    /**
     * 优化发现合并条目。L2 finding(含 rule_code / priority / category / evidence_data)
     * + L3 文案(title / description / evidence_text / sort_order)。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MergedFinding {

        /** L2 原数据(含 finding_id / rule_code / priority / category / evidence_data)。 */
        private OptimizationFinding finding;

        /** 最终展示标题(L3.title 或默认模板)。 */
        private String title;

        /** 最终展示描述。 */
        private String description;

        /** 最终证据文字(L3.evidence_text 或由 L2.evidence_data 模板渲染)。 */
        @JsonProperty("evidence_text")
        private String evidenceText;

        /**
         * 最终排序序号。若 L3 未指定 sort_order,合并服务按 L2 原序填入 1/2/3...,
         * 前端可直接按此排序渲染。
         */
        @JsonProperty("sort_order")
        private Integer sortOrder;
    }

    /**
     * 阶段合并条目。L2 phase(目标分 / 时长标签 / 完成数)+ L3 文案(title / description)。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MergedPhase {

        /** L2 原始阶段数据(phase_no / duration_label / target_score 等)。 */
        private RoiSimulation.RoiPhase phase;

        /** 阶段标题(L3 或默认)。 */
        private String title;

        /** 阶段描述(L3 或默认)。 */
        private String description;
    }

    /**
     * 竞品合并条目。L1 基础信息(rank / name / mention_count / mention_rate / avg_ranking)
     * + 最终场景描述(L3 polished 或 L1 raw 回退)。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MergedCompetitor {

        /** 排名 1-3。 */
        private Integer rank;

        /** 竞品名。 */
        private String name;

        @JsonProperty("mention_count")
        private Integer mentionCount;

        @JsonProperty("mention_rate")
        private Double mentionRate;

        @JsonProperty("avg_ranking")
        private Double avgRanking;

        /**
         * 最终场景描述。
         * L3.scene_advantages_polished 非 null → 用 L3;
         * null → 回退 L1.competitors[rank-1].scene_advantages_raw。
         */
        @JsonProperty("scene_advantages")
        private List<String> sceneAdvantages;

        /**
         * 本条 scene_advantages 来源。true=L3 polished,false=L1 raw 回退。
         * 前端可据此在 UI 上加"运营润色"或"原始提取"的小标签。
         */
        @JsonProperty("scene_is_polished")
        private Boolean sceneIsPolished;
    }
}
