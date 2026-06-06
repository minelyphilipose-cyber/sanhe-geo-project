package com.huanjing.geo.module.presale.dto.snapshot.editable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * L3 可编辑文案层。
 * <p>Schema v1.2 $defs/editableContent</p>
 * <p>
 * <b>边界契约:</b>运营可修改的所有对客文案。L3 字段为 null 时前端回退到默认模板或 L1/L2 同源事实,
 * 非 null 时以 L3 为准。
 * </p>
 * <p>
 * <b>可变性:</b>三层中<b>唯一可就地 UPDATE</b>的层(不派生版本)。
 * 冻结后编辑返回 {@code 409 CONFLICT, next_action=DERIVE_NEW_VERSION}。
 * </p>
 * <p>
 * <b>9 个顶层字段全部 required</b>(顶层键必须存在,块内文案字段大多允许 null)。
 * </p>
 * <p><b>存储:</b>MySQL {@code presale_report_version.editable_content_json}(JSON 列)。</p>
 * <p>
 * <b>合并规则(方案 A 扁平化 merged view):</b>
 * <ul>
 *   <li>标题/副标题/摘要/免责声明:L3 非 null → 用 L3;null → 默认模板(含 {brand_name} 等变量)</li>
 *   <li>key_takeaways:L3 直出(无 L1/L2 回退,默认模板由规则引擎生成后写入 L3)</li>
 *   <li>optimization_findings_content:按 finding_id 与 L2.optimization_findings 一一对应合并;is_hidden=true 跳过;sort_order 排序</li>
 *   <li>phase_descriptions:按 phase_no 与 L2.roi_simulation.phases 合并</li>
 *   <li>competitor_scene_descriptions:按 competitor_rank 与 L1.competitors[] 合并;scene_advantages_polished=null 时回退 L1.competitors[].scene_advantages_raw</li>
 * </ul>
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EditableContentDTO {

    /** 报告主标题。null 时默认"{brand_name} GEO 可见度诊断报告"。 */
    @JsonProperty("report_title")
    private String reportTitle;

    /** 报告副标题。null 时默认"基于 {total_platforms} 个 AI 平台 × {total_prompts} 条查询的深度分析"。 */
    @JsonProperty("report_subtitle")
    private String reportSubtitle;

    /** 执行摘要段落(第 03 页核心)。整体可 null。 */
    @JsonProperty("executive_summary")
    private ExecutiveSummary executiveSummary;

    /** AI 搜索新战场(第 03 页)。整体必填,块内字符串 null 由 normalizer 补默认。 */
    @JsonProperty("market_battleground")
    private MarketBattleground marketBattleground;

    /** 关键发现总结(第 17 页)。数组本身必填,条目内 title/description 必填。 */
    @JsonProperty("key_takeaways")
    private List<KeyTakeaway> keyTakeaways;

    /** 优化发现文案,通过 finding_id 关联 L2.optimization_findings。 */
    @JsonProperty("optimization_findings_content")
    private List<FindingContent> optimizationFindingsContent;

    /** 3 阶段优化路径描述,通过 phase_no 关联 L2.roi_simulation.phases。严格 3 条。 */
    @JsonProperty("phase_descriptions")
    private List<PhaseDescription> phaseDescriptions;

    /** 竞品场景描述,通过 competitor_rank 关联 L1.competitors。 */
    @JsonProperty("competitor_scene_descriptions")
    private List<CompetitorSceneDescription> competitorSceneDescriptions;

    /** 热力图总览句,由后端从 presale_heatmap_summary 配置渲染。 */
    @JsonProperty("heatmap_summary")
    private HeatmapSummary heatmapSummary;

    /**
     * ROI 模拟免责声明。可 null。
     * 默认:"基于行业平均模型的估算,实际效果受多种因素影响,建议结合业务实际情况评估"。
     */
    @JsonProperty("roi_disclaimer")
    private String roiDisclaimer;
}
