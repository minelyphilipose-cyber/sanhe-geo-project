-- =========================================================================
-- V64__seed_presale_optimization_rules_v1.sql
--
-- 售前报表 · 优化规则库 v1.0(10 条)
--
-- 数据来源:
--   * 5 条来自 report_data_mock_sample_v1_2.json 的 optimization_findings 示范
--   * 5 条为 P1·D 阶段基于 GEO 领域常识补全,覆盖 V62 category 全 4 档
--
-- 设计决策(路径 1C + 平台无关):
--   * 规则触发逻辑**不硬编码具体 platform_code**,只按相对关系触发
--     (最强 vs 最弱 / 提及率分布 / 首推占比 / 覆盖率等)
--   * 规则文案保持平台中性,不提"海外平台";具体平台名由 evidence_data
--     在运行时填充(如 {{weak_platform_name}} 从 evidence_data.weak_platforms 取)
--   * 原 mock 中 F004 RULE_PLATFORM_IMBALANCE 保留 rule_code,文案去海外化
--
-- trigger_expression 表达式:
--   * 使用 SpEL(Spring 原生,无需额外 DSL 解析器)
--   * 上下文约定(规则引擎调用时注入):
--       #l1          → RawSnapshotDTO(L1 原始事实层)
--       #l2          → ComputedSnapshotDTO(L2 计算结果层)
--       #benchmarks  → BenchmarksFrozen(L1.benchmarksFrozen 的快捷引用)
--   * 字段访问用 camelCase(Java DTO 字段规范),不是 snake_case
--   * 返回 Boolean,true 即触发规则
--
-- 文案模板:
--   * title_template / description_template / evidence_template
--   * 占位符 {{variable}} 由 evidence_data(规则触发时产出的 Map)填充
--   * evidence_data 的字段名使用 snake_case(对齐 v1.2 schema
--     $.computed_snapshot.optimization_findings[].evidence_data)
--
-- 阈值:
--   * 本批所有阈值按 GEO 领域常识设定,remark 字段标 TODO
--   * UAT 阶段由业务侧 review,不合适时直接 UPDATE trigger_expression
--   * 阈值调整不需要改 rule_code 也不需要派生新版本
--
-- category 分布(10 条):
--   基础设施 2   内容建设 3   关系建设 2   平台扩展 3
--
-- 执行前置:V62 v4 已执行(建表完成)。V63 可选(不依赖 prompt 库)。
-- Flyway 按文件名版本号顺序:V61 → V62 → V63 → V64 ← HEAD
-- =========================================================================


-- =========================================================================
-- Section 1. 基础设施类(2 条)· 品牌在 AI 生态中的基础可见度
-- =========================================================================

-- -------------------------------------------------------------------------
-- 1.1 RULE_COVERAGE_LOW_RECOMMEND  [来自 mock F001]
--   触发:推荐型高价值查询覆盖率 < 80%
--   默认优先级:HIGH
-- -------------------------------------------------------------------------
INSERT INTO presale_optimization_rule
    (rule_code, rule_name, category, default_priority,
     trigger_expression,
     title_template, description_template, evidence_template,
     enabled, sort_order, remark)
VALUES
    ('RULE_COVERAGE_LOW_RECOMMEND',
     '推荐型高价值覆盖率偏低',
     '基础设施', 'HIGH',
     -- SpEL:遍历 L2.intentBreakdown,找到 category='推荐型' 且 businessValue='高' 的那条,
     --       判断其 coverageRate 是否 < 80。^[...] 是 SpEL 选择首个匹配项。
     '#l2.intentBreakdown.^[category == ''推荐型'' && businessValue == ''高''].coverageRate < 80',
     '推荐型高价值查询覆盖率仅 {{coverage_rate}}%,低于行业 Top 水平',
     '推荐型查询是购买决策的最关键触点。您在此类查询中的覆盖率为 {{coverage_rate}}%,意味着约 {{uncovered_rate}}% 的高意向用户看不到您的品牌。',
     '{{total_prompts}} 个推荐型查询中覆盖 {{covered_prompts}} 个,Top1 竞品覆盖率 {{top_competitor_coverage_rate}}%',
     1, 101,
     'TODO: 业务侧 v1.0 UAT 后 review;阈值 80 基于 mock 70% 触发场景保留 10pp 余量'),

