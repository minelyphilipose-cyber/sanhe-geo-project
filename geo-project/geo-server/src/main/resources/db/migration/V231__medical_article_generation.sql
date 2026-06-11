-- MySQL auto-commits DDL. Keep this migration resumable for environments that
-- failed midway through an earlier V231 run.
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand' AND column_name = 'medical_license') = 0, 'ALTER TABLE brand ADD COLUMN medical_license VARCHAR(500) NULL COMMENT ''medical institution practice license public information'' AFTER brand_case_description', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand' AND column_name = 'diagnosis_scope') = 0, 'ALTER TABLE brand ADD COLUMN diagnosis_scope VARCHAR(1000) NULL COMMENT ''approved diagnosis and treatment scope'' AFTER medical_license', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand' AND column_name = 'institution_type') = 0, 'ALTER TABLE brand ADD COLUMN institution_type VARCHAR(128) NULL COMMENT ''medical institution type'' AFTER diagnosis_scope', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand' AND column_name = 'practitioner_info_public') = 0, 'ALTER TABLE brand ADD COLUMN practitioner_info_public TEXT NULL COMMENT ''public practitioner qualification information'' AFTER institution_type', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand' AND column_name = 'medical_ad_review_no') = 0, 'ALTER TABLE brand ADD COLUMN medical_ad_review_no VARCHAR(128) NULL COMMENT ''medical advertisement review certificate number'' AFTER practitioner_info_public', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand' AND column_name = 'compliance_notes_medical') = 0, 'ALTER TABLE brand ADD COLUMN compliance_notes_medical TEXT NULL COMMENT ''medical compliance notes for this brand'' AFTER medical_ad_review_no', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand_offering' AND column_name = 'medical_industry_code') = 0, 'ALTER TABLE brand_offering ADD COLUMN medical_industry_code VARCHAR(32) NULL COMMENT ''medical industry code: medical_beauty/oral'' AFTER use_scenarios', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand_offering' AND column_name = 'medical_category_code') = 0, 'ALTER TABLE brand_offering ADD COLUMN medical_category_code VARCHAR(64) NULL COMMENT ''medical project category code'' AFTER medical_industry_code', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand_offering' AND column_name = 'medical_category_name') = 0, 'ALTER TABLE brand_offering ADD COLUMN medical_category_name VARCHAR(128) NULL COMMENT ''medical project category display name'' AFTER medical_category_code', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand_offering' AND column_name = 'qualification_ref') = 0, 'ALTER TABLE brand_offering ADD COLUMN qualification_ref VARCHAR(500) NULL COMMENT ''medical qualification reference for this offering'' AFTER medical_category_name', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand_offering' AND column_name = 'medical_project_enabled') = 0, 'ALTER TABLE brand_offering ADD COLUMN medical_project_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''whether this offering is allowed for medical article generation'' AFTER qualification_ref', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'brand_offering' AND index_name = 'idx_brand_offering_medical_gate') = 0, 'ALTER TABLE brand_offering ADD KEY idx_brand_offering_medical_gate (brand_id, medical_project_enabled, medical_industry_code, medical_category_code, status, deleted_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND column_name = 'compliance_status') = 0, 'ALTER TABLE article_draft ADD COLUMN compliance_status VARCHAR(32) NULL COMMENT ''medical compliance status: pending/passed/failed/discarded_compliance_failed'' AFTER template_source', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND column_name = 'publish_review_status') = 0, 'ALTER TABLE article_draft ADD COLUMN publish_review_status VARCHAR(32) NULL COMMENT ''medical publish review status: not_required/pending/passed/rejected'' AFTER compliance_status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND column_name = 'medical_ad_review_no') = 0, 'ALTER TABLE article_draft ADD COLUMN medical_ad_review_no VARCHAR(128) NULL COMMENT ''medical advertisement review certificate number'' AFTER publish_review_status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND column_name = 'medical_channel_tier') = 0, 'ALTER TABLE article_draft ADD COLUMN medical_channel_tier VARCHAR(32) NULL COMMENT ''medical channel tier: education/source_site/official_site'' AFTER medical_ad_review_no', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND column_name = 'medical_industry_code') = 0, 'ALTER TABLE article_draft ADD COLUMN medical_industry_code VARCHAR(32) NULL COMMENT ''medical industry code frozen at generation time'' AFTER medical_channel_tier', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND column_name = 'medical_category_code') = 0, 'ALTER TABLE article_draft ADD COLUMN medical_category_code VARCHAR(64) NULL COMMENT ''medical project category code frozen at generation time'' AFTER medical_industry_code', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND index_name = 'idx_article_draft_medical_compliance') = 0, 'ALTER TABLE article_draft ADD KEY idx_article_draft_medical_compliance (medical_industry_code, medical_category_code, compliance_status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND index_name = 'idx_article_draft_publish_review') = 0, 'ALTER TABLE article_draft ADD KEY idx_article_draft_publish_review (medical_channel_tier, publish_review_status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_batch' AND column_name = 'medical_industry_code') = 0, 'ALTER TABLE batch_article_generation_batch ADD COLUMN medical_industry_code VARCHAR(32) NULL COMMENT ''medical industry code'' AFTER brand_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_batch' AND column_name = 'medical_channel_tier') = 0, 'ALTER TABLE batch_article_generation_batch ADD COLUMN medical_channel_tier VARCHAR(32) NULL COMMENT ''medical channel tier'' AFTER medical_industry_code', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND column_name = 'medical_industry_code') = 0, 'ALTER TABLE batch_article_generation_task ADD COLUMN medical_industry_code VARCHAR(32) NULL COMMENT ''medical industry code'' AFTER article_type_code', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND column_name = 'medical_category_code') = 0, 'ALTER TABLE batch_article_generation_task ADD COLUMN medical_category_code VARCHAR(64) NULL COMMENT ''medical project category code'' AFTER medical_industry_code', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND column_name = 'medical_category_name') = 0, 'ALTER TABLE batch_article_generation_task ADD COLUMN medical_category_name VARCHAR(128) NULL COMMENT ''medical project category name'' AFTER medical_category_code', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND column_name = 'topic_angle_id') = 0, 'ALTER TABLE batch_article_generation_task ADD COLUMN topic_angle_id BIGINT NULL COMMENT ''selected medical topic angle id'' AFTER medical_category_name', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND column_name = 'structure_skeleton') = 0, 'ALTER TABLE batch_article_generation_task ADD COLUMN structure_skeleton VARCHAR(64) NULL COMMENT ''medical structure skeleton code'' AFTER topic_angle_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND column_name = 'focus') = 0, 'ALTER TABLE batch_article_generation_task ADD COLUMN focus VARCHAR(64) NULL COMMENT ''medical science focus code'' AFTER structure_skeleton', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND column_name = 'compliance_status') = 0, 'ALTER TABLE batch_article_generation_task ADD COLUMN compliance_status VARCHAR(32) NULL COMMENT ''medical compliance status'' AFTER quality_issues_json', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND column_name = 'compliance_issues_json') = 0, 'ALTER TABLE batch_article_generation_task ADD COLUMN compliance_issues_json JSON NULL COMMENT ''medical compliance issues'' AFTER compliance_status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND column_name = 'discarded_article_id') = 0, 'ALTER TABLE batch_article_generation_task ADD COLUMN discarded_article_id BIGINT NULL COMMENT ''discarded article record id when compliance retries fail'' AFTER compliance_issues_json', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND index_name = 'idx_batch_article_task_medical') = 0, 'ALTER TABLE batch_article_generation_task ADD KEY idx_batch_article_task_medical (medical_industry_code, medical_category_code, topic_angle_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS medical_topic_angle (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  industry_code VARCHAR(32) NOT NULL COMMENT 'medical_beauty/oral',
  industry_name VARCHAR(64) NOT NULL,
  category_code VARCHAR(64) NOT NULL,
  category_name VARCHAR(128) NOT NULL,
  topic_angle VARCHAR(500) NOT NULL,
  recommended_focus VARCHAR(64) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  KEY idx_medical_topic_angle_category (industry_code, category_code, enabled, deleted_at),
  KEY idx_medical_topic_angle_sort (sort_order, id)
) COMMENT='medical article topic angle library';

CREATE TABLE IF NOT EXISTS medical_compliance_kernel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  industry_code VARCHAR(32) NOT NULL,
  channel_tier VARCHAR(32) NOT NULL,
  kernel_name VARCHAR(128) NOT NULL,
  system_prompt LONGTEXT NOT NULL,
  brand_exposure_limit INT NOT NULL DEFAULT 2,
  require_manual_publish_review TINYINT(1) NOT NULL DEFAULT 0,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  version_no INT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_medical_kernel_scope_version (industry_code, channel_tier, version_no),
  KEY idx_medical_kernel_enabled (industry_code, channel_tier, enabled)
) COMMENT='medical compliance kernel by industry and channel tier';

