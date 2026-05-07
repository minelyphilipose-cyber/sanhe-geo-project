UPDATE presale_optimization_rule
SET
    rule_name = '高价值场景覆盖待激活',
    trigger_expression = '#l2.sceneCoverage.highValue.coverageRate < 80',
    title_template = '高价值场景覆盖率 {{coverage_rate}}%,核心决策入口待激活',
    description_template = '高价值场景是用户决策路径上的关键触点。您在此类场景中的覆盖率为 {{coverage_rate}}%,仍有 {{missed_count}} 个核心决策场景待激活。',
    evidence_template = '{{total_prompts}} 个高价值场景中覆盖 {{covered_prompts}} 个'
WHERE rule_code = 'RULE_COVERAGE_LOW_RECOMMEND';
