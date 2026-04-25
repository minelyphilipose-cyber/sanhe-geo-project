/**
 * L2 计算结果层类型。
 *
 * 对应 Java 包:com.huanjing.geo.module.presale.dto.snapshot.computed
 * 对应 schema v1.2:$defs/computedSnapshot
 *
 * 边界契约:基于 L1 跑出的评分、聚合和规则引擎命中结果。只读。
 * 存储:MySQL presale_report_version.computed_snapshot_json。
 */

import type { SceneCoverageGroup } from './common';

/**
 * L2 顶层。
 */
export interface ComputedSnapshotDTO {
  meta?: ComputedSnapshotMeta;
  scores: Scores;
  intent_breakdown: IntentBreakdown[];
  scene_coverage: SceneCoverage;
  optimization_findings: OptimizationFinding[];
  roi_simulation: RoiSimulation;
  /**
   * 平台 × 意图 交叉提及率矩阵(β·2·补 新增,spec v3)。
   *
   * 全量硬约束:新生成 DONE 报告必须有 `平台数 × 5` 条;
   * 历史报告可能缺失(null/undefined/[]),mergeSnapshot 使用 `?? []` 兼容(见该文件 §7 注释)。
   *
   * 约定:外层按 platform_breakdown 顺序,内层按 intent_code 的固定顺序:
   *   RECOMMENDATION / COMPARISON / INQUIRY / COGNITIVE / SCENARIO
   *
   * 见契约 platform-intent-breakdown-spec-v3.md。
   */
  platform_intent_breakdown?: PlatformIntentCell[];
}

export interface ComputedSnapshotMeta {
  algorithm_version?: string;
}

/**
 * Schema v1.2 $.computed_snapshot.scores
 * 五维分 0-100。weights 是本次使用的权重冻结副本。
 */
export interface Scores {
  overall: number;
  mention: number;
  ranking: number | null;
  sentiment: number;
  coverage: number;
  weights: {
    mention: number;
    ranking: number;
    sentiment: number;
    coverage: number;
  };
}

/**
 * Schema v1.2 $.computed_snapshot.intent_breakdown[]
 * category / business_value 为中文字面值(schema 枚举),TS 侧保留 string。
 */
export interface IntentBreakdown {
  /** "推荐型" | "对比型" | "问题型" | "认知型" | "场景型"。 */
  category: string;
  /** "高" | "中" | "低"。 */
  business_value: string;
  total_prompts: number;
  covered_prompts: number;
  coverage_rate: number;
  avg_ranking: number | null;
}

/**
 * Schema v1.2 $.computed_snapshot.scene_coverage
 */
export interface SceneCoverage {
  high_value: SceneCoverageGroup;
  mid_value: SceneCoverageGroup;
  low_value: SceneCoverageGroup;
}

/**
 * 规则引擎命中的优化发现。
 * Schema v1.2 $.computed_snapshot.optimization_findings[]
 *
 * L2 只存命中结果,对客文案在 L3 optimization_findings_content 通过 finding_id 关联。
 * priority 稳定枚举,category 中文字面值。
 */
export interface OptimizationFinding {
  /** 本次报告内唯一,如 "F001"。 */
  finding_id: string;
  /** 触发规则编码,如 "RULE_COVERAGE_LOW_RECOMMEND"。 */
  rule_code: string;
  priority: FindingPriority;
  /** "基础设施" | "内容建设" | "关系建设" | "平台扩展"。 */
  category: string;
  /**
   * 规则触发时的结构化上下文数据,自由对象。
   * Schema additionalProperties: true,不同 rule_code 字段集不同。
   */
  evidence_data: Record<string, unknown>;
}

export type FindingPriority = 'HIGH' | 'MEDIUM' | 'LOW';

/**
 * Schema v1.2 $.computed_snapshot.roi_simulation
 * phases 严格 3 条。
 */
export interface RoiSimulation {
  current_score: number;
  target_score: number;
  estimated_uplift_percent: number;
  estimated_exposure_multiplier: number;
  /** 严格 3 条,phase_no 为 1/2/3。 */
  phases: [RoiPhase, RoiPhase, RoiPhase];
}

export interface RoiPhase {
  /** 1 | 2 | 3。 */
  phase_no: 1 | 2 | 3;
  /** 时长标签,如 "M1" / "M2-3" / "M4-6"。 */
  duration_label: string;
  target_score: number;
  uplift_from_previous: number;
  completed_optimization_count: number;
  total_optimization_count: number;
}

// ───────────────────────────────────────────────────────────
// β·2·补 新增:platform × intent 交叉数据
// ───────────────────────────────────────────────────────────

/**
 * 5 种意图类别的稳定英文编码。
 * Spec v3 §2.2 严格锁定,不允许扩展。
 */
export type IntentCode =
  | 'RECOMMENDATION'
  | 'COMPARISON'
  | 'INQUIRY'
  | 'COGNITIVE'
  | 'SCENARIO';

/**
 * 单个 (平台 × 意图) 交叉格子。
 * Schema v1.2 $defs/platformIntentCell
 *
 * 硬约束(详见 spec v3 §2.1 / §6):
 *   - mention_rate:
 *       if platform_prompt_count === null || <= 0: mention_rate === 0
 *       else: mention_rate === round(mention_count / platform_prompt_count * 100)
 *     后端使用 HALF_UP(四舍五入),前端不再二次 round。
 *   - platform_prompt_count === null 语义:该平台未参与该意图测试(不同于 0="测了但 0 样本")
 *   - 平台守恒律:同一 platform_code 下所有 cell 的 mention_count 之和 === platform_breakdown 里该平台的 mention_count
 */
export interface PlatformIntentCell {
  /** 平台编码,必须与 platform_breakdown.platform_code 完全一致(=== 比较,含大小写)。 */
  platform_code: string;
  /** 意图编码,稳定英文枚举,见 IntentCode。 */
  intent_code: IntentCode;
  /** 意图展示名,中文。后端白名单映射,前端 UI 可直接消费。 */
  intent_label: string;
  /** 该 (平台, 意图) 下被品牌提及的次数,>= 0 整数。 */
  mention_count: number;
  /** 该 (平台, 意图) 下的提及率,0-100 整数。 */
  mention_rate: number | null;
  /** 该意图类别的总 prompt 数(与同 intent_code 的其他 cell 相同,亦等于 intent_breakdown 对应 category.total_prompts)。 */
  total_prompts: number;
  /**
   * 该平台在该意图下的有效样本数(= mention_rate 分母)。
   *
   * - null:该平台未参与该意图测试,前端显示为 "—" 灰格
   * - 0:测了但 0 样本(极少,也显示为 "—" 灰格)
   * - 正整数:正常样本
   */
  platform_prompt_count: number | null;
  /** 对比型站队方向:target/tie/competitor/null;本阶段前端暂不消费。 */
  stance?: 'target' | 'tie' | 'competitor' | null;
}