CREATE TABLE IF NOT EXISTS medical_channel_style_module (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_group_code VARCHAR(64) NOT NULL,
  channel_sub_code VARCHAR(64) NULL,
  channel_tier VARCHAR(32) NOT NULL,
  style_prompt LONGTEXT NOT NULL,
  high_risk TINYINT(1) NOT NULL DEFAULT 0,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_medical_channel_style (channel_group_code, channel_sub_code),
  KEY idx_medical_channel_style_tier (channel_tier, enabled)
) COMMENT='medical channel style module';

CREATE TABLE IF NOT EXISTS medical_compliance_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_type VARCHAR(64) NOT NULL,
  industry_code VARCHAR(32) NULL,
  channel_tier VARCHAR(32) NULL,
  channel_group_code VARCHAR(64) NULL,
  channel_sub_code VARCHAR(64) NULL,
  pattern VARCHAR(500) NOT NULL,
  match_mode VARCHAR(32) NOT NULL DEFAULT 'contains',
  severity VARCHAR(32) NOT NULL DEFAULT 'block',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_medical_rule_scope (rule_type, industry_code, channel_tier, enabled),
  KEY idx_medical_rule_channel (channel_group_code, channel_sub_code, enabled)
) COMMENT='medical compliance rules';

CREATE TABLE IF NOT EXISTS medical_compliance_hit_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  article_id BIGINT NULL,
  batch_id BIGINT NULL,
  task_id BIGINT NULL,
  project_id BIGINT NULL,
  brand_id BIGINT NULL,
  rule_id BIGINT NULL,
  rule_type VARCHAR(64) NOT NULL,
  matched_text VARCHAR(500) NULL,
  check_stage VARCHAR(64) NOT NULL,
  action VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_medical_hit_article (article_id, created_at),
  KEY idx_medical_hit_task (batch_id, task_id, created_at),
  KEY idx_medical_hit_rule (rule_id, created_at)
) COMMENT='medical compliance hit log';

