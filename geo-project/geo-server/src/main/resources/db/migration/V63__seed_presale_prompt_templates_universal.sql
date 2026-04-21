-- =========================================================================
-- V63__seed_presale_prompt_templates_universal.sql
--
-- 售前报表 · Prompt 库 v1.0 通用模板种子数据(首批 30 条)
--
-- 数据来源:GEO售前诊断报告_Prompt库_v1_0.md "三、通用 Prompt 模板"
-- 表结构:presale_prompt_template(V62 v4)
-- 对齐契约:report_data_schema_v1_2.json + 售前报表功能__开发实施计划_v1_3
--
-- 本脚本导入策略:
--   * 路径 A + 1c:仅首批通用模板,industry='_ALL_' / industry_role='_ALL_',
--     不做分行业/分身份细分。分行业 390 条(12 行业)留给后续增量脚本。
--   * A1(竞品变量改写):原 Prompt 库使用 {competitor_a}/{competitor_b}/{competitor_c}
--     三变量,本脚本按 v1.2 契约统一改写为单变量 {competitor}(Top3 拼接文本一次注入)。
--   * X1(凑足 5 条含竞品):原通用库 CMP_GEN_001/002/003 本就含竞品变量,
--     另改写 CMP_GEN_004/005 使其也含 {competitor},达到 v1.2 契约"第二轮 5 条"的数量。
--
-- 本批 30 条分布(对齐 v1.2 契约 275+55=660 调用):
--   第一轮(has_competitor_var=0,每条 × 11 平台 = 275 次调用):
--     REC_GEN_001 ~ REC_GEN_010   推荐型 10 条
--     PRB_GEN_001 ~ PRB_GEN_005   问题型 5 条
--     CGN_GEN_001 ~ CGN_GEN_005   认知型 5 条
--     SCN_GEN_001 ~ SCN_GEN_005   场景型 5 条
--     合计 25 条
--   第二轮(has_competitor_var=1,每条 × 11 平台 = 55 次调用):
--     CMP_GEN_001 ~ CMP_GEN_005   对比型 5 条(全部含 {competitor})
--     合计 5 条
--
-- 变量占位符(对齐 v1.2 渲染规则):
--   {brand}           客户品牌名,来自 presale_report.brand_name
--   {industry}        客户行业,来自 presale_report.industry(字典 presale_industry)
--   {industry_role}   客户身份,来自 presale_report.industry_role
--   {region}          客户地区,来自 presale_report.region
--   {product}         核心产品/服务,运营补全(本 SQL 不强制)
--   {competitor}      Top3 竞品拼接文本(如"巴奴毛肚火锅、呷哺呷哺、小龙坎")
--                     仅在第二轮 CompetitorDetector 产出 Top3 后注入,一次替换,不逐个展开。
--
-- TODO / 业务 review 项(SQL 未硬阻塞,运营侧可在 UAT 前调整):
--   1. applicable_industries / applicable_roles 在 V62 v4 表结构里被拆为
--      industry / industry_role 两个标量字段。本批全通用('_ALL_'),
--      分行业/分身份版本后续增量脚本需要中文→字典key的映射表。
--   2. 原 Prompt 库提到"电商品牌"/"SaaS"等字面身份,V62 字典 presale_industry_role
--      目前有 8 个 key(chain_brand/single_store/franchise/manufacturer/dealer/
--      platform/service_provider/kol)。后续导入分行业 prompt 时,需业务侧确认:
--        - "电商品牌" → chain_brand ?
--        - "SaaS"     → service_provider ? 或新增字典项?
--        - "门店"     → single_store ?
--        - "工厂"     → manufacturer ?
--        - "电商平台" → platform ?
--      本 V63 通用批不触发此映射,分行业增量脚本需先解决。
--   3. 通用库 CMP_GEN_004 / CMP_GEN_005 为 v1.2 改写版(原 v1.0 不含竞品变量,
--      为凑足第二轮 5 条改写加入 {competitor})。业务侧 review prompt 文本语感时,
--      若觉得改写后不够自然,可在 UAT 前调整 prompt_content 字段(prompt_code 保持)。
--
-- 执行前置:V62 v4 必须已执行(建表 + 字典初始化完成)。
-- 执行方式:Flyway 按文件名版本号自动检出 V63,与 V62 串行。
-- =========================================================================

