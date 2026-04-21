/**
 * mergeSnapshot - 三层快照合并工具(非权威镜像)。
 *
 * ⚠️ 边界声明(重要):
 * 本函数是后端 MergeService(/api/presale/versions/{versionNo}/merged-view)的**非权威镜像**,
 * 仅用于以下场景:
 *   1. P1 mock 期前端本地跑通列表/详情页,后端 MergeService 尚未上线时的离线兜底
 *   2. 前后端合并规则对账测试(共享 fixture,两边结果必须一致)
 *
 * **不得**在生产运行时链路上使用本函数替代后端 /merged-view 接口。
 * 联调完成后,调用方应全部切换到后端接口,本函数将标记 @deprecated 保留仅用于 fixture 对账。
 *
 * 真相源:后端 MergeService。此处任何规则差异都应以后端为准修正,而不是反过来。
 *
 * 对应 Java 实现:后续 com.huanjing.geo.module.presale.service.MergeService。
 * 对应 schema:report_data_schema_v1_2.json + README "MergedViewDTO 合并规则" 章节。
 */

import type {
  BenchmarksFrozen,
  ClientInfo,
  Competitor,
  RawSnapshotDTO,
  TestSummary,
} from '../../types/presale/raw';
import type {
  ComputedSnapshotDTO,
  OptimizationFinding,
  RoiPhase,
} from '../../types/presale/computed';
import type {
  CompetitorSceneDescription,
  EditableContentDTO,
  ExecutiveSummary,
  FindingContent,
  KeyTakeaway,
  PhaseDescription,
} from '../../types/presale/editable';
import type {
  MergedCompetitor,
  MergedFinding,
  MergedPhase,
  MergedViewDTO,
  MergedViewMeta,
} from '../../types/presale/merged';

/**
 * 版本行元数据(对应 presale_report_version 行)。
 * mergeSnapshot 需要这些行级列来组装 MergedViewMeta。
 * 字段对齐 V62 v4 真实列名。
 */
export interface VersionRowMeta {
  version_id: number;
  report_id: number;
  version_no: number;
  schema_version: string;
  generation_status: string;
  /** RFC3339 带 +08:00,可 null。 */
  frozen_at?: string | null;
  frozen_by?: number | null;
  frozen_reason?: string | null;
  content_updated_at?: string | null;
  content_updated_by?: number | null;
  /** 对应 presale_report_version.is_degraded(TINYINT)。派生版本继承原值。 */
  is_degraded: boolean;
  /**
   * 对应 presale_report_version.degraded_platforms(JSON,**DB nullable**)。
   * 派生版本继承原值(三分法"事实冻结层"),未降级时通常为 null 或 "[]"。
   * mergeSnapshot 会在合并时归一为 string[](null/undefined → []),前端消费 MergedViewMeta 时永远是数组。
   */
  degraded_platforms: string[] | null;
  export_success_count: number;
  export_success_at?: string | null;
}

/**
 * 合并三层快照为前端消费的扁平视图。
 *
 * @param raw           L1 事实层
 * @param computed      L2 计算层
 * @param editable      L3 文案层
 * @param versionRow    版本行元数据(对应 presale_report_version 行)
 * @returns 合并后的扁平视图
 */