CREATE TABLE IF NOT EXISTS medical_generation_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  brand_id BIGINT NULL,
  topic_angle_id BIGINT NULL,
  structure_skeleton VARCHAR(64) NULL,
  focus VARCHAR(64) NULL,
  article_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_medical_history_project_recent (project_id, created_at),
  KEY idx_medical_history_brand_recent (brand_id, created_at)
) COMMENT='medical article generation history for recent de-duplication';

INSERT IGNORE INTO medical_compliance_kernel (
  industry_code, channel_tier, kernel_name, system_prompt, brand_exposure_limit,
  require_manual_publish_review, enabled, version_no
) VALUES
('medical_beauty', 'education', '医美科普合规内核 v1',
'你正在生成医疗美容科普文章。必须克制、中立、科普化：不得承诺疗效，不得使用绝对化、保证性、治愈性表达；不得制造容貌焦虑、变美诱导、前后对比或体验种草；不得替代医生诊断；必须提示个体差异、风险、禁忌和需由正规医疗机构医生面诊评估。品牌露出只可作为信息来源，不得以优惠、排名、案例效果诱导转化。',
2, 0, 1, 1),
('medical_beauty', 'source_site', '医美信源站合规内核 v1',
'你正在生成医疗美容信源站文章。必须保持第三方资讯口吻，事实与观点分开；不得写成品牌软文，不得推荐具体机构或项目，不得出现疗效承诺、变美诱导、前后对比、价格促销、案例种草。可引用机构公开资质信息，但品牌露出不超过 1 次，且只能作为公共信息背景。',
1, 0, 1, 1),
('medical_beauty', 'official_site', '医美官网合规内核 v1',
'你正在生成医疗美容官网文章。内容必须限定在机构主体、依法可公示资质、诊疗范围、就诊流程、风险提示和科普说明。不得发布未经审查的医疗广告内容，不得承诺效果，不得展示前后对比或患者见证，不得使用促销诱导。发布前必须具备有效医疗广告审查证明编号或人工法务确认。',
0, 1, 1, 1),
('oral', 'education', '口腔科普合规内核 v1',
'你正在生成口腔医疗科普文章。必须克制、中立、科普化：不得承诺治疗效果，不得替代医生诊断，不得使用根治、永久、保证等绝对化表达；必须提示个体差异、适应证、禁忌、风险和需由正规医疗机构医生检查评估。',
2, 0, 1, 1),
('oral', 'source_site', '口腔信源站合规内核 v1',
'你正在生成口腔医疗信源站文章。必须保持第三方资讯口吻，不得写成机构推广，不得推荐具体治疗方案或机构，不得承诺疗效、制造焦虑、价格促销或案例种草。品牌露出不超过 1 次，且只能作为公共信息背景。',
1, 0, 1, 1),
('oral', 'official_site', '口腔官网合规内核 v1',
'你正在生成口腔医疗官网文章。内容必须限定在机构主体、依法可公示资质、诊疗范围、就诊流程、风险提示和科普说明。不得发布未经审查的医疗广告内容，不得承诺效果，不得展示患者见证，不得使用促销诱导。发布前必须具备有效医疗广告审查证明编号或人工法务确认。',
0, 1, 1, 1);