-- =========================================================================
-- Section 1. 第一轮 25 条(has_competitor_var = 0)
-- =========================================================================

-- -------------------------------------------------------------------------
-- 1.1 通用推荐型 REC_GEN(10 条) · 商业价值:高
-- -------------------------------------------------------------------------
INSERT INTO presale_prompt_template
    (prompt_code, industry, industry_role, category, business_value,
     prompt_content, has_competitor_var, enabled, sort_order, remark)
VALUES
    ('REC_GEN_001', '_ALL_', '_ALL_', '推荐型', '高',
     '在{region}{industry}行业,有哪些比较好的{industry_role}推荐?',
     0, 1, 101, '通用推荐型 · 来源 GEO_Prompt库_v1.0'),
    ('REC_GEN_002', '_ALL_', '_ALL_', '推荐型', '高',
     '{region}{industry}行业排名前十的品牌有哪些?',
     0, 1, 102, '通用推荐型 · 来源 GEO_Prompt库_v1.0'),
    ('REC_GEN_003', '_ALL_', '_ALL_', '推荐型', '高',
     '想找{region}靠谱的{product}{industry_role},有什么推荐?',
     0, 1, 103, '通用推荐型 · 来源 GEO_Prompt库_v1.0'),
    ('REC_GEN_004', '_ALL_', '_ALL_', '推荐型', '高',
     '{region}有哪些值得信赖的{industry}品牌?',
     0, 1, 104, '通用推荐型 · 来源 GEO_Prompt库_v1.0'),
    ('REC_GEN_005', '_ALL_', '_ALL_', '推荐型', '高',
     '推荐几家{region}口碑好的{industry}{industry_role}',
     0, 1, 105, '通用推荐型 · 来源 GEO_Prompt库_v1.0'),
    ('REC_GEN_006', '_ALL_', '_ALL_', '推荐型', '高',
     '{region}{industry}领域有哪些知名度高的{industry_role}?',
     0, 1, 106, '通用推荐型 · 来源 GEO_Prompt库_v1.0'),
    ('REC_GEN_007', '_ALL_', '_ALL_', '推荐型', '高',
     '想做{industry}加盟,有什么好的品牌推荐?',
     0, 1, 107, '通用推荐型 · 来源 GEO_Prompt库_v1.0'),
    ('REC_GEN_008', '_ALL_', '_ALL_', '推荐型', '高',
     '{region}{industry}行业的头部品牌有哪些?',
     0, 1, 108, '通用推荐型 · 来源 GEO_Prompt库_v1.0'),
    ('REC_GEN_009', '_ALL_', '_ALL_', '推荐型', '高',
     '{region}做{product}比较好的{industry_role}有哪些?',
     0, 1, 109, '通用推荐型 · 来源 GEO_Prompt库_v1.0'),
    ('REC_GEN_010', '_ALL_', '_ALL_', '推荐型', '高',
     '性价比高的{industry}{industry_role}推荐',
     0, 1, 110, '通用推荐型 · 来源 GEO_Prompt库_v1.0');

-- -------------------------------------------------------------------------
-- 1.2 通用问题型 PRB_GEN(5 条) · 商业价值:中
-- -------------------------------------------------------------------------
INSERT INTO presale_prompt_template
    (prompt_code, industry, industry_role, category, business_value,
     prompt_content, has_competitor_var, enabled, sort_order, remark)