export function mergeSnapshot(
  raw: RawSnapshotDTO,
  computed: ComputedSnapshotDTO,
  editable: EditableContentDTO,
  versionRow: VersionRowMeta
): MergedViewDTO {
  const meta = buildMeta(raw, versionRow);

  return {
    meta,

    // L1.client_info 直出
    brand_name: raw.client_info.brand_name,
    industry: raw.client_info.industry,
    industry_role: raw.client_info.industry_role,
    region: raw.client_info.region,
    user_demand: raw.client_info.user_demand ?? null,

    // L1 事实直出
    test_summary: raw.test_summary,
    platform_breakdown: raw.platform_breakdown,
    sentiment_detail: raw.sentiment_detail,
    benchmarks_frozen: raw.benchmarks_frozen,

    // L2 计算结果直出
    scores: computed.scores,
    intent_breakdown: computed.intent_breakdown,
    scene_coverage: computed.scene_coverage,
    roi_simulation: computed.roi_simulation,

    // L3 文案 + 默认模板回退
    report_title: resolveReportTitle(editable.report_title, raw.client_info),
    report_subtitle: resolveReportSubtitle(
      editable.report_subtitle,
      raw.test_summary
    ),
    executive_summary: resolveExecutiveSummary(editable.executive_summary),
    key_takeaways: editable.key_takeaways,
    roi_disclaimer: resolveRoiDisclaimer(editable.roi_disclaimer),

    // 合并产物
    merged_findings: mergeFindings(
      computed.optimization_findings,
      editable.optimization_findings_content
    ),
    merged_phases: mergePhases(
      computed.roi_simulation.phases,
      editable.phase_descriptions
    ),
    merged_competitors: mergeCompetitors(
      raw.competitors,
      editable.competitor_scene_descriptions
    ),
  };
}

// ─────────────────────── meta 组装 ───────────────────────

function buildMeta(
  raw: RawSnapshotDTO,
  row: VersionRowMeta
): MergedViewMeta {
  return {
    version_id: row.version_id,
    report_id: row.report_id,
    version_no: row.version_no,
    schema_version: row.schema_version,
    generation_status: row.generation_status,
    frozen: row.frozen_at != null,
    frozen_at: row.frozen_at ?? null,
    frozen_by: row.frozen_by ?? null,
    frozen_reason: row.frozen_reason ?? null,
    content_updated_at: row.content_updated_at ?? null,
    content_updated_by: row.content_updated_by ?? null,
    // 行级列为权威(派生时已复制,三分法"事实冻结层"保证与 L1 一致)
    is_degraded: row.is_degraded,
    // DB 可为 null,前端契约永远是数组,此处归一
    degraded_platforms: row.degraded_platforms ?? [],
    // match_level 从 L1 提升到 meta,便于前端警示条一处读取
    match_level: raw.benchmarks_frozen.match_level,
    export_success_count: row.export_success_count,
    export_success_at: row.export_success_at ?? null,
  };
}

// ─────────────────────── 文案默认模板 + 变量插值 ───────────────────────

/**
 * 报告标题默认模板。
 * L3.report_title null → "{brand_name} GEO 可见度诊断报告"。
 */
function resolveReportTitle(
  l3: string | null,
  client: ClientInfo
): string {
  if (l3 != null) return l3;
  return `${client.brand_name} GEO 可见度诊断报告`;
}

/**
 * 报告副标题默认模板。
 * L3.report_subtitle null → "基于 {total_platforms} 个 AI 平台 × {total_prompts} 条查询的深度分析"。
 */
function resolveReportSubtitle(
  l3: string | null,
  summary: TestSummary
): string {
  if (l3 != null) return l3;
  return `基于 ${summary.total_platforms} 个 AI 平台 × ${summary.total_prompts} 条查询的深度分析`;
}

/**
 * 执行摘要默认模板。
 * L3.executive_summary null → 返回占位性默认文案(mock 期通常 L3 已由规则引擎写入,此处仅兜底)。
 */
function resolveExecutiveSummary(
  l3: ExecutiveSummary | null
): ExecutiveSummary {
  if (l3 != null) return l3;
  return {
    headline: '暂无执行摘要',
    paragraph: '规则引擎未生成默认摘要,请运营在 L3 编辑页填写。',
  };
}

/**
 * ROI 免责声明默认模板。
 * L3.roi_disclaimer null → schema 默认描述。
 */
function resolveRoiDisclaimer(l3: string | null): string {
  if (l3 != null) return l3;
  return '基于行业平均模型的估算,实际效果受多种因素影响,建议结合业务实际情况评估';
}

// ─────────────────────── findings 合并 ───────────────────────

