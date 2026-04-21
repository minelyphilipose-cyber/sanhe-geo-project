/**
 * 合并视图类型(前端 / PDF 消费)。
 *
 * 对应 Java 包:com.huanjing.geo.module.presale.dto.snapshot.merged
 *
 * 方案 A(扁平化):按前端消费视角重新组织,前端不感知三层。
 * 权威实现:后端 /api/presale/versions/{versionNo}/merged-view。
 * 前端 mergeSnapshot 仅 P1 mock 期作为非权威镜像,联调后标记 deprecated。
 */

import type { MatchLevel } from './common';
import type {
  BenchmarksFrozen,
  PlatformBreakdown,
  SentimentDetail,
  TestSummary,
} from './raw';
import type {
  IntentBreakdown,
  OptimizationFinding,
  RoiPhase,
  RoiSimulation,
  SceneCoverage,
  Scores,
} from './computed';
import type { ExecutiveSummary, KeyTakeaway } from './editable';

/**
 * 合并视图元数据(版本、冻结、降级、match_level 等全局信息)。
 * 字段对齐 V62 v4 真实列(presale_report_version 表)。
 */
export interface MergedViewMeta {
  /** presale_report_version.id(BIGINT),JSON 里用 number(注意 > 2^53 的 ID 需改 string)。 */
  version_id: number;
  /** presale_report_version.report_id(BIGINT)。 */
  report_id: number;
  /** presale_report_version.version_no(INT)。 */
  version_no: number;
  /** 固定 "v1.2"。 */
  schema_version: string;
  /** INIT/QUEUED/.../DONE/FAILED,前端仅在 DONE 时渲染完整报告。 */
  generation_status: string;
  /** frozen_at != null 派生。 */
  frozen: boolean;
  /** RFC3339 带 +08:00,可 null。 */
  frozen_at?: string | null;
  /** frozen_by(BIGINT,用户 ID)。 */
  frozen_by?: number | null;
  /** 当前阶段固定 "MANUAL"。 */
  frozen_reason?: string | null;
  /** RFC3339 带 +08:00,可 null。 */
  content_updated_at?: string | null;
  content_updated_by?: number | null;
  /** 对应 presale_report_version.is_degraded(TINYINT → boolean)。 */
  is_degraded: boolean;
  /** 与 L1.test_summary.degraded_platforms 冗余,此处提升到 meta 便于前端一处读取。 */
  degraded_platforms: string[];
  /** 从 L1.benchmarks_frozen.match_level 提升到 meta,便于前端警示条一处读取。 */
  match_level: MatchLevel;
  export_success_count: number;
  /** RFC3339 带 +08:00,可 null。 */
  export_success_at?: string | null;
}

/**
 * 合并视图(前端 / PDF 消费的权威扁平化视图)。
 *
 * 权威:后端 /merged-view 接口。
 * 前端 mergeSnapshot(见 utils/presale/merge-snapshot.ts)只作为 mock 期非权威镜像使用。
 */
export interface MergedViewDTO {
  meta: MergedViewMeta;

  // ─── 客户信息(L1.client_info 直出) ───
  brand_name: string;
  industry: string;
  industry_role: string;
  region: string;
  user_demand?: string | null;

  // ─── L1 事实直出 ───
  test_summary: TestSummary;
  platform_breakdown: PlatformBreakdown[];
  sentiment_detail: SentimentDetail;
  /** 含 match_level。 */
  benchmarks_frozen: BenchmarksFrozen;

  // ─── L2 计算结果直出 ───
  scores: Scores;
  intent_breakdown: IntentBreakdown[];
  scene_coverage: SceneCoverage;
  roi_simulation: RoiSimulation;

  // ─── L3 文案(已应用默认回退) ───
  report_title: string;
  report_subtitle: string;
  executive_summary: ExecutiveSummary;
  key_takeaways: KeyTakeaway[];
  roi_disclaimer: string;

  // ─── 合并产物 ───
  /** L2 findings × L3 content 合并,is_hidden 跳过,按 sort_order 排序。 */
  merged_findings: MergedFinding[];
  /** L2 phases × L3 phase_descriptions,严格 3 条按 phase_no 顺序。 */
  merged_phases: MergedPhase[];
  /** L1 competitors × L3 scene_descriptions,scene_advantages 带回退来源标记。 */
  merged_competitors: MergedCompetitor[];
}

/**
 * 优化发现合并条目。
 */
export interface MergedFinding {
  /** L2 原数据。 */
  finding: OptimizationFinding;
  /** L3.title 或默认模板。 */
  title: string;
  description: string;
  /** L3.evidence_text 或由 L2.evidence_data 模板渲染。 */
  evidence_text: string;
  /** 合并后保证非 null:L3.sort_order ?? L2 原序 index+1。 */
  sort_order: number;
}

/**
 * 阶段合并条目。
 */
export interface MergedPhase {
  /** L2 原始阶段数据。 */
  phase: RoiPhase;
  /** L3.title 或默认模板。 */
  title: string;
  description: string;
}

/**
 * 竞品合并条目。
 */
export interface MergedCompetitor {
  rank: 1 | 2 | 3;
  name: string;
  mention_count: number;
  mention_rate: number;
  avg_ranking: number | null;
  /**
   * 最终场景描述。
   * L3.scene_advantages_polished 非 null → 用 L3;
   * null → 回退 L1.competitors[rank-1].scene_advantages_raw。
   */
  scene_advantages: string[];
  /** true=来自 L3 运营润色;false=来自 L1 原始提取回退。 */
  scene_is_polished: boolean;
}
