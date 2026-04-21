package com.huanjing.geo.module.presale.ruleengine;

import java.util.Map;

/**
 * evidence_data 构建器。每个 rule_code 对应一个实现类。
 *
 * <p>契约(详见 docs/presale/p1e-rule-engine-design-v1.md §5):</p>
 * <ul>
 *   <li>返回的 Map 键名必须精确匹配 README_P1D 的 evidence_data 必填字段表</li>
 *   <li>键名使用 snake_case</li>
 *   <li>缺失可选值 → 省略该键(不放占位符)</li>
 *   <li>缺失必填值 → 回退到安全默认(0 / 空串 / 空列表文本)</li>
 * </ul>
 *
 * <p>Spring 扫描所有实现类自动注入 {@link EvidenceDataBuilderRegistry}。</p>
 */
public interface EvidenceDataBuilder {

    /**
     * 声明本 Builder 处理的 rule_code。
     * Registry 会按此返回值建立 rule_code → Builder 的分派映射。
     */
    String supportRuleCode();

    /**
     * 构建 evidence_data。
     *
     * @param input 规则命中时的上下文输入
     * @return 非 null Map。空数据也应返回空 Map,不返回 null。
     */
    Map<String, Object> build(RuleBuildInput input);
}
