START TRANSACTION;

-- 3.1 transaction.prefix 处理
UPDATE keyword_affix_word SET enabled = 0
WHERE id = 533;

UPDATE keyword_affix_word SET sub_category = '价格敏感类'
WHERE id IN (525, 526, 534);

UPDATE keyword_affix_word SET sub_category = '优惠活动类'
WHERE id IN (527, 528, 529, 530, 535, 540, 541, 542, 543, 544);

UPDATE keyword_affix_word SET sub_category = '渠道类'
WHERE id IN (531, 532);

UPDATE keyword_affix_word SET sub_category = '渠道保障类'
WHERE id IN (536, 537, 538, 539);

-- 3.2 transaction.prefix INSERT 补缺
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('transaction', 'prefix', '平价的',      800, 1, '价格敏感类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'prefix', '性价比好的',  810, 1, '性价比类',   NULL, NULL, 0, 0, 'approved', NOW(), NOW());

-- 3.3 transaction.industry 处理
UPDATE keyword_affix_word SET enabled = 0
WHERE id = 193;

UPDATE keyword_affix_word
SET type = 'function',
    sub_category = '功能行业词',
    visual_tag = 'common',
    sort_order = sort_order + 3000
WHERE id = 212;

UPDATE keyword_affix_word SET sub_category = '通用主体'
WHERE id IN (195, 196, 197, 198);

UPDATE keyword_affix_word SET sub_category = '商业模型类'
WHERE id IN (194, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211);

-- 3.4 transaction.industry INSERT 补缺
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('transaction', 'industry', '品牌',  220, 1, '通用主体', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'industry', '厂家',  230, 1, '通用主体', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'industry', '型号',  240, 1, '通用主体', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'industry', '款式',  250, 1, '通用主体', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

-- 3.5 transaction.suffix 处理
UPDATE keyword_affix_word SET enabled = 0
WHERE id IN (568, 572);

UPDATE keyword_affix_word
SET type = 'qa',
    sub_category = '攻略类',
    sort_order = sort_order + 3000
WHERE id IN (578, 579);

UPDATE keyword_affix_word
SET type = 'qa',
    sub_category = '注意事项类',
    sort_order = sort_order + 3000
WHERE id = 580;

UPDATE keyword_affix_word SET sub_category = '价格查询类'
WHERE id IN (556, 557, 558, 559, 560, 561, 563, 574, 575, 576);

UPDATE keyword_affix_word SET sub_category = '预算对比类'
WHERE id IN (562, 577);

UPDATE keyword_affix_word SET sub_category = '优惠类'
WHERE id IN (564, 565, 566, 567);

UPDATE keyword_affix_word SET sub_category = '购买渠道类'
WHERE id IN (569, 570, 571, 573);

-- 3.6 transaction.suffix INSERT 补缺
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('transaction', 'suffix', '什么价格',    300, 1, '价格查询类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'suffix', '价格',        310, 1, '价格查询类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'suffix', '价位',        320, 1, '价格查询类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'suffix', '一般多少钱',  330, 1, '价格查询类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'suffix', '大概多少钱',  340, 1, '价格查询类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('transaction', 'suffix', '贵不贵',          400, 1, '预算对比类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'suffix', '值不值',          410, 1, '预算对比类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'suffix', '划不划算',        420, 1, '预算对比类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'suffix', '性价比怎么样',    430, 1, '预算对比类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('transaction', 'suffix', '去哪买',      500, 1, '购买渠道类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'suffix', '哪里能买到',  510, 1, '购买渠道类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('transaction', 'suffix', '哪里有卖',    520, 1, '购买渠道类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

COMMIT;
