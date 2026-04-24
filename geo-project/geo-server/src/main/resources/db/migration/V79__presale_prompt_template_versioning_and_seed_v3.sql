-- =========================================================================
-- V79: presale_prompt_template versioning + seed v3 templates (45 rows)
-- Rollback:
--   1) ALTER TABLE presale_prompt_template DROP COLUMN template_version; (if no dependency)
--   2) DELETE FROM presale_prompt_template WHERE template_version='v3';
-- =========================================================================

-- 1) Add template_version column (idempotent, compatible with MySQL 8.0 minor differences)
SELECT COUNT(1) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'presale_prompt_template'
  AND COLUMN_NAME = 'template_version';

SET @ddl_add_col = IF(
  @col_exists = 0,
  "ALTER TABLE presale_prompt_template ADD COLUMN template_version VARCHAR(16) NOT NULL DEFAULT 'v2' COMMENT '模板版本' AFTER enabled",
  'SELECT 1'
);
PREPARE stmt_add_col FROM @ddl_add_col;
EXECUTE stmt_add_col;
DEALLOCATE PREPARE stmt_add_col;

-- 2) Add index idx_template_version_enabled (idempotent)
SELECT COUNT(1) INTO @idx_exists
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'presale_prompt_template'
  AND INDEX_NAME = 'idx_template_version_enabled';

SET @ddl_add_idx = IF(
  @idx_exists = 0,
  'ALTER TABLE presale_prompt_template ADD KEY idx_template_version_enabled (template_version, enabled)',
  'SELECT 1'
);
PREPARE stmt_add_idx FROM @ddl_add_idx;
EXECUTE stmt_add_idx;
DEALLOCATE PREPARE stmt_add_idx;

-- 3) Backfill existing rows to v2
UPDATE presale_prompt_template
SET template_version = 'v2'
WHERE template_version IS NULL OR template_version = '';

