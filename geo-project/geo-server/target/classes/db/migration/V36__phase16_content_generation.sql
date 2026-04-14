-- ============================================================
-- V36: content generation config + article workflow
-- ============================================================

ALTER TABLE project
    ADD COLUMN content_generation_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'content generation switch' AFTER source_type;

CREATE TABLE IF NOT EXISTS package_content_config (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  package_type        VARCHAR(32)      NOT NULL COMMENT 'package type',
  article_type        VARCHAR(32)      NOT NULL COMMENT 'faq/scenario_content/industry_article/stage_advice',
  articles_per_batch  INT UNSIGNED     NOT NULL DEFAULT 1 COMMENT 'articles per batch',
  questions_per_article INT UNSIGNED   NOT NULL DEFAULT 3 COMMENT 'questions per article',
  is_active           TINYINT(1)       NOT NULL DEFAULT 1 COMMENT 'enabled',
  created_at          DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_package_type_article_type (package_type, article_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='package content generation config';

INSERT INTO package_content_config (package_type, article_type, articles_per_batch, questions_per_article, is_active)
SELECT 'trial_6980', 'faq', 1, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM package_content_config WHERE package_type = 'trial_6980' AND article_type = 'faq');
INSERT INTO package_content_config (package_type, article_type, articles_per_batch, questions_per_article, is_active)
SELECT 'trial_6980', 'scenario_content', 1, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM package_content_config WHERE package_type = 'trial_6980' AND article_type = 'scenario_content');
INSERT INTO package_content_config (package_type, article_type, articles_per_batch, questions_per_article, is_active)
SELECT 'trial_6980', 'industry_article', 1, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM package_content_config WHERE package_type = 'trial_6980' AND article_type = 'industry_article');
INSERT INTO package_content_config (package_type, article_type, articles_per_batch, questions_per_article, is_active)
SELECT 'trial_6980', 'stage_advice', 1, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM package_content_config WHERE package_type = 'trial_6980' AND article_type = 'stage_advice');

INSERT INTO package_content_config (package_type, article_type, articles_per_batch, questions_per_article, is_active)
SELECT 'standard_12800', 'faq', 2, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM package_content_config WHERE package_type = 'standard_12800' AND article_type = 'faq');
INSERT INTO package_content_config (package_type, article_type, articles_per_batch, questions_per_article, is_active)
SELECT 'standard_12800', 'scenario_content', 2, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM package_content_config WHERE package_type = 'standard_12800' AND article_type = 'scenario_content');
INSERT INTO package_content_config (package_type, article_type, articles_per_batch, questions_per_article, is_active)
SELECT 'standard_12800', 'industry_article', 2, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM package_content_config WHERE package_type = 'standard_12800' AND article_type = 'industry_article');
INSERT INTO package_content_config (package_type, article_type, articles_per_batch, questions_per_article, is_active)
SELECT 'standard_12800', 'stage_advice', 1, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM package_content_config WHERE package_type = 'standard_12800' AND article_type = 'stage_advice');

INSERT INTO package_content_config (package_type, article_type, articles_per_batch, questions_per_article, is_active)
SELECT 'growth_26800', 'faq', 3, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM package_content_config WHERE package_type = 'growth_26800' AND article_type = 'faq');
INSERT INTO package_content_config (package_type, article_type, articles_per_batch, questions_per_article, is_active)
SELECT 'growth_26800', 'scenario_content', 3, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM package_content_config WHERE package_type = 'growth_26800' AND article_type = 'scenario_content');
INSERT INTO package_content_config (package_type, article_type, articles_per_batch, questions_per_article, is_active)
SELECT 'growth_26800', 'industry_article', 3, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM package_content_config WHERE package_type = 'growth_26800' AND article_type = 'industry_article');
INSERT INTO package_content_config (package_type, article_type, articles_per_batch, questions_per_article, is_active)
SELECT 'growth_26800', 'stage_advice', 2, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM package_content_config WHERE package_type = 'growth_26800' AND article_type = 'stage_advice');