INSERT IGNORE INTO medical_channel_style_module (
  channel_group_code, channel_sub_code, channel_tier, style_prompt, high_risk, enabled
) VALUES
('agent_site', NULL, 'official_site',
'官网档：表达正式、克制、可核验。只写机构主体、资质范围、流程说明、风险提示和科普内容，不做转化诱导。',
0, 1),
('industry_site', NULL, 'source_site',
'信源站档：第三方资讯口吻，客观解释行业现象、选择标准、流程和风险，不出现品牌推荐或软文语气。',
0, 1),
('authority_media', NULL, 'source_site',
'权威媒体档：公共信息价值优先，事实与观点分开，避免营销化表达和单一机构导向。',
0, 1),
('forum', NULL, 'education',
'平台讨论档：可以从常见疑问切入，但不得写成体验种草；所有判断必须回到风险、适应证和医生评估。',
1, 1),
('self_media', 'wechat', 'education',
'公众号档：结构完整、递进清楚，适合长文科普。每篇必须包含风险、禁忌或个体差异提示。',
0, 1),
('self_media', 'zhihu', 'education',
'知乎档：以认真回答问题的方式解释边界和判断依据，不做机构推荐，不写个人治疗建议。',
0, 1),
('self_media', 'baijiahao', 'education',
'百家号档：面向搜索收录，标题和开头突出科普问题，正文保持审慎和信息密度。',
0, 1),
('self_media', 'toutiao', 'education',
'今日头条档：结论前置但不标题党，短段落表达，必须保留风险、禁忌和理性决策提示。',
0, 1),
('self_media', 'netease', 'education',
'网易档：泛资讯口吻，强调常识、风险和决策边界，不做机构或项目种草。',
0, 1),
('self_media', 'sohu', 'education',
'搜狐档：门户资讯口吻，清晰解释问题和注意事项，避免夸张营销表达。',
0, 1),
('self_media', 'douyin', 'education',
'抖音图文档：短、直接，但不得制造焦虑或诱导变美；必须出现风险提示和理性决策提示。',
1, 1),
('self_media', 'xiaohongshu', 'education',
'小红书档：不得使用种草、体验、变美打卡、前后对比口吻；只能做克制清单式科普，必须提示风险和医生评估。',
1, 1);

