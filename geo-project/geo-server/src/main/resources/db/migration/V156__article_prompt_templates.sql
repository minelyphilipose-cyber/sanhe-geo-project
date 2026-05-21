CREATE TABLE IF NOT EXISTS article_prompt_template (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(500) NULL,
  channel_group_code VARCHAR(32) NOT NULL,
  channel_sub_code VARCHAR(64) NULL,
  agent_site_module VARCHAR(32) NULL,
  article_type_code VARCHAR(64) NOT NULL DEFAULT 'industry_article',
  weight INT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'draft',
  current_version_id BIGINT UNSIGNED NULL,
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_article_prompt_template_channel (channel_group_code, channel_sub_code, status),
  KEY idx_article_prompt_template_current_version (current_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章生成提示词模板';

CREATE TABLE IF NOT EXISTS article_prompt_template_version (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_id BIGINT UNSIGNED NOT NULL,
  version_no INT NOT NULL,
  system_prompt MEDIUMTEXT NOT NULL,
  user_prompt_template MEDIUMTEXT NOT NULL,
  variables_json JSON NULL,
  quality_rules_json JSON NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'draft',
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_article_prompt_template_version (template_id, version_no),
  KEY idx_article_prompt_template_version_status (template_id, status),
  CONSTRAINT fk_article_prompt_template_version_template FOREIGN KEY (template_id) REFERENCES article_prompt_template(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章生成提示词模板版本';

ALTER TABLE article_prompt_template
  ADD CONSTRAINT fk_article_prompt_template_current_version
  FOREIGN KEY (current_version_id) REFERENCES article_prompt_template_version(id);

ALTER TABLE batch_article_generation_task
  ADD COLUMN channel_group_code VARCHAR(32) NULL COMMENT 'article generation channel group' AFTER content_style,
  ADD COLUMN channel_sub_code VARCHAR(64) NULL COMMENT 'article generation channel subcategory' AFTER channel_group_code,
  ADD COLUMN agent_site_module VARCHAR(32) NULL COMMENT 'Agent site module: faq/knowledge/product' AFTER channel_sub_code,
  ADD COLUMN article_type_code VARCHAR(64) NULL COMMENT 'prompt template article type code' AFTER agent_site_module,
  ADD COLUMN prompt_template_id BIGINT UNSIGNED NULL COMMENT 'frozen prompt template id' AFTER article_type_code,
  ADD COLUMN prompt_template_version_id BIGINT UNSIGNED NULL COMMENT 'frozen prompt template version id' AFTER prompt_template_id,
  ADD COLUMN allocation_mode VARCHAR(16) NULL COMMENT 'auto/custom/legacy' AFTER prompt_template_version_id,
  ADD KEY idx_batch_article_task_prompt_template (prompt_template_id, prompt_template_version_id),
  ADD KEY idx_batch_article_task_channel_template (channel_group_code, channel_sub_code, article_type_code);

ALTER TABLE article_draft
  ADD COLUMN channel_group_code VARCHAR(32) NULL COMMENT 'article generation channel group' AFTER content_style,
  ADD COLUMN channel_sub_code VARCHAR(64) NULL COMMENT 'article generation channel subcategory' AFTER channel_group_code,
  ADD COLUMN agent_site_module VARCHAR(32) NULL COMMENT 'Agent site module: faq/knowledge/product' AFTER channel_sub_code,
  ADD COLUMN article_type_code VARCHAR(64) NULL COMMENT 'prompt template article type code' AFTER agent_site_module,
  ADD COLUMN prompt_template_id BIGINT UNSIGNED NULL COMMENT 'prompt template id used for generation' AFTER article_type_code,
  ADD COLUMN prompt_template_version_id BIGINT UNSIGNED NULL COMMENT 'prompt template version id used for generation' AFTER prompt_template_id,
  ADD COLUMN allocation_mode VARCHAR(16) NULL COMMENT 'auto/custom/legacy' AFTER prompt_template_version_id,
  ADD KEY idx_article_draft_prompt_template (prompt_template_id, prompt_template_version_id),
  ADD KEY idx_article_draft_channel_template (channel_group_code, channel_sub_code, article_type_code);