CREATE TABLE IF NOT EXISTS article_batch (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  dispatch_task_id    BIGINT          NOT NULL,
  project_id          BIGINT          NOT NULL,
  batch_date          DATE            NOT NULL,
  batch_no            INT             NOT NULL DEFAULT 1,
  status              VARCHAR(32)     NOT NULL DEFAULT 'running',
  total_count         INT             NOT NULL DEFAULT 0,
  completed_count     INT             NOT NULL DEFAULT 0,
  failed_count        INT             NOT NULL DEFAULT 0,
  created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_article_batch_project_date_no (project_id, batch_date, batch_no),
  KEY idx_article_batch_task (dispatch_task_id),
  CONSTRAINT fk_article_batch_task FOREIGN KEY (dispatch_task_id) REFERENCES dispatch_task(id),
  CONSTRAINT fk_article_batch_project FOREIGN KEY (project_id) REFERENCES project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='article generation batch';

CREATE TABLE IF NOT EXISTS article_draft (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  batch_id            BIGINT UNSIGNED NOT NULL,
  project_id          BIGINT          NOT NULL,
  article_type        VARCHAR(32)     NOT NULL,
  title               VARCHAR(255)    NOT NULL,
  status              VARCHAR(32)     NOT NULL DEFAULT 'pending_review' COMMENT 'pending_review/approved/rejected/under_revision/published/unpublished',
  has_risk            TINYINT(1)      NOT NULL DEFAULT 0,
  risk_severity       VARCHAR(16)     NOT NULL DEFAULT 'none' COMMENT 'none/warn/block',
  risk_words_json     JSON            NULL,
  is_duplicate_title  TINYINT(1)      NOT NULL DEFAULT 0,
  duplicate_score     DECIMAL(5,4)    NULL,
  duplicate_article_id BIGINT UNSIGNED NULL,
  current_version_no  INT             NOT NULL DEFAULT 1,
  created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_article_draft_project_type_status (project_id, article_type, status),
  KEY idx_article_draft_project_created (project_id, created_at),
  CONSTRAINT fk_article_draft_batch FOREIGN KEY (batch_id) REFERENCES article_batch(id),
  CONSTRAINT fk_article_draft_project FOREIGN KEY (project_id) REFERENCES project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='article draft main table';

CREATE TABLE IF NOT EXISTS article_draft_version (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  article_id          BIGINT UNSIGNED NOT NULL,
  version_no          INT             NOT NULL,
  title               VARCHAR(255)    NOT NULL,
  content_markdown    LONGTEXT        NOT NULL,
  prompt_snapshot     JSON            NULL,
  input_snapshot      JSON            NULL,
  model_platform_code VARCHAR(64)     NULL,
  model_id            VARCHAR(128)    NULL,
  generated_by        VARCHAR(16)     NOT NULL DEFAULT 'system' COMMENT 'system/manual/regenerate',
  created_by          BIGINT          NULL,
  created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_article_version (article_id, version_no),
  KEY idx_article_version_created (article_id, created_at),
  CONSTRAINT fk_article_version_article FOREIGN KEY (article_id) REFERENCES article_draft(id),
  CONSTRAINT fk_article_version_user FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='article draft versions';

CREATE TABLE IF NOT EXISTS article_question_rel (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  article_id          BIGINT UNSIGNED NOT NULL,
  version_id          BIGINT UNSIGNED NOT NULL,
  question_id         BIGINT          NULL,
  question_text       VARCHAR(1000)   NOT NULL,
  sort_order          INT             NOT NULL DEFAULT 1,
  created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_article_question (version_id, question_id, sort_order),
  KEY idx_article_question_article (article_id),
  CONSTRAINT fk_article_question_article FOREIGN KEY (article_id) REFERENCES article_draft(id),
  CONSTRAINT fk_article_question_version FOREIGN KEY (version_id) REFERENCES article_draft_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='article-question relation';

CREATE TABLE IF NOT EXISTS article_review_log (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  article_id          BIGINT UNSIGNED NOT NULL,
  action              VARCHAR(32)     NOT NULL COMMENT 'approve/reject/return_for_revision/resubmit',
  comment             VARCHAR(1000)   NULL,
  risk_overridden     TINYINT(1)      NOT NULL DEFAULT 0,
  operator_id         BIGINT          NOT NULL,
  created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_article_review_article (article_id, created_at),
  CONSTRAINT fk_article_review_article FOREIGN KEY (article_id) REFERENCES article_draft(id),
  CONSTRAINT fk_article_review_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='article review history';

CREATE TABLE IF NOT EXISTS article_publish_log (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  article_id          BIGINT UNSIGNED NOT NULL,
  publish_action      VARCHAR(16)     NOT NULL COMMENT 'publish/unpublish',
  channel_name        VARCHAR(64)     NULL,
  channel_url         VARCHAR(500)    NULL,
  operator_id         BIGINT          NOT NULL,
  note                VARCHAR(1000)   NULL,
  created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_article_publish_article (article_id, created_at),
  CONSTRAINT fk_article_publish_article FOREIGN KEY (article_id) REFERENCES article_draft(id),
  CONSTRAINT fk_article_publish_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='article publish history';

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'global_forbidden_phrase', '根治', 'block', 10, 1, 'medical claim'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'global_forbidden_phrase' AND dict_key = '根治');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'global_forbidden_phrase', '100%有效', 'block', 20, 1, 'absolute claim'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'global_forbidden_phrase' AND dict_key = '100%有效');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'global_forbidden_phrase', '第一', 'warn', 30, 1, 'absolute expression'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'global_forbidden_phrase' AND dict_key = '第一');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'global_forbidden_phrase', '最好', 'warn', 40, 1, 'absolute expression'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'global_forbidden_phrase' AND dict_key = '最好');
