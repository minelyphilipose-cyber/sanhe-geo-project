START TRANSACTION;

-- 2.1 comparison.compare 连接词, PRD 3.4.4
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('comparison', 'compare', '和',     10, 1, '对比连接词', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('comparison', 'compare', '对比',   20, 1, '对比连接词', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('comparison', 'compare', '与',     30, 1, '对比连接词', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('comparison', 'compare', 'VS',     40, 1, '对比连接词', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('comparison', 'compare', '比较',   50, 1, '对比连接词', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('comparison', 'compare', '相比',   60, 1, '对比连接词', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

-- 2.2 function.prefix 通用性能类
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('function', 'prefix', '高质量的',  10, 1, '通用性能类', NULL, 'common', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '耐用的',    20, 1, '通用性能类', NULL, 'common', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '结实的',    30, 1, '通用性能类', NULL, 'common', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '稳定的',    40, 1, '通用性能类', NULL, 'common', 0, 0, 'approved', NOW(), NOW());

-- 2.3 function.prefix 门窗行业
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('function', 'prefix', '隔音的',  100, 1, '门窗行业', NULL, 'door_window', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '防盗的',  110, 1, '门窗行业', NULL, 'door_window', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '保温的',  120, 1, '门窗行业', NULL, 'door_window', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '抗风的',  130, 1, '门窗行业', NULL, 'door_window', 0, 0, 'approved', NOW(), NOW());

-- 2.4 function.prefix 家电行业
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('function', 'prefix', '节能的',  200, 1, '家电行业', NULL, 'appliance', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '静音的',  210, 1, '家电行业', NULL, 'appliance', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '智能的',  220, 1, '家电行业', NULL, 'appliance', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '变频的',  230, 1, '家电行业', NULL, 'appliance', 0, 0, 'approved', NOW(), NOW());

-- 2.5 function.prefix 建材行业
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('function', 'prefix', '环保的',  300, 1, '建材行业', NULL, 'building_material', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '防水的',  310, 1, '建材行业', NULL, 'building_material', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '阻燃的',  320, 1, '建材行业', NULL, 'building_material', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '抗菌的',  330, 1, '建材行业', NULL, 'building_material', 0, 0, 'approved', NOW(), NOW());

-- 2.6 function.prefix 快消行业
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('function', 'prefix', '健康的',    400, 1, '快消行业', NULL, 'fmcg', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '有机的',    410, 1, '快消行业', NULL, 'fmcg', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '纯天然的',  420, 1, '快消行业', NULL, 'fmcg', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '无添加的',  430, 1, '快消行业', NULL, 'fmcg', 0, 0, 'approved', NOW(), NOW());

-- 2.7 function.prefix 工业品行业
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('function', 'prefix', '高精度的',    500, 1, '工业品行业', NULL, 'industrial', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '高产能的',    510, 1, '工业品行业', NULL, 'industrial', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '稳定运行的',  520, 1, '工业品行业', NULL, 'industrial', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '自动化的',    530, 1, '工业品行业', NULL, 'industrial', 0, 0, 'approved', NOW(), NOW());

-- 2.8 function.prefix 服装行业
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('function', 'prefix', '百搭的',  600, 1, '服装行业', NULL, 'clothing', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '显瘦的',  610, 1, '服装行业', NULL, 'clothing', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '舒适的',  620, 1, '服装行业', NULL, 'clothing', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '透气的',  630, 1, '服装行业', NULL, 'clothing', 0, 0, 'approved', NOW(), NOW());

-- 2.9 function.prefix 新旧/来源类
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('function', 'prefix', '新款',     700, 1, '新旧/来源类', NULL, 'common', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '最新款',   710, 1, '新旧/来源类', NULL, 'common', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '进口',     720, 1, '新旧/来源类', NULL, 'common', 0, 0, 'approved', NOW(), NOW()),
  ('function', 'prefix', '国产',     730, 1, '新旧/来源类', NULL, 'common', 0, 0, 'approved', NOW(), NOW());

-- 2.10 function.industry 5 条, (留空) 不入库
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('function', 'industry', '品牌',  10, 1, '功能行业词', 'common', NULL, 0, 0, 'approved', NOW(), NOW()),
  ('function', 'industry', '厂家',  20, 1, '功能行业词', 'common', NULL, 0, 0, 'approved', NOW(), NOW()),
  ('function', 'industry', '型号',  30, 1, '功能行业词', 'common', NULL, 0, 0, 'approved', NOW(), NOW()),
  ('function', 'industry', '款式',  40, 1, '功能行业词', 'common', NULL, 0, 0, 'approved', NOW(), NOW()),
  ('function', 'industry', '产品',  50, 1, '功能行业词', 'common', NULL, 0, 0, 'approved', NOW(), NOW());

-- 2.11 function.suffix 7 条, (留空) 不入库
INSERT INTO keyword_affix_word
  (type, affix_kind, word_text, sort_order, enabled, sub_category, visual_tag, industry_tag, is_manual, is_temporary, approval_status, created_at, updated_at)
VALUES
  ('function', 'suffix', '推荐',         10, 1, '功能后缀', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('function', 'suffix', '哪个好',       20, 1, '功能后缀', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('function', 'suffix', '哪款好',       30, 1, '功能后缀', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('function', 'suffix', '选什么',       40, 1, '功能后缀', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('function', 'suffix', '有哪些',       50, 1, '功能后缀', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('function', 'suffix', '什么牌子好',   60, 1, '功能后缀', NULL, NULL, 0, 0, 'approved', NOW(), NOW()),
  ('function', 'suffix', '推荐一下',     70, 1, '功能后缀', NULL, NULL, 0, 0, 'approved', NOW(), NOW());

-- 2.12 修正版: qa.prefix 决策风格词迁入 decision.prefix
UPDATE keyword_affix_word
SET type = 'decision',
    sub_category = '正向评价类',
    sort_order = sort_order + 1000
WHERE id IN (9,10,11,12,13,14,15,16,17,18,19,20,21,22,24,26,27,28,29,30,31,32,33,34,35,36,37,38)
  AND type = 'qa'
  AND affix_kind = 'prefix';

-- 2.13 qa.suffix 决策风格词迁入 decision.suffix
-- 对目标 decision.suffix 已存在的重复词先不迁移，避免 uk_type_kind_word 冲突。
UPDATE keyword_affix_word q
LEFT JOIN keyword_affix_word d
  ON d.type = 'decision'
 AND d.affix_kind = 'suffix'
 AND d.word_text = q.word_text
SET q.type = 'decision',
    q.sub_category = '决策选择类',
    q.sort_order = q.sort_order + 1000
WHERE q.type = 'qa'
  AND q.affix_kind = 'suffix'
  AND q.enabled = 1
  AND d.id IS NULL;

-- 重复词已有 decision.suffix 等价行，源 qa 行软下架。
UPDATE keyword_affix_word q
JOIN keyword_affix_word d
  ON d.type = 'decision'
 AND d.affix_kind = 'suffix'
 AND d.word_text = q.word_text
SET q.enabled = 0,
    q.sub_category = '决策选择类',
    q.sort_order = q.sort_order + 1000
WHERE q.type = 'qa'
  AND q.affix_kind = 'suffix'
  AND q.enabled = 1;

COMMIT;
