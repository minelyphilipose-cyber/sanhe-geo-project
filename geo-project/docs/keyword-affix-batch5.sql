-- 2026-04-28 批 5:qa 问答词类型重建
-- 执行前已预检: qa.prefix / qa.suffix 待插入词无唯一键冲突。

START TRANSACTION;

-- 3.1 处理现存 qa.prefix 2 条
UPDATE keyword_affix_word
SET type = 'transaction',
    sub_category = '性价比类',
    sort_order = sort_order + 4000
WHERE id = 23 AND type = 'qa' AND affix_kind = 'prefix';

UPDATE keyword_affix_word
SET enabled = 0
WHERE id = 25 AND type = 'qa' AND affix_kind = 'prefix';

-- 3.2 qa.prefix INSERT 5 条 PRD 3.5.4
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('qa', 'prefix', '进口',     10, 1, '来源类',   NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'prefix', '国产',     20, 1, '来源类',   NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'prefix', '新款',     30, 1, '新旧类',   NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'prefix', '最新款',   40, 1, '新旧类',   NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'prefix', '高品质',   50, 1, '品质类',   NULL, NULL, 0, 0, 'approved', NOW(), NOW());

-- 3.4 qa.suffix INSERT 14 条 PRD 3.5.5
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('qa', 'suffix', '怎么用',     100, 1, '使用类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'suffix', '使用方法',   110, 1, '使用类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'suffix', '安装方法',   120, 1, '使用类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'suffix', '使用教程',   130, 1, '使用类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('qa', 'suffix', '怎么保养',   200, 1, '维护类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'suffix', '保养方法',   210, 1, '维护类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'suffix', '维护',       220, 1, '维护类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'suffix', '清洁方法',   230, 1, '维护类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('qa', 'suffix', '坏了怎么办', 300, 1, '故障类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'suffix', '维修',       310, 1, '故障类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'suffix', '售后',       320, 1, '故障类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'suffix', '质保',       330, 1, '故障类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('qa', 'suffix', '常见问题',     400, 1, '综合类', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('qa', 'suffix', '有哪些种类',   410, 1, '综合类', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

COMMIT;
