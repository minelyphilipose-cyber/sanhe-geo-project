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
  PlatformBreakdown,
} from '../../types/presale/raw';
import type {
  ComputedSnapshotDTO,
  NarrativeProfile,
  OptimizationFinding,
  PlatformIntentCell,
  RoiPhase,
  SceneCompetitorPressure,
} from '../../types/presale/computed';
import type {
  CompetitorSceneDescription,
  EditableContentDTO,
  ExecutiveSummary,
  FindingContent,
  HeatmapSummary,
  KeyTakeaway,
  MarketBattleground,
  PhaseDescription,
} from '../../types/presale/editable';
import type {
  MergedCompetitor,
  MergedFinding,
  MergedPhase,
  MergedViewDTO,
  MergedViewMeta,
} from '../../types/presale/merged';
import { toIntRounded } from './numberFormat';

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
  query_web_mode?: 'OFF' | 'SHADOW' | 'REQUIRED';
  planned_query_count?: number;
  planned_web_query_count?: number;
  web_valid_query_count?: number;
  effective_sample_count?: number;
  query_failed_count?: number;
  analyze_failed_count?: number;
  skipped_query_count?: number;
  degraded_excluded_sample_count?: number;
  main_web_failure_code?: string | null;
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
  const meta = buildMeta(raw, computed, versionRow);
  const l2Findings = asArray<OptimizationFinding>(computed?.optimization_findings);
  const l3FindingContent = asArray<FindingContent>(
    editable?.optimization_findings_content
  );
  const l2Phases = asArray<RoiPhase>(computed?.roi_simulation?.phases);
  const l3PhaseDescriptions = asArray<PhaseDescription>(
    editable?.phase_descriptions
  );
  const l1Competitors = asArray<Competitor>(raw?.competitors);
  const l3CompetitorDescriptions = asArray<CompetitorSceneDescription>(
    editable?.competitor_scene_descriptions
  );
  const keyTakeaways = normalizeKeyTakeaways(asArray<KeyTakeaway>(editable?.key_takeaways));
  const platformBreakdown = filterEffectivePlatforms(raw);
  const effectivePlatformCodes = new Set(platformBreakdown.map((p) => p.platform_code));
  const effectiveTestSummary = normalizeTestSummary(raw.test_summary, platformBreakdown);
  const platformIntentBreakdown = asArray<PlatformIntentCell>(computed?.platform_intent_breakdown).filter((cell) =>
    effectivePlatformCodes.has(cell.platform_code)
  );
  const narrativeProfile = normalizeNarrativeProfile(computed?.narrative_profile);

  return {
    meta,

    // L1.client_info 直出
    brand_name: raw.client_info.brand_name,
    industry: raw.client_info.industry,
    industry_role: raw.client_info.industry_role,
    represented_brands: raw.client_info.represented_brands ?? [],
    attribution_mode: raw.client_info.attribution_mode ?? 'STANDARD',
    matched_role_name: raw.client_info.matched_role_name,
    region: raw.client_info.region,
    user_demand: raw.client_info.user_demand ?? null,

    // L1 事实直出
    test_summary: effectiveTestSummary,
    platform_breakdown: platformBreakdown,
    sentiment_detail: raw.sentiment_detail,
    benchmarks_frozen: raw.benchmarks_frozen,
    dealer_attribution_summary: raw.dealer_attribution_summary,

    // L2 计算结果直出
    scores: computed.scores,
    intent_breakdown: computed.intent_breakdown,
    scene_coverage: computed.scene_coverage,
    scene_competitor_pressure: normalizeSceneCompetitorPressure(computed.scene_competitor_pressure),
    roi_simulation: computed.roi_simulation,
    // β·2·补 新增:平台 × 意图交叉矩阵(P05 热力图消费)。
    // `?? []` 兼容历史报告(spec v3 §7.2):新生成 DONE 报告此字段 required,
    // 历史快照可能缺失,此处归一为空数组,Page05 会显示降级提示。
    // 6 个月后评估是否移除此兜底(见 spec §7.4)。
    platform_intent_breakdown: platformIntentBreakdown,
    narrative_profile: narrativeProfile,
    dealer_attribution_interpretation: computed.dealer_attribution_interpretation,

    // L3 文案 + 默认模板回退
    report_title: resolveReportTitle(editable.report_title, raw.client_info),
    report_subtitle: resolveReportSubtitle(
      editable.report_subtitle,
      effectiveTestSummary
    ),
    executive_summary: resolveExecutiveSummary(editable.executive_summary),
    market_battleground: resolveMarketBattleground(editable.market_battleground),
    key_takeaways: keyTakeaways,
    heatmap_summary: resolveHeatmapSummary(editable?.heatmap_summary, narrativeProfile),
    roi_disclaimer: resolveRoiDisclaimer(editable.roi_disclaimer),

    // 合并产物
    merged_findings: mergeFindings(
      l2Findings,
      l3FindingContent
    ),
    merged_phases: mergePhases(
      l2Phases,
      l3PhaseDescriptions
    ),
    merged_competitors: mergeCompetitors(
      l1Competitors,
      l3CompetitorDescriptions
    ),
    group_scene_advantages: raw.group_scene_advantages ?? [],
  };
}