VALUES
    ('PRB_GEN_001', '_ALL_', '_ALL_', '问题型', '中',
     '如何选择靠谱的{industry}{industry_role}?',
     0, 1, 301, '通用问题型 · 来源 GEO_Prompt库_v1.0'),
    ('PRB_GEN_002', '_ALL_', '_ALL_', '问题型', '中',
     '{industry}行业有哪些常见的坑需要避开?',
     0, 1, 302, '通用问题型 · 来源 GEO_Prompt库_v1.0'),
    ('PRB_GEN_003', '_ALL_', '_ALL_', '问题型', '中',
     '做{industry}{industry_role}需要注意什么?',
     0, 1, 303, '通用问题型 · 来源 GEO_Prompt库_v1.0'),
    ('PRB_GEN_004', '_ALL_', '_ALL_', '问题型', '中',
     '{industry}行业的发展趋势如何?',
     0, 1, 304, '通用问题型 · 来源 GEO_Prompt库_v1.0'),
    ('PRB_GEN_005', '_ALL_', '_ALL_', '问题型', '中',
     '{region}{industry}市场怎么样?',
     0, 1, 305, '通用问题型 · 来源 GEO_Prompt库_v1.0');

-- -------------------------------------------------------------------------
-- 1.3 通用认知型 CGN_GEN(5 条) · 商业价值:中
-- -------------------------------------------------------------------------
INSERT INTO presale_prompt_template
    (prompt_code, industry, industry_role, category, business_value,
     prompt_content, has_competitor_var, enabled, sort_order, remark)
VALUES
    ('CGN_GEN_001', '_ALL_', '_ALL_', '认知型', '中',
     '{brand}怎么样?',
     0, 1, 401, '通用认知型 · 来源 GEO_Prompt库_v1.0'),
    ('CGN_GEN_002', '_ALL_', '_ALL_', '认知型', '中',
     '{brand}的口碑如何?',
     0, 1, 402, '通用认知型 · 来源 GEO_Prompt库_v1.0'),
    ('CGN_GEN_003', '_ALL_', '_ALL_', '认知型', '中',
     '{brand}在{industry}行业排名如何?',
     0, 1, 403, '通用认知型 · 来源 GEO_Prompt库_v1.0'),
    ('CGN_GEN_004', '_ALL_', '_ALL_', '认知型', '中',
     '{brand}是什么时候成立的?主要做什么?',
     0, 1, 404, '通用认知型 · 来源 GEO_Prompt库_v1.0'),
    ('CGN_GEN_005', '_ALL_', '_ALL_', '认知型', '中',
     '{brand}值得信赖吗?',
     0, 1, 405, '通用认知型 · 来源 GEO_Prompt库_v1.0');

-- -------------------------------------------------------------------------
-- 1.4 通用场景型 SCN_GEN(5 条) · 商业价值:中
-- -------------------------------------------------------------------------
INSERT INTO presale_prompt_template
    (prompt_code, industry, industry_role, category, business_value,
     prompt_content, has_competitor_var, enabled, sort_order, remark)
VALUES
    ('SCN_GEN_001', '_ALL_', '_ALL_', '场景型', '中',
     '第一次接触{industry},应该选什么{industry_role}?',
     0, 1, 501, '通用场景型 · 来源 GEO_Prompt库_v1.0'),
    ('SCN_GEN_002', '_ALL_', '_ALL_', '场景型', '中',
     '预算有限,{region}有什么{industry}{industry_role}推荐?',
     0, 1, 502, '通用场景型 · 来源 GEO_Prompt库_v1.0'),
    ('SCN_GEN_003', '_ALL_', '_ALL_', '场景型', '中',
     '{region}{industry}哪家服务最好?',
     0, 1, 503, '通用场景型 · 来源 GEO_Prompt库_v1.0'),
    ('SCN_GEN_004', '_ALL_', '_ALL_', '场景型', '中',
     '想要高性价比的{industry}{product},怎么选?',
     0, 1, 504, '通用场景型 · 来源 GEO_Prompt库_v1.0'),
    ('SCN_GEN_005', '_ALL_', '_ALL_', '场景型', '中',
     '{industry}新手入门,有什么建议?',
     0, 1, 505, '通用场景型 · 来源 GEO_Prompt库_v1.0');

-- =========================================================================
-- Section 2. 第二轮 5 条(has_competitor_var = 1)
-- 全部含 {competitor} 单变量占位符(v1.2 契约:Top3 拼接文本一次注入)
-- =========================================================================

