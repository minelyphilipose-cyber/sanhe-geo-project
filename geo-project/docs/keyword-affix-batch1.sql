START TRANSACTION;

-- 3.1 brand.prefix 下架 2 条 + 补字段
UPDATE keyword_affix_word
SET enabled = 0
WHERE id IN (296, 313);

UPDATE keyword_affix_word SET sub_category = '正向评价类'
WHERE id IN (293, 294, 304);

UPDATE keyword_affix_word SET sub_category = '品质特征类'
WHERE id IN (295, 298, 299, 300, 301, 302, 303, 311, 312, 317);

UPDATE keyword_affix_word SET sub_category = '来源类'
WHERE id IN (305, 306, 307);

UPDATE keyword_affix_word SET sub_category = '规模类'
WHERE id = 308;

UPDATE keyword_affix_word SET sub_category = '资质类'
WHERE id IN (309, 310, 314, 315, 316);

-- 3.2 brand.prefix INSERT 补缺
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('brand', 'prefix', '靠谱的',    400, 1, '正向评价类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'prefix', '专业的',    410, 1, '正向评价类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'prefix', '评价高的',  420, 1, '正向评价类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'prefix', '优秀的',    430, 1, '正向评价类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

-- 3.3 brand.industry 下架 1 条 + 补字段
UPDATE keyword_affix_word SET enabled = 0 WHERE id = 71;

UPDATE keyword_affix_word SET sub_category = '主体词'
WHERE id IN (69, 70, 73, 74, 88);

UPDATE keyword_affix_word SET sub_category = '品质特征'
WHERE id = 87;

-- 3.4 brand.industry 迁入 decision.industry
UPDATE keyword_affix_word
SET type = 'decision',
    sub_category = '行业词',
    visual_tag = 'toB',
    sort_order = sort_order + 2000
WHERE id IN (72, 76, 77, 78, 81, 82, 83);

UPDATE keyword_affix_word
SET type = 'decision',
    sub_category = '行业词',
    visual_tag = 'toC',
    sort_order = sort_order + 2000
WHERE id IN (75, 79, 80, 84, 85, 86);

-- 3.5 brand.industry INSERT 补缺
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('brand', 'industry', '产品',      90, 1, '主体词', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'industry', '这个牌子',  100, 1, '主体词', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

-- 3.6 brand.suffix 下架 2 条 + 保留补字段
UPDATE keyword_affix_word SET enabled = 0
WHERE id IN (333, 337);

UPDATE keyword_affix_word SET sub_category = '了解类'
WHERE id IN (329, 346);

-- 3.7 brand.suffix 迁入 decision.suffix
UPDATE keyword_affix_word
SET type = 'decision',
    sub_category = '榜单类',
    sort_order = sort_order + 2000
WHERE id IN (324, 326, 327, 331, 334, 335, 338);

UPDATE keyword_affix_word
SET type = 'decision',
    sub_category = '推荐类',
    sort_order = sort_order + 2000
WHERE id IN (325, 342, 344);

UPDATE keyword_affix_word
SET type = 'decision',
    sub_category = '决策选择类',
    sort_order = sort_order + 2000
WHERE id IN (328, 332, 339, 340, 343, 345);

UPDATE keyword_affix_word
SET type = 'decision',
    sub_category = '测评类',
    sort_order = sort_order + 2000
WHERE id IN (336, 347);

UPDATE keyword_affix_word
SET type = 'decision',
    sub_category = '对比类',
    sort_order = sort_order + 2000
WHERE id = 330;

UPDATE keyword_affix_word
SET type = 'decision',
    sub_category = '指南类',
    sort_order = sort_order + 2000
WHERE id = 341;

-- 3.8 brand.suffix INSERT 补缺
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('brand', 'suffix', '怎么样',  500, 1, '了解类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'suffix', '是什么',  510, 1, '了解类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'suffix', '介绍',    520, 1, '了解类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'suffix', '简介',    530, 1, '了解类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'suffix', '好不好',  540, 1, '了解类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'suffix', '有名吗',  550, 1, '了解类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('brand', 'suffix', '口碑如何',      600, 1, '评价类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'suffix', '靠不靠谱',      610, 1, '评价类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'suffix', '质量怎么样',    620, 1, '评价类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'suffix', '评价',          630, 1, '评价类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'suffix', '值不值得买',    640, 1, '评价类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('brand', 'suffix', '是哪里的',        700, 1, '归属类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'suffix', '是哪个公司的',    710, 1, '归属类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'suffix', '是什么牌子',      720, 1, '归属类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('brand', 'suffix', '哪里生产的',      730, 1, '归属类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

COMMIT;