function normalizeKeyTakeaways(source: KeyTakeaway[]): KeyTakeaway[] {
  const seen = new Set<string>();
  return [...source]
    .sort((a, b) => a.order_no - b.order_no)
    .filter((item) => {
      const key = displayKey(item.title, item.description);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .map((item, index) => ({
      ...item,
      order_no: index + 1,
    }));
}

function displayKey(title: string | null | undefined, description: string | null | undefined): string {
  return `${normalizeDisplayText(title)}\n${normalizeDisplayText(description)}`;
}

function normalizeDisplayText(text: string | null | undefined): string {
  return (text ?? '').trim().replace(/\s+/g, ' ');
}

function normalizeNarrativeProfile(value: NarrativeProfile | undefined): NarrativeProfile {
  if (value != null) {
    return value;
  }
  return {
    profile_version: 'fallback',
    config_version: 'unknown',
    band: 'MIDDLE',
    band_tone: 'neutral',
    heatmap_pattern: 'RECO_EMERGING',
    display_flags: {
      show_negative_box: false,
      show_advantage_box: false,
      comparison_metric: 'MENTION_RATE',
      show_radar_baseline_gap: false,
      hide_empty_blocks: true,
      allow_competitor_overtake_claim: false,
    },
    lexicon_fallback: true,
    fallback: true,
    fallback_reason: 'missing_narrative_profile',
  };
}

function normalizeSceneCompetitorPressure(value: SceneCompetitorPressure | undefined): SceneCompetitorPressure {
  return {
    hv_reco_total: value?.hv_reco_total ?? 0,
    suppressed_scene_count: value?.suppressed_scene_count ?? 0,
    top_suppressing_competitor: value?.top_suppressing_competitor ?? null,
    items: value?.items ?? [],
  };
}

// ─────────────────────── meta 组装 ───────────────────────

function buildMeta(
  raw: RawSnapshotDTO,
  computed: ComputedSnapshotDTO,
  row: VersionRowMeta
): MergedViewMeta {
  const matchLevel = raw?.benchmarks_frozen?.match_level ?? 'EXACT';
  return {
    version_id: row.version_id,
    report_id: row.report_id,
    version_no: row.version_no,
    schema_version: row.schema_version,
    generation_status: row.generation_status,
    query_web_mode: row.query_web_mode ?? 'OFF',
    planned_query_count: row.planned_query_count ?? 0,
    planned_web_query_count: row.planned_web_query_count ?? 0,
    web_valid_query_count: row.web_valid_query_count ?? 0,
    effective_sample_count: row.effective_sample_count ?? 0,
    query_failed_count: row.query_failed_count ?? 0,
    analyze_failed_count: row.analyze_failed_count ?? 0,
    skipped_query_count: row.skipped_query_count ?? 0,
    degraded_excluded_sample_count: row.degraded_excluded_sample_count ?? 0,
    main_web_failure_code: row.main_web_failure_code ?? null,
    generated_at: raw?.meta?.generated_at ?? null,
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
    match_level: matchLevel,
    export_success_count: row.export_success_count,
    export_success_at: row.export_success_at ?? null,
    algorithm_version: computed?.meta?.algorithm_version ?? 'v1',
  };
}

function asArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}

function filterEffectivePlatforms(raw: RawSnapshotDTO): PlatformBreakdown[] {
  const degradedCodes = new Set<string>(raw?.test_summary?.degraded_platforms ?? []);
  for (const platform of asArray<PlatformBreakdown>(raw?.platform_breakdown)) {
    if (platform.is_degraded === true && platform.platform_code) {
      degradedCodes.add(platform.platform_code);
    }
  }
  return asArray<PlatformBreakdown>(raw?.platform_breakdown).filter(
    (platform) => !degradedCodes.has(platform.platform_code)
  );
}

function normalizeTestSummary(
  summary: TestSummary,
  platformBreakdown: PlatformBreakdown[]
): TestSummary {
  return {
    ...summary,
    total_platforms: platformBreakdown.length,
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

function resolveMarketBattleground(l3: MarketBattleground | null | undefined): MarketBattleground {
  return l3 ?? {
    topbar_title: '',
    topbar_right: '',
    page_title: '',
    page_kicker: '',
    market_card: {
      label: '',
      source: '',
      stats: [
        { value: '', unit: '', label: '' },
        { value: '', unit: '', label: '' },
        { value: '', unit: '', label: '' },
        { value: '', unit: '', label: '' },
      ],
      platform_label: '',
      platforms: [
        { name: '', value: '' },
        { name: '', value: '' },
        { name: '', value: '' },
      ],
      platform_suffix: '',
    },
    national_card: {
      label: '',
      value_prefix: '',
      value: '',
      unit: '',
      subtitle: '',
      calculation_label: '',
      rows: [
        { label: '', value: '', is_total: false },
        { label: '', value: '', is_total: false },
        { label: '', value: '', is_total: false },
        { label: '', value: '', is_total: true },
      ],
    },
    bridge_text: '',
    regional_card: {
      label: '',
      value_prefix: '',
      value: '',
      unit: '',
      subtitle: '',
      calculation_label: '',
      rows: [
        { label: '', value: '', is_total: false },
        { label: '', value: '', is_total: false },
        { label: '', value: '', is_total: false },
        { label: '', value: '', is_total: true },
      ],
    },
    narrative: {
      intro: '',
      questions: ['', '', ''],
      conclusion: '',
      brand_line_prefix: '',
      brand_name: '',
      brand_line_suffix: '',
    },
    footnote: '',
    footer_brand: '',
  };
}

function resolveHeatmapSummary(
  l3: HeatmapSummary | null | undefined,
  profile: NarrativeProfile
): HeatmapSummary {
  if (l3 != null && l3.summary && l3.color_legend) {
    return l3;
  }
  const pattern = profile.heatmap_pattern ?? 'RECO_EMERGING';
  switch (pattern) {
    case 'NEW_CUSTOMER_BLANK':
      return {
        heatmap_pattern: pattern,
        summary: '新顾客入口场景仍存在明显空白,需要优先补齐推荐、咨询和具体场景问题中的品牌出现。',
        color_legend: '颜色越深表示该场景下品牌越稳定出现;灰色表示该平台未参与或无有效样本。',
      };
    case 'RECO_UNSTABLE':
      return {
        heatmap_pattern: pattern,
        summary: '推荐场景已有出现,但平台间波动较大,说明 AI 对品牌的推荐信号还不稳定。',
        color_legend: '颜色差异体现不同平台的推荐稳定性差异;灰色表示该平台未参与或无有效样本。',
      };
    case 'BROAD_PRESENCE':
      return {
        heatmap_pattern: pattern,
        summary: '新老顾客场景均已有品牌出现,当前重点是保持稳定覆盖并补强局部短板。',
        color_legend: '颜色用于观察平台和场景之间的强弱差异,不是单一好坏判断。',
      };
    case 'RECO_EMERGING':
    default:
      return {
        heatmap_pattern: 'RECO_EMERGING',
        summary: '推荐场景开始出现品牌信号,但覆盖广度和强度仍需要继续放大。',
        color_legend: '颜色越深表示该场景信号越强;浅色表示仍处在建设初期。',
      };
  }
}

/**
 * ROI 免责声明默认模板。
 * L3.roi_disclaimer null → schema 默认描述。
 */
function resolveRoiDisclaimer(l3: string | null): string {
  if (l3 != null) return l3;
  return '以上为基于你当前得分与计划优化项设定的改进目标与情景测算,非保证结果;实际效果取决于执行、AI 平台变化与竞争情况。';
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
      evidence_text: l3?.evidence_text ?? renderDefaultFindingEvidence(l2),
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
 * finding 默认文案:基于 rule_code 的对客兜底。
 * 这里主要兜住旧报告或 L3 content 缺失时的展示,避免把 RULE_* 内部编码暴露给客户。
 */
interface FindingDisplayTemplate {
  title: string;
  description: string;
  evidence?: string;
}

const FINDING_DISPLAY_TEMPLATES: Record<string, FindingDisplayTemplate> = {
  RULE_COVERAGE_LOW_RECOMMEND: {
    title: '高价值问题覆盖不足',
    description:
      '在最接近成交决策的高价值问题中,品牌出现比例仍偏低。建议优先补齐推荐、咨询和具体场景问题的内容资产,让 AI 在用户主动询问时有稳定依据可以引用。',
    evidence: '高价值问题覆盖 {{covered_prompts}} / {{total_prompts}},缺口 {{missed_count}} 个'
  },
  RULE_BRAND_AWARENESS_LOW: {
    title: 'AI 对品牌的整体识别偏弱',
    description:
      '综合可见度仍处在偏低区间,说明 AI 对品牌基础信息、服务优势和可信来源的掌握不足。建议先补齐权威信息源、门店资料、服务项目和口碑内容,建立可被 AI 反复引用的品牌基础盘。',
    evidence: '综合得分 {{overall_score}},行业均值 {{industry_avg_overall}},Top1 {{top1_overall}}'
  },
  RULE_RECOMMENDATION_ABSENT: {
    title: '用户求推荐时品牌仍未稳定出现',
    description:
      '在推荐型高价值场景中,品牌缺席比例较高。该类问题通常对应用户正在筛选服务机构,建议优先补齐能被 AI 引用的品牌介绍、服务项目、案例和本地信源。',
    evidence: '推荐型高价值场景缺席 {{client_absent_count}} / {{hv_reco_total}},缺席率 {{absence_rate}}%'
  },
  RULE_COMPARE_GAP: {
    title: '被点名比较时优势表达不足',
    description:
      '当用户把品牌与竞品放在一起比较时,AI 对品牌的判断仍不够充分。建议补齐对比型内容,明确服务差异、专业资质、价格透明度和适用人群,减少用户决策阶段的信息空白。',
    evidence: '对比型问题覆盖 {{covered_prompts}} / {{total_prompts}},覆盖率 {{coverage_rate}}%'
  },
  RULE_PLATFORM_IMBALANCE: {
    title: '不同 AI 平台上的表现不均衡',
    description:
      '品牌在不同 AI 平台上的出现情况存在明显差异。建议复盘表现较好的平台内容来源,并将有效信息同步补齐到弱势平台更容易引用的公开信源中。',
    evidence: '最高 {{strong_platform_name}} {{strong_mention_rate}}%,最低 {{weak_platform_name}} {{weak_mention_rate}}%,差距 {{gap_pp}} 个百分点'
  },
  RULE_SCENE_MISS_HIGH_VALUE: {
    title: '关键决策场景仍有缺口',
    description:
      '部分高价值场景中,AI 还没有稳定提到品牌。这类场景往往对应用户正在选择服务机构的关键时刻,建议围绕缺失问题逐条建设内容,提升品牌进入答案的机会。',
    evidence: '缺失高价值场景 {{missed_count}} 个:{{missed_scenes_text}}'
  },
  RULE_COMPETITOR_PRESENT_CLIENT_ABSENT: {
    title: '竞品在场但品牌缺席',
    description:
      '在用户未点名品牌的推荐场景里,已有竞品进入 AI 答案,但品牌尚未稳定出现。建议针对这些场景补齐内容入口,先让品牌进入候选答案,再进一步争取靠前位置。',
    evidence: '竞品在场且品牌缺席 {{display_gap_count}} / {{hv_reco_total}} 个场景'
  },
  RULE_NATURAL_RECO_WEAK_BRAND_KNOWN: {
    title: '被点名时 AI 知道你,但用户没点名时 AI 几乎不主动推荐你',
    description:
      '品牌在被点名了解或比较时已有一定识别度,但在用户主动求推荐的场景中出现比例仍低于 20%。建议把已有品牌信息转化为推荐型内容,让 AI 在用户未点名时也能主动把品牌列入候选。',
    evidence: '推荐型高价值覆盖率 {{recommendation_rate}}%,认知/对比最高 {{known_rate}}%'
  },
  RULE_HIGH_VALUE_RECO_GAP: {
    title: '推荐型高价值问题仍有缺口',
    description:
      '推荐型高价值问题直接对应用户筛选服务机构的时刻。当前仍有较多问题未覆盖,建议按问题逐条补齐内容资产,提升品牌在自然推荐场景中的基础出现率。',
    evidence: '推荐型高价值覆盖 {{hv_reco_covered}} / {{hv_reco_total}},缺口 {{hv_reco_gap}} 个'
  },
  RULE_NEGATIVE_EVIDENCE: {
    title: 'AI 回答中出现负面反馈',
    description:
      'AI 已引用与品牌相关的负面表述。建议先核实来源,再用事实说明、服务改进和正向内容进行对冲,避免负面信息在后续回答中持续放大。',
    evidence: '负面反馈 {{negative_evidence_count}} 条,涉及 {{affected_platform_count}} 个平台'
  },
  RULE_LOW_SENTIMENT_SCORE: {
    title: '品牌正向印象不足',
    description:
      'AI 对品牌的表述以中性为主,正向评价不够稳定。建议补充真实案例、专业背书、用户评价和服务优势,让 AI 在回答中形成更清晰的正面认知。',
    evidence: '情感得分 {{sentiment_score}},正面 {{positive_count}} / 中性 {{neutral_count}} / 负面 {{negative_count}}'
  },
  RULE_BRAND_SENTIMENT_SAMPLE_THIN: {
    title: 'AI 还没有形成稳定的品牌情感印象',
    description:
      '品牌自身情感样本较少,不足以支撑稳定的正负面判断。建议先提升品牌在回答中的出现次数,再通过案例、评价和专业背书建立更明确的正向印象。',
    evidence: '品牌情感样本 {{brand_sentiment_sample_count}} 条'
  },
  RULE_PLATFORM_COVERAGE_NARROW: {
    title: '覆盖平台范围偏窄',
    description:
      '品牌只在部分 AI 平台中被提及,用户换一个 AI 工具后可能看不到品牌。建议针对未覆盖平台补齐公开内容、百科资料、问答内容和本地服务信息。',
    evidence: '已覆盖 {{covered_platform_count}} / {{total_platforms}} 个平台,未覆盖 {{uncovered_platform_count}} 个'
  },
  RULE_PLATFORM_COUNT_LOW: {
    title: '本次有效测试平台偏少',
    description:
      '本次可用测试平台数量偏少,会影响结果代表性。建议先排查平台接入与降级原因,在平台恢复后重新生成报告,让诊断结论更稳定。',
    evidence: '有效平台 {{effective_platforms}} 个,降级 {{degraded_count}} 个'
  },
  RULE_SINGLE_PLATFORM_DOMINANT: {
    title: '品牌曝光过度依赖单一平台',
    description:
      '品牌的主要出现机会集中在单一平台。一旦该平台答案来源或排序逻辑变化,整体可见度可能波动。建议同步建设其他平台可引用的信息源,降低单点依赖。',
    evidence: '{{dominant_platform_name}} 首推占比 {{dominant_ratio}}%'
  },
  RULE_PLATFORM_NEW_CUSTOMER_BLANK: {
    title: '新顾客入口场景存在空白',
    description:
      '推荐、问题和具体场景问题代表新顾客首次寻找服务机构的主要入口。当前三类场景整体出现率偏低,建议围绕新客常问问题建立内容矩阵,优先提升自然进入答案的概率。',
    evidence: '新顾客入口平均出现率 {{new_customer_avg_rate}}%'
  },
  RULE_PLATFORM_DEPTH_SHALLOW: {
    title: '平台出现深度待补齐',
    description:
      '品牌已在部分推荐型高价值场景出现,但仍主要停留在少数平台。代表场景「{{scene_example}}」中,品牌仅在 {{target_platforms}}/{{evaluated_platforms}} 个平台出现。建议把已验证有效的内容资产同步到更多 AI 平台,让出现从点状覆盖变成更稳定的多平台基本盘。',
    evidence: '浅覆盖场景 {{shallow_scene_count}}/{{hv_reco_total}}'
  },
  RULE_LONG_TAIL_SCENE_GAP: {
    title: '长尾场景可持续补齐',
    description:
      '核心高价值入口之外,中低价值问题仍有 {{long_tail_gap}} 个未覆盖场景。这类问题通常不需要抢在第一阶段处理,但适合在后续运营中持续补齐,拓宽 AI 能回答品牌的场景范围。建议优先补真实服务介绍、常见问题解答和可验证案例,避免使用虚构评价或未经证实的承诺。',
    evidence: '中价值缺口 {{mid_gap}}/{{mid_total}},低价值缺口 {{low_gap}}/{{low_total}}'
  },
  RULE_CONTENT_CONSISTENCY_CHECK: {
    title: '品牌信息一致性建议检查',
    description:
      '品牌已在 {{covered_platform_count}}/{{total_platforms}} 个平台出现,但平台间提及率仍有 {{gap_pp}} 个百分点差异。这类轻量差异适合通过一致性检查处理:核对不同平台对服务项目、优势证据和本地信息的描述是否一致。建议以真实资质、真实案例和真实服务流程为基础,统一可被 AI 引用的内容材料。',
    evidence: '最高 {{strong_mention_rate}}% / 最低 {{weak_mention_rate}}%(差距 {{gap_pp}} pp)'
  },
  RULE_PERIODIC_RETEST_MONITORING: {
    title: '周期复测与变化预警',
    description:
      'AI 回答、竞品在场和平台收录会持续变化。订阅期可持续执行{{service_action}},跟踪{{monitoring_focus}}。这不是当前诊断出的缺陷,而是后续持续运营的交付价值:定期发现变化,及时调整内容与平台动作。',
    evidence: '{{service_action}}: {{monitoring_focus}}'
  }
};

function renderDefaultFindingTitle(l2: OptimizationFinding): string {
  return FINDING_DISPLAY_TEMPLATES[l2.rule_code]?.title ?? '发现一项可优化机会';
}

function renderDefaultFindingDescription(l2: OptimizationFinding): string {
  return renderFindingTemplate(
    FINDING_DISPLAY_TEMPLATES[l2.rule_code]?.description ??
      '本项由报告数据自动识别,建议结合前文指标与平台表现制定优化动作。',
    l2.evidence_data
  );
}

function renderDefaultFindingEvidence(l2: OptimizationFinding): string {
  const template = FINDING_DISPLAY_TEMPLATES[l2.rule_code]?.evidence;
  if (!template) return '';
  const rendered = renderFindingTemplate(template, l2.evidence_data);
  return rendered.includes('{{') ? '' : rendered;
}

function renderFindingTemplate(template: string, evidence: Record<string, unknown> | null | undefined): string {
  return template.replace(/\{\{\s*([a-zA-Z0-9_]+)\s*\}\}/g, (_match, key: string) => {
    const value = evidence?.[key];
    if (value === null || value === undefined || value === '') return '—';
    if (Array.isArray(value)) return value.join('、');
    return String(value);
  });
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
  const planned = phase.planned_optimization_count ?? phase.total_optimization_count ?? 0;
  switch (phase.phase_no) {
    case 1:
      return planned > 0 ? `基础优化阶段,聚焦${planned}项关键改动` : '基础优化阶段';
    case 2:
      return planned > 0 ? `内容深化阶段,推进${planned}项优化` : '内容深化阶段';
    case 3:
      return planned > 0 ? `持续优化阶段,跟进${planned}项优化` : '巩固·监测阶段';
    default:
      return `阶段 ${phase.phase_no}`;
  }
}

function renderDefaultPhaseDescription(phase: RoiPhase): string {
  const planned = phase.planned_optimization_count ?? phase.total_optimization_count ?? 0;
  const target = phase.target_score_low != null && phase.target_score_high != null
    ? `${toIntRounded(phase.target_score_low)}-${toIntRounded(phase.target_score_high)} 分`
    : `${toIntRounded(phase.target_score)} 分`;
  return `${phase.duration_label}:目标 ${target},本阶段计划优化项 ${planned} 项`;
}

// ─────────────────────── competitors 合并 ───────────────────────

/**
 * 合并 L1 competitors × L3 competitor_scene_descriptions。
 *
 * 按当前提及次数 mention_count 降序输出,并重新生成展示 rank。
 * L1 最多 3 个竞品(schema maxItems=3),实际存在几个就输出几个。
 *
 * 特殊回退:L3.scene_advantages_polished 为 null 时回退 L1.scene_advantages_raw。
 * 这是 L3 唯一一处向 L1(非默认模板)回退的字段。
 *
 * 实现策略:
 * 1. L3 按 competitor_rank 建 map,保证场景描述仍按原始竞品 rank 关联
 * 2. L1 按 mention_count 降序排序,同分时按原始 rank 升序稳定兜底
 * 3. 输出时将展示 rank 重排为 1/2/3
 *
 * 契约意义:后端 MergeService 必须做同样的排序和展示 rank 重排。
 */
export function mergeCompetitors(
  l1Competitors: Competitor[],
  l3Descriptions: CompetitorSceneDescription[]
): MergedCompetitor[] {
  const l3ByRank = new Map<number, CompetitorSceneDescription>();
  for (const d of l3Descriptions) {
    l3ByRank.set(d.competitor_rank, d);
  }

  return [...l1Competitors]
    .sort((a, b) => {
      const mentionDiff = (b.mention_count ?? 0) - (a.mention_count ?? 0);
      if (mentionDiff !== 0) return mentionDiff;
      return a.rank - b.rank;
    })
    .map((c, index) => {
      const l3 = l3ByRank.get(c.rank);
      const polished = l3?.scene_advantages_polished;
      const useL3 = polished != null;

      return {
        rank: (index + 1) as 1 | 2 | 3,
        name: c.name,
        mention_count: c.mention_count,
        mention_rate: c.mention_rate,
        avg_ranking: c.avg_ranking,
        scene_advantages: useL3 ? polished! : c.scene_advantages_raw ?? [],
        scene_is_polished: useL3,
        comparison_verdict_count: c.comparison_verdict_count,
        target_preferred_count: c.target_preferred_count,
        competitor_preferred_count: c.competitor_preferred_count,
        tie_count: c.tie_count,
        unclear_count: c.unclear_count,
        target_preferred_rate: c.target_preferred_rate,
        competitor_preferred_rate: c.competitor_preferred_rate,
        comparison_advantages: c.comparison_advantages,
      };
    });
}
