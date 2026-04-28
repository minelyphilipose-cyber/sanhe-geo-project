START TRANSACTION;

-- 3.1 comparison.prefix 全部下架
UPDATE keyword_affix_word SET enabled = 0
WHERE type = 'comparison' AND affix_kind = 'prefix' AND enabled = 1;

-- 3.2 comparison.industry 全部下架
UPDATE keyword_affix_word SET enabled = 0
WHERE type = 'comparison' AND affix_kind = 'industry' AND enabled = 1;

-- 3.3 comparison.suffix 处理
UPDATE keyword_affix_word
SET type = 'qa',
    sub_category = '注意事项类',
    sort_order = sort_order + 4000
WHERE id IN (623, 624);

UPDATE keyword_affix_word SET sub_category = '对比选择类'
WHERE id IN (602, 604, 605, 617);

UPDATE keyword_affix_word SET sub_category = '对比类'
WHERE id IN (603, 607, 608, 609, 619, 622);

UPDATE keyword_affix_word SET sub_category = '维度对比类'
WHERE id IN (610, 611, 612, 613, 625);

UPDATE keyword_affix_word SET sub_category = '优劣分析类'
WHERE id IN (606, 614, 615, 616, 618);

UPDATE keyword_affix_word SET sub_category = '替代类'
WHERE id IN (620, 621);

-- 3.4 comparison.suffix INSERT 补缺
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('comparison', 'suffix', '差别',          300, 1, '对比选择类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('comparison', 'suffix', '哪个值得买',    310, 1, '优劣分析类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('comparison', 'suffix', '哪个性价比高',  320, 1, '优劣分析类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('comparison', 'suffix', '哪个更靠谱',    330, 1, '优劣分析类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('comparison', 'suffix', '哪个更推荐',    340, 1, '优劣分析类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

COMMIT;
