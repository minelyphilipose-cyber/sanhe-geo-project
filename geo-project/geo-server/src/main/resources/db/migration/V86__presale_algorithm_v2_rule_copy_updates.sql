UPDATE presale_optimization_rule
SET rule_name = '品牌综合可见度偏低',
    title_template = '品牌综合可见度偏低',
    description_template = '综合得分 {{overall_score}} 分,低于行业均值 {{industry_avg_overall}} 分,与行业 Top1 的 {{top1_overall}} 分存在较大差距。综合得分覆盖提及率、排名、情感、场景覆盖四个维度,偏低表明品牌在 AI 平台的整体可见度仍有提升空间。建议从基础认知建设入手,通过内容铺设、平台优化、负面管理多管齐下提升整体可见度。',
    evidence_template = '综合得分 {{overall_score}} 分(行业均值 {{industry_avg_overall}} / Top1 {{top1_overall}})'
WHERE rule_code = 'RULE_BRAND_AWARENESS_LOW';

UPDATE presale_optimization_rule
SET title_template = '对比型查询覆盖不足',
    description_template = '在 {{total_prompts}} 个对比型查询中,品牌形成有效对比判断 {{covered_prompts}} 个,覆盖率 {{coverage_rate}}%。对比型查询反映用户在决策阶段的信息需求,覆盖不足意味着 AI 在用户主动对比时未充分形成清晰立场。建议补齐"与竞品 X 相比"、"X 类型哪个好"等典型对比型场景的内容布局。',
    evidence_template = '对比型查询覆盖率 {{coverage_rate}}%({{covered_prompts}}/{{total_prompts}})'
WHERE rule_code = 'RULE_COMPARE_GAP';