-- -------------------------------------------------------------------------
-- 1.2 RULE_BRAND_AWARENESS_LOW  [新增,P1·D 常识补充]
--   触发:综合总分(L2.scores.overall)< 50
--   默认优先级:HIGH
-- -------------------------------------------------------------------------
    ('RULE_BRAND_AWARENESS_LOW',
     '品牌 AI 可见度整体偏低',
     '基础设施', 'HIGH',
     '#l2.scores.overall < 50',
     '品牌 AI 可见度总分 {{overall_score}},整体处于偏低水平',
     '您的品牌在各 AI 平台的综合可见度为 {{overall_score}} 分,低于健康线 50 分。建议优先补齐基础信息披露,确保权威信源能够覆盖品牌核心事实。',
     '综合总分 {{overall_score}},行业平均 {{industry_avg_overall}},行业 Top1 {{top1_overall}}',
     1, 102,
     'TODO: 业务侧 v1.0 UAT 后 review;阈值 50 为通用警戒线,可按行业差异调整');


-- =========================================================================
-- Section 2. 内容建设类(3 条)· 围绕对比/场景/平台分布的内容体系
-- =========================================================================

-- -------------------------------------------------------------------------
-- 2.1 RULE_COMPARE_GAP  [来自 mock F002]
--   触发:对比型查询覆盖率 < 60%
--   默认优先级:HIGH
-- -------------------------------------------------------------------------
INSERT INTO presale_optimization_rule
    (rule_code, rule_name, category, default_priority,
     trigger_expression,
     title_template, description_template, evidence_template,
     enabled, sort_order, remark)
VALUES
    ('RULE_COMPARE_GAP',
     '对比型查询覆盖不足',
     '内容建设', 'HIGH',
     '#l2.intentBreakdown.^[category == ''对比型''].coverageRate < 60',
     '对比型查询覆盖严重不足,仅 {{coverage_rate}}%',
     '"X 品牌和 Y 品牌对比" 类查询是消费决策的关键环节。您在此类查询中的覆盖率仅 {{coverage_rate}}%,意味着大多数用户在对比选型时看不到您的品牌。',
     '{{total_prompts}} 个对比型查询中仅覆盖 {{covered_prompts}} 个',
     1, 201,
     'TODO: 业务侧 v1.0 UAT 后 review;阈值 60 基于 mock 40% 触发场景留余量'),

