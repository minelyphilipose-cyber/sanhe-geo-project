/**
 * L1 原始事实层类型。
 *
 * 对应 Java 包:com.huanjing.geo.module.presale.dto.snapshot.raw
 * 对应 schema v1.2:$defs/rawSnapshot
 *
 * 边界契约:只存 AI 测试聚合事实 + 基准值冻结副本。只读,生成时一次写入,永不修改。
 * 存储:MySQL presale_report_version.raw_snapshot_json(JSON 列)。
 *
 * 时间字段格式:RFC3339 带 +08:00 偏移,如 "2026-04-18T14:05:00+08:00"(对齐 schema date-time)。
 */

import type { MatchLevel, ScoreSet } from './common';

/**
 * L1 顶层。
 * Schema v1.2 $.raw_snapshot
 */
export interface RawSnapshotDTO {
  meta: RawMeta;
  client_info: ClientInfo;
  test_summary: TestSummary;
  platform_breakdown: PlatformBreakdown[];
  competitors: Competitor[];
  sentiment_detail: SentimentDetail;
  benchmarks_frozen: BenchmarksFrozen;
}

/**
 * Schema v1.2 $.raw_snapshot.meta
 */
export interface RawMeta {
  /** 报表主表 ID(presale_report.id,BIGINT)。 */
  report_id: number;
  /** 版本号(从 1 递增)。 */
  version_no: number;
  /** 生成完成时间,RFC3339 带 +08:00。 */
  generated_at: string;
  /** 生成总耗时(秒)。 */
  generation_duration_seconds: number;
  /** 评分公式版本,如 "v1.0"。 */
  formula_version: string;
}

/**
 * 客户填报信息冻结副本。
 * Schema v1.2 $.raw_snapshot.client_info
 * Required: brand_name, industry, industry_role, region;user_demand 可选。
 */
export interface ClientInfo {
  brand_name: string;
  industry: string;
  industry_role: string;
  region: string;
  /** 可为 null 或省略(v1.2 变更为可选)。 */
  user_demand?: string | null;
}

/**
 * Schema v1.2 $.raw_snapshot.test_summary
 * 9 字段全部 required。
 */
export interface TestSummary {
  total_prompts: number;
  total_platforms: number;
  /** 含两轮测试 + 分析,典型 660。 */
  total_calls: number;
  successful_calls: number;
  failed_calls: number;
  excluded_count: number;
  /** 1 或 2。 */
  rounds: 1 | 2;
  /** 是否整份报告降级(降级平台 ≥4)。 */
  is_degraded: boolean;
  /** 降级平台 platform_code 列表(平台级成功率 < 50%)。 */
  degraded_platforms: string[];
}

/**
 * 单平台测试事实。
 * Schema v1.2 $.raw_snapshot.platform_breakdown[]
 * sentiment_distribution 只统计第一轮 275 次,与 L1.sentiment_detail(两轮合计)不交叉校验。
 */
export interface PlatformBreakdown {
  platform_code: string;
  platform_name: string;
  total_tests: number;
  mention_count: number;
  /** 提及率百分比 0-100。 */
  mention_rate: number;
  /** 未被提及时为 null。 */
  avg_ranking: number | null;
  /** 首推次数(排名=1)。 */
  primary_recommendation_count: number;
  sentiment_distribution: {
    positive: number;
    neutral: number;
    negative: number;
  };
  is_degraded: boolean;
}

/**
 * Top3 竞品条目。
 * Schema v1.2 $.raw_snapshot.competitors[](maxItems=3)
 * scene_advantages_raw 是 LLM 原始提取,L3 competitor_scene_descriptions 为 null 时回退此字段。
 */
export interface Competitor {
  /** 排名 1-3。 */
  rank: 1 | 2 | 3;
  name: string;
  mention_count: number;
  mention_rate: number;
  avg_ranking: number | null;
  scene_advantages_raw?: string[];
}

/**
 * 情感明细(两轮合计)。
 * Schema v1.2 $.raw_snapshot.sentiment_detail
 */
export interface SentimentDetail {
  positive_count: number;
  neutral_count: number;
  negative_count: number;
  top_keywords?: SentimentKeyword[];
  negative_evidence?: NegativeEvidence[];
}

export type Sentiment = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';

export interface SentimentKeyword {
  keyword: string;
  frequency: number;
  sentiment: Sentiment;
  /** 词云字号。 */
  font_size?: number;
}

export interface NegativeEvidence {
  platform_code: string;
  platform_name: string;
  query: string;
  snippet: string;
  /** RFC3339 带 +08:00。 */
  tested_at: string;
}

/**
 * 基准值冻结副本(单个聚合对象,非列表)。
 * Schema v1.2 $.raw_snapshot.benchmarks_frozen
 *
 * match_level 在此处记录命中情况:
 * - EXACT: 精确命中 (industry, industry_role)
 * - FALLBACK_INDUSTRY: 回退到 (industry, '_ALL_'),此时 industry_role 字段值为 "_ALL_"
 */
export interface BenchmarksFrozen {
  industry: string;
  /** 回退时为 "_ALL_"。 */
  industry_role: string;
  match_level: MatchLevel;
  industry_avg: ScoreSet;
  top1: ScoreSet;
  /** 行业 Top10 综合分阈值。 */
  top10_score: number;
  confidence_level: ConfidenceLevel;
  source: BenchmarkSource;
  sample_size: number;
  industry_ranking: IndustryRanking;
}

export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';

/** 基准值计算来源。Java 侧叫 Source,TS 避开常见名称冲突改名。 */
export type BenchmarkSource = 'MANUAL' | 'AUTO_P50' | 'HYBRID';

export interface IndustryRanking {
  /** 本品牌排名(1 起)。 */
  position: number;
  /** 行业已诊断品牌总数。 */
  total: number;
}
