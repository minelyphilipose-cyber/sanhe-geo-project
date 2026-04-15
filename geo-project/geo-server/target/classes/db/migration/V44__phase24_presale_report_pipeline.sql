-- ============================================================
-- V44: report core + presale diagnosis pipeline
-- ============================================================

CREATE TABLE IF NOT EXISTS reports (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id          BIGINT          NOT NULL,
  report_type         VARCHAR(32)     NOT NULL COMMENT 'presale/biweekly/monthly/quarterly',
  version_no          INT UNSIGNED    NOT NULL DEFAULT 1,
  period_start        DATE            NULL,
  period_end          DATE            NULL,
  status              VARCHAR(32)     NOT NULL DEFAULT 'generating' COMMENT 'generating/draft/published/intercepted/superseded/archived',
  share_token         VARCHAR(64)     NULL,
  share_password_hash VARCHAR(255)    NULL,
  share_expires_at    DATETIME        NULL,
  pdf_url             VARCHAR(1000)   NULL,
  visibility          VARCHAR(16)     NOT NULL DEFAULT 'client' COMMENT 'client/internal',
  stage_advice        TEXT            NULL,
  stage_advice_input  JSON            NULL,
  superseded_by       BIGINT UNSIGNED NULL,
  created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by          BIGINT          NULL,
  published_at        DATETIME        NULL,
  published_by        BIGINT          NULL,
  UNIQUE KEY uk_reports_project_type_version (project_id, report_type, version_no),
  UNIQUE KEY uk_reports_share_token (share_token),
  KEY idx_reports_project_type_status (project_id, report_type, status, created_at),
  CONSTRAINT fk_reports_project FOREIGN KEY (project_id) REFERENCES project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='unified report main table';

CREATE TABLE IF NOT EXISTS presale_question_sets (
  id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id       BIGINT          NOT NULL,
  version_no       INT UNSIGNED    NOT NULL,
  status           VARCHAR(16)     NOT NULL DEFAULT 'draft' COMMENT 'draft/locked/archived',
  question_count   INT UNSIGNED    NOT NULL DEFAULT 0,
  generated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  locked_at        DATETIME        NULL,
  locked_by        BIGINT          NULL,
  archived_at      DATETIME        NULL,
  created_by       BIGINT          NULL,
  created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_presale_set_project_version (project_id, version_no),
  KEY idx_presale_set_project_status (project_id, status, created_at),
  CONSTRAINT fk_presale_set_project FOREIGN KEY (project_id) REFERENCES project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='presale diagnosis question sets';

CREATE TABLE IF NOT EXISTS presale_question_items (
  id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  set_id           BIGINT UNSIGNED NOT NULL,
  project_id       BIGINT          NOT NULL,
  content          VARCHAR(512)    NOT NULL,
  question_type    VARCHAR(32)     NOT NULL,
  source           VARCHAR(16)     NOT NULL COMMENT 'auto/manual',
  sort_order       INT             NOT NULL DEFAULT 0,
  is_active        TINYINT(1)      NOT NULL DEFAULT 1,
  created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_presale_item_set_active_sort (set_id, is_active, sort_order, id),
  CONSTRAINT fk_presale_item_set FOREIGN KEY (set_id) REFERENCES presale_question_sets(id),
  CONSTRAINT fk_presale_item_project FOREIGN KEY (project_id) REFERENCES project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='presale diagnosis question items';

CREATE TABLE IF NOT EXISTS presale_diagnosis_batches (
  id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id       BIGINT          NOT NULL,
  question_set_id  BIGINT UNSIGNED NOT NULL,
  dispatch_task_id BIGINT          NOT NULL,
  status           VARCHAR(32)     NOT NULL DEFAULT 'running' COMMENT 'running/completed/failed',
  total_requests   INT UNSIGNED    NOT NULL DEFAULT 0,
  completed_count  INT UNSIGNED    NOT NULL DEFAULT 0,
  failed_count     INT UNSIGNED    NOT NULL DEFAULT 0,
  started_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at      DATETIME        NULL,
  created_by       BIGINT          NULL,
  created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_presale_batch_dispatch_task (dispatch_task_id),
  KEY idx_presale_batch_project_set (project_id, question_set_id, created_at),
  CONSTRAINT fk_presale_batch_project FOREIGN KEY (project_id) REFERENCES project(id),
  CONSTRAINT fk_presale_batch_set FOREIGN KEY (question_set_id) REFERENCES presale_question_sets(id),
  CONSTRAINT fk_presale_batch_dispatch FOREIGN KEY (dispatch_task_id) REFERENCES dispatch_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='presale diagnosis execution batches';

CREATE TABLE IF NOT EXISTS presale_diagnosis_results (
  id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  batch_id         BIGINT UNSIGNED NOT NULL,
  project_id       BIGINT          NOT NULL,
  question_set_id  BIGINT UNSIGNED NOT NULL,
  question_item_id BIGINT UNSIGNED NOT NULL,
  platform_id      BIGINT          NOT NULL,
  platform_code    VARCHAR(64)     NOT NULL,
  status           VARCHAR(32)     NOT NULL COMMENT 'completed/failed',
  request_count    INT UNSIGNED    NOT NULL DEFAULT 1,
  response_time_ms INT UNSIGNED    NULL,
  brand_hit        TINYINT(1)      NOT NULL DEFAULT 0,
  site_mentioned   TINYINT(1)      NOT NULL DEFAULT 0,
  contact_mentioned TINYINT(1)     NOT NULL DEFAULT 0,
  detail_json      JSON            NULL,
  error_message    VARCHAR(1000)   NULL,
  created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_presale_result_batch_question_platform (batch_id, question_item_id, platform_id),
  KEY idx_presale_result_project_set (project_id, question_set_id),
  KEY idx_presale_result_batch_status (batch_id, status),
  CONSTRAINT fk_presale_result_batch FOREIGN KEY (batch_id) REFERENCES presale_diagnosis_batches(id),
  CONSTRAINT fk_presale_result_project FOREIGN KEY (project_id) REFERENCES project(id),
  CONSTRAINT fk_presale_result_set FOREIGN KEY (question_set_id) REFERENCES presale_question_sets(id),
  CONSTRAINT fk_presale_result_item FOREIGN KEY (question_item_id) REFERENCES presale_question_items(id),
  CONSTRAINT fk_presale_result_platform FOREIGN KEY (platform_id) REFERENCES ai_platform_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='presale diagnosis results (question x platform)';

CREATE TABLE IF NOT EXISTS presale_report_snapshots (
  id                        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  report_id                 BIGINT UNSIGNED NOT NULL,
  diagnosis_batch_id        BIGINT UNSIGNED NOT NULL,
  snapshot_data             JSON            NOT NULL,
  diagnosis_summary         TEXT            NULL,
  action_recommendations    TEXT            NULL,
  brand_completeness_checks JSON            NULL,
  question_matrix           JSON            NULL,
  created_at                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_presale_snapshot_report (report_id),
  KEY idx_presale_snapshot_batch (diagnosis_batch_id),
  CONSTRAINT fk_presale_snapshot_report FOREIGN KEY (report_id) REFERENCES reports(id),
  CONSTRAINT fk_presale_snapshot_batch FOREIGN KEY (diagnosis_batch_id) REFERENCES presale_diagnosis_batches(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='presale report snapshot detail';

CREATE TABLE IF NOT EXISTS report_access_logs (
  id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  report_id        BIGINT UNSIGNED NOT NULL,
  share_token      VARCHAR(64)     NOT NULL,
  access_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ip_address       VARCHAR(64)     NULL,
  user_agent       VARCHAR(500)    NULL,
  password_verified TINYINT(1)     NOT NULL DEFAULT 0,
  referer          VARCHAR(500)    NULL,
  KEY idx_report_access_report_time (report_id, access_at),
  KEY idx_report_access_token_time (share_token, access_at),
  CONSTRAINT fk_report_access_report FOREIGN KEY (report_id) REFERENCES reports(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='report share access logs';

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_conversion_rate', 'general', '0.25', 10, 1, 'default conversion coefficient'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_conversion_rate' AND dict_key = 'general');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_conversion_rate', 'medical_health', '0.35', 20, 1, 'default conversion coefficient'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_conversion_rate' AND dict_key = 'medical_health');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_conversion_rate', 'education_training', '0.28', 30, 1, 'default conversion coefficient'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_conversion_rate' AND dict_key = 'education_training');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_conversion_rate', 'tech_internet', '0.22', 40, 1, 'default conversion coefficient'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_conversion_rate' AND dict_key = 'tech_internet');
