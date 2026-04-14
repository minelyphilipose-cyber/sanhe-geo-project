-- ============================================================
-- V41: content distribution execution (sites + quota + tasks)
-- ============================================================

CREATE TABLE IF NOT EXISTS content_question_rotation (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id          BIGINT          NOT NULL,
  article_type        VARCHAR(32)     NOT NULL,
  current_offset      INT UNSIGNED    NOT NULL DEFAULT 0,
  updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_content_rotation_project_type (project_id, article_type),
  CONSTRAINT fk_content_rotation_project FOREIGN KEY (project_id) REFERENCES project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='content question rotation offset';

ALTER TABLE article_draft
    ADD COLUMN published_at DATETIME NULL COMMENT 'published/distributed time' AFTER updated_at;

CREATE TABLE IF NOT EXISTS publish_sites (
  id                        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  site_name                 VARCHAR(128)    NOT NULL,
  domain                    VARCHAR(255)    NOT NULL,
  industry_tags             JSON            NULL,
  tier                      VARCHAR(8)      NOT NULL COMMENT 'S0/S1/S2',
  status                    VARCHAR(32)     NOT NULL DEFAULT 'active' COMMENT 'active/suspended/maintenance',
  integration_method        VARCHAR(32)     NOT NULL DEFAULT 'rest_api' COMMENT 'rest_api/ftp/email/manual',
  api_endpoint              VARCHAR(500)    NULL,
  http_method               VARCHAR(16)     NULL COMMENT 'POST/PUT',
  auth_type                 VARCHAR(32)     NULL COMMENT 'api_key/bearer_token/basic_auth/oauth2',
  credential_ref            VARCHAR(255)    NULL,
  api_credential_encrypted  VARCHAR(1000)   NULL,
  request_header_template   JSON            NULL,
  request_body_template     JSON            NULL,
  response_url_path         VARCHAR(255)    NULL COMMENT 'json path, e.g. $.data.url',
  content_constraints       JSON            NULL,
  current_health_status     VARCHAR(32)     NOT NULL DEFAULT 'normal' COMMENT 'normal/slow/high_failure/degraded',
  last_failure_at           DATETIME        NULL,
  failure_rate              DECIMAL(6,4)    NOT NULL DEFAULT 0,
  remark                    VARCHAR(1000)   NULL,
  created_at                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_publish_sites_tier_status (tier, status),
  KEY idx_publish_sites_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='publish site catalog';

CREATE TABLE IF NOT EXISTS package_publish_config (
  id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  package_type          VARCHAR(32)      NOT NULL,
  allowed_site_tiers    JSON             NOT NULL,
  monthly_publish_limit INT UNSIGNED     NOT NULL DEFAULT 0,
  weekly_publish_limit  INT UNSIGNED     NOT NULL DEFAULT 0,
  is_active             TINYINT(1)       NOT NULL DEFAULT 1,
  created_at            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_package_publish_type (package_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='package publish quota config';

CREATE TABLE IF NOT EXISTS distribution_tasks (
  id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  article_id           BIGINT UNSIGNED NOT NULL,
  project_id           BIGINT          NOT NULL,
  site_id              BIGINT UNSIGNED NOT NULL,
  attempt_no           INT UNSIGNED    NOT NULL DEFAULT 1,
  status               VARCHAR(32)     NOT NULL DEFAULT 'pending' COMMENT 'pending/submitting/submitted/failed/confirmed',
  integration_method   VARCHAR(32)     NOT NULL,
  request_payload      JSON            NULL,
  response_payload     JSON            NULL,
  published_url        VARCHAR(1000)   NULL,
  error_message        VARCHAR(1000)   NULL,
  retry_count          INT UNSIGNED    NOT NULL DEFAULT 0,
  operator_id          BIGINT          NOT NULL,
  created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at          DATETIME        NULL,
  UNIQUE KEY uk_distribution_article_site_attempt (article_id, site_id, attempt_no),
  KEY idx_distribution_project_created (project_id, created_at),
  KEY idx_distribution_site_created (site_id, created_at),
  KEY idx_distribution_article_created (article_id, created_at),
  CONSTRAINT fk_distribution_article FOREIGN KEY (article_id) REFERENCES article_draft(id),
  CONSTRAINT fk_distribution_project FOREIGN KEY (project_id) REFERENCES project(id),
  CONSTRAINT fk_distribution_site FOREIGN KEY (site_id) REFERENCES publish_sites(id),
  CONSTRAINT fk_distribution_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='distribution execution tasks';

CREATE TABLE IF NOT EXISTS project_publish_quota (
  id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id           BIGINT          NOT NULL,
  quota_month          VARCHAR(7)      NOT NULL COMMENT 'YYYY-MM',
  used_count           INT UNSIGNED    NOT NULL DEFAULT 0,
  monthly_limit        INT UNSIGNED    NOT NULL DEFAULT 0,
  created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_project_publish_quota (project_id, quota_month),
  CONSTRAINT fk_project_publish_quota_project FOREIGN KEY (project_id) REFERENCES project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='project monthly publish quota snapshot';

INSERT INTO package_publish_config (package_type, allowed_site_tiers, monthly_publish_limit, weekly_publish_limit, is_active)
SELECT 'trial_6980', JSON_ARRAY('S2'), 10, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM package_publish_config WHERE package_type = 'trial_6980');

INSERT INTO package_publish_config (package_type, allowed_site_tiers, monthly_publish_limit, weekly_publish_limit, is_active)
SELECT 'standard_12800', JSON_ARRAY('S1', 'S2'), 20, 6, 1
WHERE NOT EXISTS (SELECT 1 FROM package_publish_config WHERE package_type = 'standard_12800');

INSERT INTO package_publish_config (package_type, allowed_site_tiers, monthly_publish_limit, weekly_publish_limit, is_active)
SELECT 'growth_26800', JSON_ARRAY('S0', 'S1', 'S2'), 40, 12, 1
WHERE NOT EXISTS (SELECT 1 FROM package_publish_config WHERE package_type = 'growth_26800');

UPDATE package_content_config
SET articles_per_batch = 2, is_active = 1
WHERE package_type = 'trial_6980' AND article_type = 'faq';
UPDATE package_content_config
SET articles_per_batch = 1, is_active = 1
WHERE package_type = 'trial_6980' AND article_type = 'scenario_content';
UPDATE package_content_config
SET articles_per_batch = 0, is_active = 0
WHERE package_type = 'trial_6980' AND article_type IN ('industry_article', 'stage_advice');

UPDATE package_content_config
SET articles_per_batch = 2, is_active = 1
WHERE package_type = 'standard_12800' AND article_type = 'faq';
UPDATE package_content_config
SET articles_per_batch = 2, is_active = 1
WHERE package_type = 'standard_12800' AND article_type = 'scenario_content';
UPDATE package_content_config
SET articles_per_batch = 1, is_active = 1
WHERE package_type = 'standard_12800' AND article_type = 'industry_article';
UPDATE package_content_config
SET articles_per_batch = 0, is_active = 0
WHERE package_type = 'standard_12800' AND article_type = 'stage_advice';

UPDATE package_content_config
SET articles_per_batch = 3, is_active = 1
WHERE package_type = 'growth_26800' AND article_type = 'faq';
UPDATE package_content_config
SET articles_per_batch = 2, is_active = 1
WHERE package_type = 'growth_26800' AND article_type = 'scenario_content';
UPDATE package_content_config
SET articles_per_batch = 2, is_active = 1
WHERE package_type = 'growth_26800' AND article_type = 'industry_article';
UPDATE package_content_config
SET articles_per_batch = 1, is_active = 1
WHERE package_type = 'growth_26800' AND article_type = 'stage_advice';