-- 4) Seed v3 templates (enabled=0, idempotent by prompt_code)
INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_001', '_ALL_', '_ALL_', '推荐型', '高', '在{region}{industry}行业,有哪些比较好的{industry_role}推荐?', 0, 0, 101, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_001');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_002', '_ALL_', '_ALL_', '推荐型', '高', '{region}{industry}行业排名前十的品牌有哪些?', 0, 0, 102, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_002');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_003', '_ALL_', '_ALL_', '推荐型', '高', '想找{region}靠谱的{product}{industry_role},有什么推荐?', 0, 0, 103, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_003');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_004', '_ALL_', '_ALL_', '推荐型', '高', '{region}有哪些值得信赖的{industry}品牌?', 0, 0, 104, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_004');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_005', '_ALL_', '_ALL_', '推荐型', '高', '推荐几家{region}口碑好的{industry}{industry_role}', 0, 0, 105, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_005');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_006', '_ALL_', '_ALL_', '推荐型', '高', '{region}{industry}领域有哪些知名度高的{industry_role}?', 0, 0, 106, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_006');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_007', '_ALL_', '_ALL_', '推荐型', '高', '想做{industry}加盟,有什么好的品牌推荐?', 0, 0, 107, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_007');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_008', '_ALL_', '_ALL_', '推荐型', '高', '{region}{industry}行业的头部品牌有哪些?', 0, 0, 108, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_008');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_009', '_ALL_', '_ALL_', '推荐型', '高', '{region}做{product}比较好的{industry_role}有哪些?', 0, 0, 109, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_009');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_010', '_ALL_', '_ALL_', '推荐型', '高', '性价比高的{industry}{industry_role}推荐', 0, 0, 110, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_010');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_011', '_ALL_', '_ALL_', '推荐型', '高', '{region}有哪些连锁{industry}品牌值得推荐?', 0, 0, 111, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_011');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_012', '_ALL_', '_ALL_', '推荐型', '高', '{industry}行业有哪些老牌的知名品牌?', 0, 0, 112, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_012');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_REC_GEN_013', '_ALL_', '_ALL_', '推荐型', '高', '{industry}行业最受年轻人欢迎的品牌有哪些?', 0, 0, 113, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_REC_GEN_013');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_PRB_GEN_001', '_ALL_', '_ALL_', '问题型', '中', '如何选择靠谱的{industry}{industry_role}?', 0, 0, 301, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_PRB_GEN_001');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_PRB_GEN_002', '_ALL_', '_ALL_', '问题型', '中', '{industry}行业有哪些常见的坑需要避开?', 0, 0, 302, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_PRB_GEN_002');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_PRB_GEN_003', '_ALL_', '_ALL_', '问题型', '中', '做{industry}{industry_role}需要注意什么?', 0, 0, 303, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_PRB_GEN_003');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_PRB_GEN_004', '_ALL_', '_ALL_', '问题型', '中', '{industry}行业的发展趋势如何?', 0, 0, 304, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_PRB_GEN_004');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_PRB_GEN_005', '_ALL_', '_ALL_', '问题型', '中', '{region}{industry}市场怎么样?', 0, 0, 305, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_PRB_GEN_005');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_PRB_GEN_006', '_ALL_', '_ALL_', '问题型', '中', '{industry}行业目前的竞争格局是什么样的?', 0, 0, 306, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_PRB_GEN_006');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_PRB_GEN_007', '_ALL_', '_ALL_', '问题型', '中', '选择{industry}{industry_role}时最容易被忽略的因素是什么?', 0, 0, 307, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_PRB_GEN_007');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_SCN_GEN_001', '_ALL_', '_ALL_', '场景型', '中', '第一次接触{industry},应该选什么{industry_role}?', 0, 0, 501, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_SCN_GEN_001');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_SCN_GEN_002', '_ALL_', '_ALL_', '场景型', '中', '预算有限,{region}有什么{industry}{industry_role}推荐?', 0, 0, 502, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_SCN_GEN_002');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_SCN_GEN_003', '_ALL_', '_ALL_', '场景型', '中', '{region}{industry}哪家服务最好?', 0, 0, 503, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_SCN_GEN_003');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_SCN_GEN_004', '_ALL_', '_ALL_', '场景型', '中', '想要高性价比的{industry}{product},怎么选?', 0, 0, 504, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_SCN_GEN_004');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_SCN_GEN_005', '_ALL_', '_ALL_', '场景型', '中', '{industry}新手入门,有什么建议?', 0, 0, 505, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_SCN_GEN_005');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_SCN_GEN_006', '_ALL_', '_ALL_', '场景型', '中', '带家人去{region}哪家{industry}{industry_role}比较合适?', 0, 0, 506, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_SCN_GEN_006');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_SCN_GEN_007', '_ALL_', '_ALL_', '场景型', '中', '商务宴请选{region}哪家{industry}{industry_role}不会失礼?', 0, 0, 507, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_SCN_GEN_007');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_SCN_GEN_008', '_ALL_', '_ALL_', '场景型', '中', '朋友聚会想去{region}的{industry}{industry_role},有什么推荐?', 0, 0, 508, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_SCN_GEN_008');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_SCN_GEN_009', '_ALL_', '_ALL_', '场景型', '中', '一个人想{industry},{region}哪家比较合适?', 0, 0, 509, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_SCN_GEN_009');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_SCN_GEN_010', '_ALL_', '_ALL_', '场景型', '中', '周末和同事去{region}的{industry}{industry_role},选哪家?', 0, 0, 510, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_SCN_GEN_010');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_SCN_GEN_011', '_ALL_', '_ALL_', '场景型', '中', '打算在{region}第一次尝试{industry},推荐哪家{industry_role}?', 0, 0, 511, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_SCN_GEN_011');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CGN_GEN_001', '_ALL_', '_ALL_', '认知型', '中', '{brand}怎么样?', 0, 0, 401, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CGN_GEN_001');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CGN_GEN_002', '_ALL_', '_ALL_', '认知型', '中', '{brand}的口碑如何?', 0, 0, 402, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CGN_GEN_002');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CGN_GEN_003', '_ALL_', '_ALL_', '认知型', '中', '{brand}在{industry}行业排名如何?', 0, 0, 403, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CGN_GEN_003');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CGN_GEN_004', '_ALL_', '_ALL_', '认知型', '中', '{brand}是什么时候成立的?主要做什么?', 0, 0, 404, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CGN_GEN_004');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CGN_GEN_005', '_ALL_', '_ALL_', '认知型', '中', '{brand}值得信赖吗?', 0, 0, 405, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CGN_GEN_005');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CGN_GEN_006', '_ALL_', '_ALL_', '认知型', '中', '{brand}有哪些优势和特点?', 0, 0, 406, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CGN_GEN_006');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CGN_GEN_007', '_ALL_', '_ALL_', '认知型', '中', '{brand}适合什么样的{user_type}?', 0, 0, 407, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CGN_GEN_007');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CMP_GEN_001', '_ALL_', '_ALL_', '对比型', '高', '{competitor} 这几个品牌,哪个更好?', 1, 0, 201, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CMP_GEN_001');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CMP_GEN_002', '_ALL_', '_ALL_', '对比型', '高', '{brand}和 {competitor} 相比有什么优势?', 1, 0, 202, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CMP_GEN_002');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CMP_GEN_003', '_ALL_', '_ALL_', '对比型', '高', '{region}{industry}行业,{competitor} 哪个口碑更好?', 1, 0, 203, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CMP_GEN_003');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CMP_GEN_004', '_ALL_', '_ALL_', '对比型', '高', '{industry}行业,{competitor} 这几个 Top 品牌的对比分析', 1, 0, 204, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CMP_GEN_004');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CMP_GEN_005', '_ALL_', '_ALL_', '对比型', '高', '{brand}和 {competitor} 相比,在{industry}行业各自处于什么水平?', 1, 0, 205, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CMP_GEN_005');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CMP_GEN_006', '_ALL_', '_ALL_', '对比型', '高', '{brand} 和 {competitor},哪个更适合{user_type}?', 1, 0, 206, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CMP_GEN_006');

INSERT INTO presale_prompt_template (prompt_code, industry, industry_role, category, business_value, prompt_content, has_competitor_var, enabled, sort_order, remark, template_version)
SELECT 'V3_CMP_GEN_007', '_ALL_', '_ALL_', '对比型', '高', '选{brand}还是{competitor},各自的优缺点是什么?', 1, 0, 207, 'v3 模板导入', 'v3'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM presale_prompt_template WHERE prompt_code = 'V3_CMP_GEN_007');
