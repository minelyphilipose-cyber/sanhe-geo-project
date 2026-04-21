/**
 * L3 可编辑文案层类型。
 *
 * 对应 Java 包:com.huanjing.geo.module.presale.dto.snapshot.editable
 * 对应 schema v1.2:$defs/editableContent
 *
 * 边界契约:运营可修改的所有对客文案。L3 字段为 null 时前端回退到默认模板或 L1/L2 事实。
 * 可变性:三层中唯一可就地 UPDATE 的层;冻结后编辑返回 409 CONFLICT。
 * 存储:MySQL presale_report_version.editable_content_json。
 *
 * 8 个顶层字段全部 required(顶层键必须存在,块内文案字段大多允许 null)。
 */

/**
 * L3 顶层。
 */
export interface EditableContentDTO {
  /** null 回退 "{brand_name} GEO 可见度诊断报告"。 */
  report_title: string | null;
  /** null 回退 "基于 {total_platforms} 个 AI 平台 × {total_prompts} 条查询的深度分析"。 */
  report_subtitle: string | null;
  /** 整体可 null。 */
  executive_summary: ExecutiveSummary | null;
  /** 数组本身必填,条目内 title/description 必填。 */
  key_takeaways: KeyTakeaway[];
  /** 通过 finding_id 关联 L2.optimization_findings。 */
  optimization_findings_content: FindingContent[];
  /** 严格 3 条,通过 phase_no 关联 L2.roi_simulation.phases。 */
  phase_descriptions: PhaseDescription[];
  /** 通过 competitor_rank 关联 L1.competitors。 */
  competitor_scene_descriptions: CompetitorSceneDescription[];
  /** null 回退默认免责声明。 */
  roi_disclaimer: string | null;
}

/**
 * Schema v1.2 $.editable_content.executive_summary(可为 null)
 */
export interface ExecutiveSummary {
  /** 一句话核心结论。 */
  headline: string;
  /** 展开描述段落。 */
  paragraph: string;
}

/**
 * Schema v1.2 $.editable_content.key_takeaways[]
 * 三字段全部 required。
 */
export interface KeyTakeaway {
  order_no: number;
  title: string;
  description: string;
}

/**
 * 优化发现的对客文案。
 * Schema v1.2 $.editable_content.optimization_findings_content[]
 *
 * 仅 finding_id 必填,其余字段均可 null(回退到规则模板生成的默认文案)。
 * is_hidden 是 L3 唯一的隐藏控制(L3 整体没有通用的模块隐藏/排序能力)。
 */
export interface FindingContent {
  /** 关联 L2 的 finding_id,如 "F001"。 */
  finding_id: string;
  title?: string | null;
  description?: string | null;
  /** 证据文字描述,由 L2.evidence_data 默认渲染,L3 可覆盖。 */
  evidence_text?: string | null;
  /** 运营自定义排序,null 时保持 L2 原序。 */
  sort_order?: number | null;
  /** 默认 false;true 时此条在 merged view 和 PDF 中跳过。 */
  is_hidden?: boolean;
}

/**
 * 阶段描述。
 * Schema v1.2 $.editable_content.phase_descriptions[](严格 3 条)
 * 通过 phase_no 关联 L2.roi_simulation.phases。
 */
export interface PhaseDescription {
  /** 1 | 2 | 3,必填。 */
  phase_no: 1 | 2 | 3;
  /** 如"基础优化阶段",可 null。 */
  title?: string | null;
  description?: string | null;
}

/**
 * 竞品场景描述。
 * Schema v1.2 $.editable_content.competitor_scene_descriptions[]
 *
 * 回退规则:scene_advantages_polished 为 null 时,merged view 回退到
 * L1.competitors[rank-1].scene_advantages_raw。这是 L3 唯一一处向 L1 回退的字段。
 */
export interface CompetitorSceneDescription {
  /** 1-3,必填。 */
  competitor_rank: 1 | 2 | 3;
  /** null 时前端回退到 L1.competitors[rank-1].scene_advantages_raw。 */
  scene_advantages_polished?: string[] | null;
}