/**
 * 合并 L2 optimization_findings × L3 optimization_findings_content。
 *
 * 规则:
 * 1. 按 finding_id 连接
 * 2. L3.is_hidden = true 的条目跳过
 * 3. 排序:L3.sort_order 有值按其升序;无值按 L2 原序 index(稳定排序)
 * 4. 文案字段 L3 非 null → L3;null → 默认模板(由 rule_code + evidence_data 渲染)
 */
export function mergeFindings(
  l2Findings: OptimizationFinding[],
  l3Content: FindingContent[]
): MergedFinding[] {
  const l3ByFindingId = new Map<string, FindingContent>();
  for (const c of l3Content) {
    l3ByFindingId.set(c.finding_id, c);
  }

  // 先装配所有未隐藏条目,保留 L2 原序 index 作为稳定排序兜底
  const assembled: Array<{ merged: MergedFinding; l2Index: number }> = [];
  l2Findings.forEach((l2, idx) => {
    const l3 = l3ByFindingId.get(l2.finding_id);
    if (l3?.is_hidden === true) return;

    const merged: MergedFinding = {
      finding: l2,
      title: l3?.title ?? renderDefaultFindingTitle(l2),
      description: l3?.description ?? renderDefaultFindingDescription(l2),
      evidence_text: l3?.evidence_text ?? renderDefaultEvidenceText(l2),
      // sort_order 合并后保证非 null:L3.sort_order ?? L2 原序 (idx+1)
      sort_order: l3?.sort_order ?? idx + 1,
    };
    assembled.push({ merged, l2Index: idx });
  });

  // 按 sort_order 升序,同值按 L2 原序稳定
  assembled.sort((a, b) => {
    const diff = a.merged.sort_order - b.merged.sort_order;
    return diff !== 0 ? diff : a.l2Index - b.l2Index;
  });

  return assembled.map((x) => x.merged);
}

/**
 * finding 默认标题:基于 rule_code 映射。
 * 真实规则库映射由后端 OptimizationRuleRegistry 提供;前端 mock 期用简化兜底。
 */
function renderDefaultFindingTitle(l2: OptimizationFinding): string {
  return `[${l2.priority}] ${l2.rule_code}`;
}

function renderDefaultFindingDescription(l2: OptimizationFinding): string {
  return `规则 ${l2.rule_code} 触发 ${l2.category} 类优化建议。详细依据见证据数据。`;
}

function renderDefaultEvidenceText(l2: OptimizationFinding): string {
  // 简单序列化 evidence_data 作为兜底;后端会按 rule_code 模板渲染更人性化的文本
  const entries = Object.entries(l2.evidence_data);
  if (entries.length === 0) return '(无证据数据)';
  return entries.map(([k, v]) => `${k}=${formatEvidenceValue(v)}`).join(', ');
}

function formatEvidenceValue(v: unknown): string {
  if (v == null) return 'null';
  if (typeof v === 'number' || typeof v === 'string' || typeof v === 'boolean') {
    return String(v);
  }
  return JSON.stringify(v);
}

// ─────────────────────── phases 合并 ───────────────────────

/**
 * 合并 L2 roi_simulation.phases × L3 phase_descriptions。
 * 严格 3 条,**强制按 phase_no 1/2/3 顺序输出**,不依赖输入数组顺序。
 *
 * 实现策略:
 * 1. 将 L2 phases 和 L3 descriptions 都按 phase_no 建 map
 * 2. 遍历固定序 [1, 2, 3] 从 map 取对应条目
 * 3. 若 L2 缺失某个 phase_no(异常数据),合成占位 phase 保证返回严格 3 条(前端不崩)
 *
 * 契约意义:后端 MergeService 必须做同样的顺序保证,fixture 对账默认期望按 1/2/3 输出。
 */
