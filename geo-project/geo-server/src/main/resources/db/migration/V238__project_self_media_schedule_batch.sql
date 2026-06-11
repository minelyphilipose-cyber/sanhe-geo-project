-- ============================================================
-- V238: project-level self-media auto schedule switch and batch records
-- ============================================================

CREATE TABLE IF NOT EXISTS project_self_media_schedule_config (
  id                         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id                 BIGINT          NOT NULL,
  brand_id                   BIGINT          NOT NULL,
  company_id                 BIGINT          NOT NULL,
  auto_schedule_enabled      TINYINT(1)      NOT NULL DEFAULT 0,
  default_schedule_strategy  VARCHAR(32)     NOT NULL DEFAULT 'platform_schedule',
  include_adjusted_workdays  TINYINT(1)      NOT NULL DEFAULT 0,
  remark                     VARCHAR(255)    NULL,
  created_by                 BIGINT          NULL,
  updated_by                 BIGINT          NULL,
  created_at                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_project_self_media_schedule_config_project (project_id),
  KEY idx_project_self_media_schedule_config_enabled (auto_schedule_enabled, project_id),
  KEY idx_project_self_media_schedule_config_brand (brand_id),
  CONSTRAINT fk_project_self_media_schedule_config_project FOREIGN KEY (project_id) REFERENCES project(id),
  CONSTRAINT fk_project_self_media_schedule_config_brand FOREIGN KEY (brand_id) REFERENCES brand(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Project-level self-media automatic schedule switch';

CREATE TABLE IF NOT EXISTS project_self_media_schedule_batch (
  id                         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id                 BIGINT          NOT NULL,
  brand_id                   BIGINT          NOT NULL,
  company_id                 BIGINT          NOT NULL,
  target_month               CHAR(7)         NOT NULL,
  trigger_mode               VARCHAR(32)     NOT NULL DEFAULT 'manual'
    COMMENT 'manual/job',
  status                     VARCHAR(32)     NOT NULL DEFAULT 'previewed'
    COMMENT 'previewed/created/failed/cancelled',
  schedule_strategy          VARCHAR(32)     NOT NULL DEFAULT 'platform_schedule',
  article_count              INT             NOT NULL DEFAULT 0,
  account_count              INT             NOT NULL DEFAULT 0,
  planned_count              INT             NOT NULL DEFAULT 0,
  created_count              INT             NOT NULL DEFAULT 0,
  rejected_count             INT             NOT NULL DEFAULT 0,
  request_payload            JSON            NULL,
  result_snapshot            JSON            NULL,
  failure_message            VARCHAR(512)    NULL,
  created_by                 BIGINT          NULL,
  updated_by                 BIGINT          NULL,
  created_at                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_project_self_media_schedule_batch_month (project_id, target_month),
  KEY idx_project_self_media_schedule_batch_status (status, target_month),
  KEY idx_project_self_media_schedule_batch_brand (brand_id, target_month),
  CONSTRAINT fk_project_self_media_schedule_batch_project FOREIGN KEY (project_id) REFERENCES project(id),
  CONSTRAINT fk_project_self_media_schedule_batch_brand FOREIGN KEY (brand_id) REFERENCES brand(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Monthly self-media automatic schedule batch per project';
