-- V220__presale_low_priority_operational_rules.sql
-- 目的:补充 LOW 层的可执行补强项与持续运营价值项。
-- 约束:
--   1. 不改 scores / roi / scene_coverage / scene_competitor_pressure 底层计算。
--   2. RULE_PLATFORM_COUNT_LOW 转为内部可靠性信号,不再作为对客优化项展示。
--   3. LOW 规则与上档规则的互斥由规则引擎 fired-set 过滤兜底。

INSERT INTO presale_optimization_rule
    (rule_code, rule_name, category, default_priority,
     trigger_expression,
     title_template, description_template, evidence_template,
     enabled, sort_order, remark)
VALUES
    ('RULE_PLATFORM_DEPTH_SHALLOW',
     '平台出现深度待补齐',
     '平台扩展', 'LOW',
     '#l2.sceneCompetitorPressure != null && #l2.sceneCompetitorPressure.items != null && #l2.sceneCompetitorPressure.items.?[targetMentionedPlatformCount != null && targetMentionedPlatformCount > 0 && platformsEvaluated != null && targetMentionedPlatformCount < (platformsEvaluated * 0.5)].size() >= 1',
     '品牌已出现,但平台深度仍可补齐',
     '品牌已在部分推荐型高价值场景出现,但覆盖平台数仍偏少。建议把已被验证有效的内容资产同步到更多 AI 平台,让出现从少数平台扩展为更稳定的多平台基本盘。',
     '浅覆盖场景 {{shallow_scene_count}} 个,代表场景 {{scene_example}}',
     1, 405,
     'LOW 诊断型;未命中平台覆盖面窄/平台失衡/单平台集中等上档平台规则时保留'),

    ('RULE_LONG_TAIL_SCENE_GAP',
     '长尾场景可持续补齐',
     '内容建设', 'LOW',
     '#l2.sceneCoverage != null && ((#l2.sceneCoverage.midValue != null ? ((#l2.sceneCoverage.midValue.total ?: 0) - (#l2.sceneCoverage.midValue.covered ?: 0)) : 0) + (#l2.sceneCoverage.lowValue != null ? ((#l2.sceneCoverage.lowValue.total ?: 0) - (#l2.sceneCoverage.lowValue.covered ?: 0)) : 0)) >= 2',
     '中低价值长尾问题仍有补充空间',
     '核心高价值入口之外,中低价值问题仍有未覆盖场景。它们通常不是第一优先级,但适合在项目后续阶段持续补齐,用来拓宽 AI 能回答品牌的场景范围。',
     '长尾缺口 {{long_tail_gap}} 个(中价值 {{mid_gap}}/{{mid_total}},低价值 {{low_gap}}/{{low_total}})',
     1, 406,
     'LOW 诊断型;高价值缺口类上档规则命中时由引擎剔除,避免同一覆盖问题双列'),

    ('RULE_CONTENT_CONSISTENCY_CHECK',
     '品牌信息一致性建议检查',
     '内容建设', 'LOW',
     '#l1.platformBreakdown != null && #l1.platformBreakdown.?[isDegraded != true && mentionCount != null && mentionCount > 0].size() >= 2',
     '多平台表达需要做一致性检查',
     '品牌已在多个 AI 平台出现,适合进入内容一致性检查阶段。建议核对不同平台对服务项目、优势证据和本地信息的描述是否一致,避免用户在不同入口看到不一致的品牌印象。',
     '已出现平台 {{covered_platform_count}}/{{total_platforms}},最高/最低提及率差 {{gap_pp}}pp',
     1, 407,
     'LOW 诊断型;平台失衡/覆盖面窄/单平台集中命中时由引擎剔除'),

    ('RULE_PERIODIC_RETEST_MONITORING',
     '周期复测与变化预警',
     '平台扩展', 'LOW',
     '#l2.scores != null && #l2.scores.overall != null && #l2.scores.overall >= 50',
     '持续复测可捕捉 AI 回答变化',
     'AI 回答、竞品在场和平台收录会持续变化。订阅期建议保留周期复测与变化预警,持续跟踪核心推荐场景、竞品进入和 AI 回答口径变化。',
     '{{service_action}}:{{monitoring_focus}}',
     1, 408,
     'LOW 运营价值型;不是诊断缺陷,用于说明持续合作/订阅期交付价值')
ON DUPLICATE KEY UPDATE
    rule_name = VALUES(rule_name),
    category = VALUES(category),
    default_priority = VALUES(default_priority),
    trigger_expression = VALUES(trigger_expression),
    title_template = VALUES(title_template),
    description_template = VALUES(description_template),
    evidence_template = VALUES(evidence_template),
    enabled = VALUES(enabled),
    sort_order = VALUES(sort_order),
    remark = VALUES(remark);
