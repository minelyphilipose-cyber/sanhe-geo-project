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
  scores: Scores;
  intent_breakdown: IntentBreakdown[];
  scene_coverage: SceneCoverage;
  optimization_findings: OptimizationFinding[];
  roi_simulation: RoiSimulation;
}

/**
 * Schema v1.2 $.computed_snapshot.scores
 * 五维分 0-100。weights 是本次使用的权重冻结副本。
 */
export interface Scores {
  overall: number;
  mention: number;
  ranking: number;
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
