package com.huanjing.geo.module.presale.dto.snapshot.common;

/**
 * 基准值匹配等级。
 * <p>Schema v1.2 $.raw_snapshot.benchmarks_frozen.match_level</p>
 * <p>策略:稳定枚举值,使用 Java enum(决策 3C)。</p>
 */
public enum MatchLevel {
    /** 精确命中 (industry, industry_role)。 */
    EXACT,
    /** 回退到 (industry, '_ALL_') 行业级兜底,前端需展示回退警示条。 */
    FALLBACK_INDUSTRY,
    /** 回退到 (_ALL_, '_ALL_') 全局通用基准。 */
    FALLBACK_GLOBAL,
    /** 没有任何可用基准；报告继续生成但不展示比较结论。 */
    MISSING
}
