START TRANSACTION;

-- 3.1 decision.prefix 原 20 词处理
UPDATE keyword_affix_word SET sub_category = '正向评价类'
WHERE id IN (463, 464, 465, 466, 467, 468);

UPDATE keyword_affix_word SET sub_category = '品牌品质类'
WHERE id = 469;

UPDATE keyword_affix_word
SET type = 'transaction',
    sub_category = '性价比类',
    sort_order = sort_order + 3000
WHERE id IN (470, 471);

UPDATE keyword_affix_word
SET type = 'transaction',
    sub_category = '价格敏感类',
    sort_order = sort_order + 3000
WHERE id = 472;

UPDATE keyword_affix_word
SET type = 'function',
    sub_category = '产品分级类',
    industry_tag = 'common',
    sort_order = sort_order + 3000
WHERE id IN (473, 474, 475, 476, 477, 481, 482);

UPDATE keyword_affix_word
SET type = 'function',
    sub_category = '规模类',
    industry_tag = 'common',
    sort_order = sort_order + 3000
WHERE id IN (478, 479, 480);

-- 3.2 decision.prefix 批 0 迁入 28 词处理
UPDATE keyword_affix_word SET enabled = 0
WHERE id IN (10, 17, 27, 29, 30, 31, 33, 34, 35, 36, 37, 38);

UPDATE keyword_affix_word SET sub_category = '品牌品质类'
WHERE id IN (12, 14, 24, 28);

UPDATE keyword_affix_word SET sub_category = '资质类'
WHERE id IN (19, 20, 21);

UPDATE keyword_affix_word SET sub_category = '场景类'
WHERE id = 32;

-- 3.3 decision.prefix INSERT 补 PRD 期望的品牌品质类 6 条
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('decision', 'prefix', '一线的',    300, 1, '品牌品质类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('decision', 'prefix', '大牌的',    310, 1, '品牌品质类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('decision', 'prefix', '老牌的',    320, 1, '品牌品质类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('decision', 'prefix', '头部的',    330, 1, '品牌品质类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('decision', 'prefix', '高端的',    340, 1, '品牌品质类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('decision', 'prefix', '中高端的',  350, 1, '品牌品质类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

-- 3.4 decision.industry 原 20 词处理
UPDATE keyword_affix_word SET sub_category='行业词', visual_tag='common'
WHERE id IN (162, 163, 165, 168, 169);

UPDATE keyword_affix_word SET sub_category='行业词', visual_tag='toC'
WHERE id = 166;

UPDATE keyword_affix_word SET sub_category='行业词', visual_tag='toB'
WHERE id IN (164, 167, 170, 171, 177, 178, 179, 180, 181);

UPDATE keyword_affix_word SET enabled = 0
WHERE id IN (172, 173, 174);

UPDATE keyword_affix_word
SET type = 'function',
    sub_category = '功能行业词',
    visual_tag = 'common',
    sort_order = sort_order + 3000
WHERE id IN (175, 176);

-- 3.5 decision.suffix 原 23 词补字段
UPDATE keyword_affix_word SET sub_category = '决策选择类'
WHERE id IN (494, 495, 502, 503, 504, 508, 509);

UPDATE keyword_affix_word SET sub_category = '指南类'
WHERE id IN (496, 497, 498, 514);

UPDATE keyword_affix_word SET sub_category = '注意事项类'
WHERE id IN (499, 505, 506, 507);

UPDATE keyword_affix_word SET sub_category = '决策依据类'
WHERE id IN (500, 501, 511, 516);

UPDATE keyword_affix_word SET sub_category = '建议类'
WHERE id IN (510, 512, 513, 515);

-- 3.6 decision.suffix 批 0 迁入 29 词处理
UPDATE keyword_affix_word SET enabled = 0
WHERE id IN (48, 51, 52, 57);

-- 3.7 decision.suffix 批 1 迁入 20 词无需处理

COMMIT;
