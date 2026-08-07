/**
 * 售前报表共享值对象 / 枚举。
 *
 * 对应 Java 包:com.huanjing.geo.module.presale.dto.snapshot.common
 * 对应 schema v1.2:$defs/scoreSet, $defs/sceneCoverageGroup, $defs/sceneQueryItem, $defs/sceneQueryMissing
 *
 * 命名规则:JSON 键为 snake_case,TS 类型名为 PascalCase(对齐 Java 类名,含 DTO 后缀)。
 */

/**
 * 基准值匹配等级。
 * Schema v1.2 $.raw_snapshot.benchmarks_frozen.match_level
 */
export type MatchLevel = 'EXACT' | 'FALLBACK_INDUSTRY' | 'FALLBACK_GLOBAL' | 'MISSING';

/**
 * 五维评分集。
 * Schema v1.2 $defs/scoreSet
 */
export interface ScoreSet {
  overall: number;
  mention: number;
  ranking: number | null;
  sentiment: number;
  coverage: number;
}

/**
 * 场景覆盖分组。
 * Schema v1.2 $defs/sceneCoverageGroup
 * 被 L2.scene_coverage.{high_value, mid_value, low_value} 引用。
 */
export interface SceneCoverageGroup {
  total: number;
  covered: number;
  coverage_rate: number;
  coverage?: CoverageStats;
  natural_coverage?: CoverageStats;
  judge_coverage?: CoverageStats;
  covered_queries?: SceneQueryItem[];
  missing_queries?: SceneQueryMissing[];
}

export interface CoverageStats {
  total: number;
  covered: number;
  coverage_rate: number;
}

/**
 * 已覆盖的场景查询条目。
 * Schema v1.2 $defs/sceneQueryItem
 * category 中文字面值:推荐型/对比型/问题型/认知型/场景型。
 */
export interface SceneQueryItem {
  prompt_code: string;
  prompt_content: string;
  category: string;
}

/**
 * 未覆盖的场景查询条目(附竞品覆盖情况)。
 * Schema v1.2 $defs/sceneQueryMissing
 */
export interface SceneQueryMissing {
  prompt_code: string;
  prompt_content: string;
  category: string;
  top_competitor_coverage: string[];
}