export function mergePhases(
  l2Phases: readonly RoiPhase[],
  l3Descriptions: PhaseDescription[]
): MergedPhase[] {
  const l2ByPhaseNo = new Map<number, RoiPhase>();
  for (const p of l2Phases) {
    l2ByPhaseNo.set(p.phase_no, p);
  }
  const l3ByPhaseNo = new Map<number, PhaseDescription>();
  for (const d of l3Descriptions) {
    l3ByPhaseNo.set(d.phase_no, d);
  }

  const ORDER: ReadonlyArray<1 | 2 | 3> = [1, 2, 3];
  return ORDER.map((phaseNo) => {
    const phase = l2ByPhaseNo.get(phaseNo) ?? synthesizePlaceholderPhase(phaseNo);
    const l3 = l3ByPhaseNo.get(phaseNo);
    return {
      phase,
      title: l3?.title ?? renderDefaultPhaseTitle(phase),
      description: l3?.description ?? renderDefaultPhaseDescription(phase),
    };
  });
}

/**
 * L2 缺失某个 phase_no 时的兜底占位 phase。
 * 正常数据不会走到此分支(schema 强约束 3 条);走到说明数据异常,保持前端不崩并输出占位,便于排查。
 */
function synthesizePlaceholderPhase(phaseNo: 1 | 2 | 3): RoiPhase {
  return {
    phase_no: phaseNo,
    duration_label: '(数据缺失)',
    target_score: 0,
    uplift_from_previous: 0,
    completed_optimization_count: 0,
    total_optimization_count: 0,
  };
}

function renderDefaultPhaseTitle(phase: RoiPhase): string {
  switch (phase.phase_no) {
    case 1:
      return '基础优化阶段';
    case 2:
      return '内容建设阶段';
    case 3:
      return '持续优化阶段';
    default:
      return `阶段 ${phase.phase_no}`;
  }
}

function renderDefaultPhaseDescription(phase: RoiPhase): string {
  return `${phase.duration_label}:目标 ${phase.target_score} 分,完成 ${phase.completed_optimization_count}/${phase.total_optimization_count} 项优化`;
}

// ─────────────────────── competitors 合并 ───────────────────────

/**
 * 合并 L1 competitors × L3 competitor_scene_descriptions。
 *
 * **强制按 rank 1/2/3 顺序输出**,不依赖输入数组顺序。L1 最多 3 个竞品(schema maxItems=3),
 * 实际存在几个就输出几个,但顺序严格 1 → 2 → 3。
 *
 * 特殊回退:L3.scene_advantages_polished 为 null 时回退 L1.scene_advantages_raw。
 * 这是 L3 唯一一处向 L1(非默认模板)回退的字段。
 *
 * 实现策略:
 * 1. 将 L1 按 rank 建 map(最多 3 条),L3 按 competitor_rank 建 map
 * 2. 遍历固定序 [1, 2, 3],L1 map 里存在才输出,不存在则跳过(客户填报不足 3 个竞品的正常场景)
 *
 * 契约意义:后端 MergeService 必须做同样的顺序保证。
 */
export function mergeCompetitors(
  l1Competitors: Competitor[],
  l3Descriptions: CompetitorSceneDescription[]
): MergedCompetitor[] {
  const l1ByRank = new Map<number, Competitor>();
  for (const c of l1Competitors) {
    l1ByRank.set(c.rank, c);
  }
  const l3ByRank = new Map<number, CompetitorSceneDescription>();
  for (const d of l3Descriptions) {
    l3ByRank.set(d.competitor_rank, d);
  }

  const ORDER: ReadonlyArray<1 | 2 | 3> = [1, 2, 3];
  const result: MergedCompetitor[] = [];
  for (const rank of ORDER) {
    const c = l1ByRank.get(rank);
    if (c == null) continue; // 客户填报不足 3 个,该 rank 无竞品,跳过

    const l3 = l3ByRank.get(rank);
    const polished = l3?.scene_advantages_polished;
    const useL3 = polished != null;

    result.push({
      rank: c.rank,
      name: c.name,
      mention_count: c.mention_count,
      mention_rate: c.mention_rate,
      avg_ranking: c.avg_ranking,
      scene_advantages: useL3 ? polished! : c.scene_advantages_raw ?? [],
      scene_is_polished: useL3,
    });
  }
  return result;
}