DELETE FROM medical_compliance_rule
WHERE (rule_type, IFNULL(industry_code, ''), IFNULL(channel_tier, ''), IFNULL(channel_group_code, ''), IFNULL(channel_sub_code, ''), pattern) IN (
  ('absolute_claim', '', '', '', '', '根治'),
  ('absolute_claim', '', '', '', '', '永久有效'),
  ('absolute_claim', '', '', '', '', '保证效果'),
  ('absolute_claim', '', '', '', '', '100%安全'),
  ('absolute_claim', '', '', '', '', '零风险'),
  ('absolute_claim', '', '', '', '', '无副作用'),
  ('ranking_claim', '', '', '', '', '最权威'),
  ('ranking_claim', '', '', '', '', '第一'),
  ('ranking_claim', '', '', '', '', '顶级专家'),
  ('promotion', '', '', '', '', '限时优惠'),
  ('promotion', '', '', '', '', '立减'),
  ('promotion', '', '', '', '', '免费体验'),
  ('beauty_anxiety', 'medical_beauty', '', '', '', '丑'),
  ('beauty_anxiety', 'medical_beauty', '', '', '', '变美逆袭'),
  ('beauty_anxiety', 'medical_beauty', '', '', '', '颜值焦虑'),
  ('comparison_case', 'medical_beauty', '', '', '', '前后对比'),
  ('experience_seeding', 'medical_beauty', '', 'self_media', 'xiaohongshu', '种草'),
  ('experience_seeding', 'medical_beauty', '', 'self_media', 'xiaohongshu', '亲测'),
  ('experience_seeding', 'medical_beauty', '', 'self_media', 'douyin', '变美打卡')
);

INSERT INTO medical_compliance_rule (
  rule_type, industry_code, channel_tier, channel_group_code, channel_sub_code,
  pattern, match_mode, severity, enabled, remark
) VALUES
('absolute_claim', NULL, NULL, NULL, NULL, '根治', 'contains', 'block', 1, '医疗内容不得承诺根治'),
('absolute_claim', NULL, NULL, NULL, NULL, '永久有效', 'contains', 'block', 1, '医疗内容不得承诺永久效果'),
('absolute_claim', NULL, NULL, NULL, NULL, '保证效果', 'contains', 'block', 1, '医疗内容不得保证效果'),
('absolute_claim', NULL, NULL, NULL, NULL, '100%安全', 'contains', 'block', 1, '医疗内容不得绝对化安全承诺'),
('absolute_claim', NULL, NULL, NULL, NULL, '零风险', 'contains', 'block', 1, '医疗内容不得宣称零风险'),
('absolute_claim', NULL, NULL, NULL, NULL, '无副作用', 'contains', 'block', 1, '医疗内容不得宣称无副作用'),
('ranking_claim', NULL, NULL, NULL, NULL, '最权威', 'contains', 'block', 1, '绝对化排名或权威表述'),
('ranking_claim', NULL, NULL, NULL, NULL, '第一', 'contains', 'block', 1, '绝对化排名表述'),
('ranking_claim', NULL, NULL, NULL, NULL, '顶级专家', 'contains', 'block', 1, '绝对化专家宣传'),
('promotion', NULL, NULL, NULL, NULL, '限时优惠', 'contains', 'block', 1, '医疗广告不得以促销诱导'),
('promotion', NULL, NULL, NULL, NULL, '立减', 'contains', 'block', 1, '医疗广告不得以促销诱导'),
('promotion', NULL, NULL, NULL, NULL, '免费体验', 'contains', 'block', 1, '医疗广告不得以体验诱导'),
('beauty_anxiety', 'medical_beauty', NULL, NULL, NULL, '丑', 'contains', 'block', 1, '医美不得制造容貌焦虑'),
('beauty_anxiety', 'medical_beauty', NULL, NULL, NULL, '变美逆袭', 'contains', 'block', 1, '医美不得诱导变美逆袭'),
('beauty_anxiety', 'medical_beauty', NULL, NULL, NULL, '颜值焦虑', 'contains', 'block', 1, '医美不得制造容貌焦虑'),
('comparison_case', 'medical_beauty', NULL, NULL, NULL, '前后对比', 'contains', 'block', 1, '医美不得使用前后对比'),
('experience_seeding', 'medical_beauty', NULL, 'self_media', 'xiaohongshu', '种草', 'contains', 'block', 1, '小红书医美加严'),
('experience_seeding', 'medical_beauty', NULL, 'self_media', 'xiaohongshu', '亲测', 'contains', 'block', 1, '小红书医美加严'),
('experience_seeding', 'medical_beauty', NULL, 'self_media', 'douyin', '变美打卡', 'contains', 'block', 1, '抖音图文医美加严');