-- -------------------------------------------------------------------------
-- 2.2 RULE_PLATFORM_IMBALANCE  [来自 mock F004,去海外化]
--   触发:最强平台 mention_rate - 最弱平台 mention_rate > 30 百分点
--   默认优先级:MEDIUM
--
--   文案去海外化说明:
--     原 mock 标题 "海外平台表现明显弱于国内平台" 带地域偏见。
--     本版保留 rule_code,文案改为中性"部分平台表现偏弱"。
--     最强/最弱平台名由 evidence_data.strong_platforms / weak_platforms 动态填充。
-- -------------------------------------------------------------------------
    ('RULE_PLATFORM_IMBALANCE',
     '平台间品牌识别表现失衡',
     '内容建设', 'MEDIUM',
     -- SpEL:max(mentionRate) - min(mentionRate) > 30
     -- 说明:SpEL 投影 ![mentionRate] 返回 List<Double>,转成 Stream 后求 max/min。
     -- 生产代码 RuleEngine 建议先把 is_degraded=true 的平台过滤掉再传入 ctx,
     -- 此处表达式为简化版,以降级平台的 mentionRate 可能为 null 为前提(用 ?[#this != null] 过滤)。
     '#l1.platformBreakdown.![mentionRate].?[#this != null].stream().mapToDouble(T(java.lang.Double)::doubleValue).max().orElse(0.0) - #l1.platformBreakdown.![mentionRate].?[#this != null].stream().mapToDouble(T(java.lang.Double)::doubleValue).min().orElse(0.0) > 30',
     '部分 AI 平台对品牌的识别明显偏弱,存在平台间优化空间',
     '在测试的 {{total_platforms}} 个 AI 平台中,表现最强的 {{strong_platform_name}} 提及率达 {{strong_mention_rate}}%,而表现最弱的 {{weak_platform_name}} 仅 {{weak_mention_rate}}%,差距 {{gap_pp}} 个百分点。建议针对弱势平台定向补充内容和信源。',
     '弱势平台:{{weak_platforms_text}};强势平台:{{strong_platforms_text}}',
     1, 202,
     'TODO: 业务侧 v1.0 UAT 后 review;阈值 30pp 为平台间显著失衡的经验门槛,可按实际观测调整'),

-- -------------------------------------------------------------------------
-- 2.3 RULE_SCENE_MISS_HIGH_VALUE  [来自 mock F005]
--   触发:高价值场景缺失数 >= 2
--   默认优先级:MEDIUM
-- -------------------------------------------------------------------------
    ('RULE_SCENE_MISS_HIGH_VALUE',
     '高价值场景查询持续缺席',
     '内容建设', 'MEDIUM',
     -- SpEL:L2.sceneCoverage.highValue.total - covered >= 2
     '(#l2.sceneCoverage.highValue.total - #l2.sceneCoverage.highValue.covered) >= 2',
     '{{missed_count}} 个高价值场景持续缺席',
     '高价值场景(如地域 + 产品类别组合查询)是用户决策的关键触点,竞品在这些场景中均有覆盖而您未出现。建议对缺失场景进行针对性内容建设。',
     '高价值场景缺失 {{missed_count}} 个:{{missed_scenes_text}}',
     1, 203,
     'TODO: 业务侧 v1.0 UAT 后 review;阈值 2 为缺失敏感度,单个缺失不触发以避免规则过敏');


-- =========================================================================
-- Section 3. 关系建设类(2 条)· 负面信息与情感维度
-- =========================================================================

-- -------------------------------------------------------------------------
-- 3.1 RULE_NEGATIVE_EVIDENCE  [来自 mock F003]
--   触发:负面提及总数 >= 1
--   默认优先级:HIGH
--
--   说明:负面信息只要出现就值得处理(保守策略)。
-- -------------------------------------------------------------------------
INSERT INTO presale_optimization_rule
    (rule_code, rule_name, category, default_priority,
     trigger_expression,
     title_template, description_template, evidence_template,
     enabled, sort_order, remark)
VALUES
    ('RULE_NEGATIVE_EVIDENCE',
     'AI 回答中出现负面信息',
     '关系建设', 'HIGH',
     '#l1.sentimentDetail.negativeCount >= 1',
     '负面信息在 AI 回答中被引用',
     '关于 {{key_topic}} 的负面内容在 {{affected_platform_count}} 个平台的回答中出现,需要溯源处理。AI 平台倾向于复用权威信源,负面信息一旦被引用会持续影响品牌判断。',
     '{{affected_platforms_text}} 出现负面提及 {{negative_count}} 次,主要话题:{{key_topic}}',
     1, 301,
     'TODO: 业务侧 v1.0 UAT 后 review;阈值 1 为保守策略,有就触发;若误报多可调为 >= 2'),

-- -------------------------------------------------------------------------
-- 3.2 RULE_LOW_SENTIMENT_SCORE  [新增,P1·D 常识补充]
--   触发:L2.scores.sentiment < 60
--   默认优先级:MEDIUM
-- -------------------------------------------------------------------------
    ('RULE_LOW_SENTIMENT_SCORE',
     '情感维度得分偏低',
     '关系建设', 'MEDIUM',
     '#l2.scores.sentiment < 60',
     '情感倾向评分 {{sentiment_score}},整体舆情偏中性或偏弱',
     '您的品牌在 AI 回答中的情感倾向得分为 {{sentiment_score}} 分,低于健康线 60 分。这反映 AI 在描述品牌时较少使用积极表达,建议推动正面舆情内容建设。',
     '情感分 {{sentiment_score}};正面提及 {{positive_count}},中性 {{neutral_count}},负面 {{negative_count}}',
     1, 302,
     'TODO: 业务侧 v1.0 UAT 后 review;阈值 60 为通用警戒线,与 BRAND_AWARENESS_LOW 的 overall 阈值保持一致量级');


-- =========================================================================
-- Section 4. 平台扩展类(3 条)· 平台覆盖面与分布健康度
-- =========================================================================

-- -------------------------------------------------------------------------
-- 4.1 RULE_PLATFORM_COVERAGE_NARROW  [新增]
--   触发:有效覆盖平台数 < 总平台数的 60%
--   说明:"有效覆盖" = mention_count > 0 的平台;避免把 is_degraded 算进来
--   默认优先级:MEDIUM
-- -------------------------------------------------------------------------
INSERT INTO presale_optimization_rule
    (rule_code, rule_name, category, default_priority,
     trigger_expression,
     title_template, description_template, evidence_template,
     enabled, sort_order, remark)
VALUES
    ('RULE_PLATFORM_COVERAGE_NARROW',
     '平台覆盖面偏窄',
     '平台扩展', 'MEDIUM',
     -- SpEL:有提及的平台数 / 总平台数 < 0.6
     '(#l1.platformBreakdown.?[mentionCount > 0].size() * 1.0 / #l1.testSummary.totalPlatforms) < 0.6',
     '品牌仅在 {{covered_platform_count}}/{{total_platforms}} 个平台被提及,覆盖面偏窄',
     '您的品牌在不到 60% 的受测 AI 平台上出现,意味着用户在不同 AI 工具间切换时可能看不到您。建议排查未覆盖平台对应的信源差异。',
     '覆盖平台 {{covered_platform_count}} 个,未覆盖平台 {{uncovered_platform_count}} 个:{{uncovered_platforms_text}}',
     1, 401,
     'TODO: 业务侧 v1.0 UAT 后 review;阈值 60% = 6/11 为"多数平台都能覆盖"的基线'),

-- -------------------------------------------------------------------------
-- 4.2 RULE_PLATFORM_COUNT_LOW  [新增]
--   触发:非降级平台总数 < 8
--   说明:测试环境本身可用平台就少,不是内容问题而是环境问题,运营侧需关注
--   默认优先级:LOW(对客交付次要,对运营运维主要)
-- -------------------------------------------------------------------------
    ('RULE_PLATFORM_COUNT_LOW',
     '可用测试平台数偏少',
     '平台扩展', 'LOW',
     -- 非降级平台数 = totalPlatforms - degradedPlatforms 列表 size
     '(#l1.testSummary.totalPlatforms - #l1.testSummary.degradedPlatforms.size()) < 8',
     '本次测试有效平台仅 {{effective_platforms}} 个,覆盖样本偏少',
     '本次测试中可用的 AI 平台数量偏少,{{degraded_count}} 个平台处于降级状态。这可能影响报告结论的代表性,建议排查平台可用性并择机重新生成报告。',
     '有效平台 {{effective_platforms}} 个;降级平台 {{degraded_count}} 个:{{degraded_platforms_text}}',
     1, 402,
     'TODO: 业务侧 v1.0 UAT 后 review;阈值 8 = 11 平台 - 3 宽容降级;若调用平台清单变化需同步'),

-- -------------------------------------------------------------------------
-- 4.3 RULE_SINGLE_PLATFORM_DOMINANT  [新增]
--   触发:单一平台首推次数占总首推次数 > 60%
--   默认优先级:MEDIUM
-- -------------------------------------------------------------------------
    ('RULE_SINGLE_PLATFORM_DOMINANT',
     '首推过度集中单一平台',
     '平台扩展', 'MEDIUM',
     -- SpEL:(sum(primaryRecommendationCount) > 0) && (max > sum * 0.6)
     -- 保护性判断:sum > 0 才计算占比,避免全平台零首推时误触发
     '#l1.platformBreakdown.![primaryRecommendationCount].stream().mapToInt(T(java.lang.Integer)::intValue).sum() > 0 && #l1.platformBreakdown.![primaryRecommendationCount].stream().mapToInt(T(java.lang.Integer)::intValue).max().asInt > #l1.platformBreakdown.![primaryRecommendationCount].stream().mapToInt(T(java.lang.Integer)::intValue).sum() * 0.6',
     '首推次数过度集中在单一平台({{dominant_platform_name}} 占 {{dominant_ratio}}%)',
     '您的品牌首推次数主要来自 {{dominant_platform_name}},占比 {{dominant_ratio}}%。过度依赖单一平台意味着该平台一旦权重下降,品牌曝光会显著受影响。建议推动其他平台的首推表现。',
     '{{dominant_platform_name}}: 首推 {{dominant_count}} 次 / 总首推 {{total_primary}} 次 = {{dominant_ratio}}%',
     1, 403,
     'TODO: 业务侧 v1.0 UAT 后 review;阈值 60% 为常见过度集中线;可按实际平台数量调整');


-- =========================================================================
-- Section 5. 数量校验(运行时 SELECT 可验证,不强制)
-- =========================================================================
-- 预期结果:
--   SELECT COUNT(*) FROM presale_optimization_rule;                              -- 10
--   SELECT category, COUNT(*) FROM presale_optimization_rule GROUP BY category;
--     基础设施 2   内容建设 3   关系建设 2   平台扩展 3
--   SELECT default_priority, COUNT(*) FROM presale_optimization_rule GROUP BY default_priority;
--     HIGH 4   MEDIUM 5   LOW 1
--   SELECT COUNT(*) FROM presale_optimization_rule WHERE trigger_expression LIKE '%chatgpt%' OR trigger_expression LIKE '%claude%' OR trigger_expression LIKE '%gemini%';  -- 0 (平台无关)
--   SELECT COUNT(*) FROM presale_optimization_rule WHERE remark LIKE 'TODO%';     -- 10
-- =========================================================================
