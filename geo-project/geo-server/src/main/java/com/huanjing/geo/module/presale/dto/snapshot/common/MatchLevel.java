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
    FALLBACK_INDUSTRY
}