-- -------------------------------------------------------------------------
-- 2.1 通用对比型 CMP_GEN(5 条) · 商业价值:高
-- 说明:
--   CMP_GEN_001/002/003 原 v1.0 已含竞品变量,本版按 v1.2 将 {competitor_a}/{b}/{c}
--   合并改写为 {competitor}。
--   CMP_GEN_004/005 原 v1.0 不含竞品变量,本版按 X1 方案改写加入 {competitor},
--   以凑足 v1.2 契约"第二轮 5 条"的数量。业务侧 review 语感时可调整 prompt_content。
-- -------------------------------------------------------------------------
INSERT INTO presale_prompt_template
    (prompt_code, industry, industry_role, category, business_value,
     prompt_content, has_competitor_var, enabled, sort_order, remark)
VALUES
    -- 原 v1.0: {competitor_a}和{competitor_b}哪个更好?
    ('CMP_GEN_001', '_ALL_', '_ALL_', '对比型', '高',
     '{competitor} 这几个品牌,哪个更好?',
     1, 1, 201,
     '通用对比型 · 来源 GEO_Prompt库_v1.0 · v1.2 单变量改写(原 {competitor_a}/b/c)'),

    -- 原 v1.0: {brand}和{competitor_a}相比有什么优势?
    ('CMP_GEN_002', '_ALL_', '_ALL_', '对比型', '高',
     '{brand}和 {competitor} 相比有什么优势?',
     1, 1, 202,
     '通用对比型 · 来源 GEO_Prompt库_v1.0 · v1.2 单变量改写(原 {competitor_a})'),

    -- 原 v1.0: {region}{industry}行业,{competitor_a}、{competitor_b}、{competitor_c}哪个口碑更好?
    ('CMP_GEN_003', '_ALL_', '_ALL_', '对比型', '高',
     '{region}{industry}行业,{competitor} 哪个口碑更好?',
     1, 1, 203,
     '通用对比型 · 来源 GEO_Prompt库_v1.0 · v1.2 单变量改写(原 {competitor_a}/b/c)'),

    -- 原 v1.0: {industry}行业的Top品牌对比分析(不含竞品变量)
    -- X1 改写:加入 {competitor} 凑足第二轮 5 条
    ('CMP_GEN_004', '_ALL_', '_ALL_', '对比型', '高',
     '{industry}行业,{competitor} 这几个 Top 品牌的对比分析',
     1, 1, 204,
     '通用对比型 · 来源 GEO_Prompt库_v1.0 · X1 改写加入 {competitor} (原无竞品变量)'),

    -- 原 v1.0: {brand}在{industry}行业处于什么水平?(不含竞品变量)
    -- X1 改写:加入 {competitor} 凑足第二轮 5 条
    ('CMP_GEN_005', '_ALL_', '_ALL_', '对比型', '高',
     '{brand}和 {competitor} 相比,在{industry}行业各自处于什么水平?',
     1, 1, 205,
     '通用对比型 · 来源 GEO_Prompt库_v1.0 · X1 改写加入 {competitor} (原无竞品变量)');

-- =========================================================================
-- Section 3. 数量校验(运行时 SELECT 可验证,不强制)
-- =========================================================================
-- 预期结果:
--   SELECT COUNT(*) FROM presale_prompt_template;                                -- 30
--   SELECT COUNT(*) FROM presale_prompt_template WHERE has_competitor_var = 0;    -- 25
--   SELECT COUNT(*) FROM presale_prompt_template WHERE has_competitor_var = 1;    -- 5
--   SELECT category, COUNT(*) FROM presale_prompt_template GROUP BY category;
--     推荐型  10   问题型  5   认知型  5   场景型  5   对比型  5
--   SELECT business_value, COUNT(*) FROM presale_prompt_template GROUP BY business_value;
--     高      15   中      15
--
-- 第一轮调用口径(v1.2):25 prompt × 11 platform = 275 次测试调用 + 275 次分析调用
-- 第二轮调用口径(v1.2):5 prompt × 11 platform = 55 次测试调用 + 55 次分析调用
-- 总 LLM 调用:275 + 275 + 55 + 55 = 660 次
-- =========================================================================