DELETE FROM medical_topic_angle
WHERE (industry_code, category_code, topic_angle) IN (
  ('medical_beauty', 'skin_laser', '光电类皮肤项目为什么要先做皮肤状态评估'),
  ('medical_beauty', 'skin_laser', '皮肤光电项目常见适应证和禁忌证怎么理解'),
  ('medical_beauty', 'injection', '注射类医美项目为什么不能只看材料名称'),
  ('medical_beauty', 'injection', '注射类项目面诊时需要重点确认哪些风险边界'),
  ('medical_beauty', 'surgery', '手术类医美项目为什么必须关注适应证和恢复期'),
  ('medical_beauty', 'surgery', '如何区分医美手术咨询中的合理预期和过度承诺'),
  ('oral', 'orthodontics', '牙齿矫正前为什么需要完整口腔检查和影像评估'),
  ('oral', 'orthodontics', '成年人正畸常见误区有哪些'),
  ('oral', 'implant', '种植牙为什么不是人人都能直接做'),
  ('oral', 'implant', '种植牙术前评估通常关注哪些条件'),
  ('oral', 'restoration', '牙齿修复方案为什么需要结合牙体条件判断'),
  ('oral', 'periodontal', '牙周治疗为什么强调长期维护而不是一次解决')
);

INSERT INTO medical_topic_angle (
  industry_code, industry_name, category_code, category_name, topic_angle,
  recommended_focus, enabled, sort_order
) VALUES
('medical_beauty', '医美', 'skin_laser', '皮肤光电', '光电类皮肤项目为什么要先做皮肤状态评估', 'risk', 1, 10),
('medical_beauty', '医美', 'skin_laser', '皮肤光电', '皮肤光电项目常见适应证和禁忌证怎么理解', 'principle', 1, 20),
('medical_beauty', '医美', 'injection', '注射类项目', '注射类医美项目为什么不能只看材料名称', 'rational_decision', 1, 30),
('medical_beauty', '医美', 'injection', '注射类项目', '注射类项目面诊时需要重点确认哪些风险边界', 'risk', 1, 40),
('medical_beauty', '医美', 'surgery', '手术类项目', '手术类医美项目为什么必须关注适应证和恢复期', 'risk', 1, 50),
('medical_beauty', '医美', 'surgery', '手术类项目', '如何区分医美手术咨询中的合理预期和过度承诺', 'misconception', 1, 60),
('oral', '口腔', 'orthodontics', '正畸', '牙齿矫正前为什么需要完整口腔检查和影像评估', 'risk', 1, 10),
('oral', '口腔', 'orthodontics', '正畸', '成年人正畸常见误区有哪些', 'misconception', 1, 20),
('oral', '口腔', 'implant', '种植牙', '种植牙为什么不是人人都能直接做', 'risk', 1, 30),
('oral', '口腔', 'implant', '种植牙', '种植牙术前评估通常关注哪些条件', 'principle', 1, 40),
('oral', '口腔', 'restoration', '修复', '牙齿修复方案为什么需要结合牙体条件判断', 'rational_decision', 1, 50),
('oral', '口腔', 'periodontal', '牙周', '牙周治疗为什么强调长期维护而不是一次解决', 'misconception', 1, 60);
