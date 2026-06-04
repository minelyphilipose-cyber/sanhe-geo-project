-- ============================================================
-- V203: self-media automatic publish schedule foundation
-- ============================================================
-- WARNING:
-- MySQL DDL is auto-committed and cannot be fully rolled back by Flyway
-- transactions. Run a backup before applying this migration.
--
-- Rollback during the v1 development window, before production schedule data exists:
--   DROP TABLE self_media_publish_schedule;
--   DROP TABLE self_media_publish_schedule_request;
-- Do not use the DROP rollback blindly after real schedule records exist.

CREATE TABLE IF NOT EXISTS self_media_publish_schedule_request (
  id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  brand_id                BIGINT       NOT NULL,
  operator_id             BIGINT       NULL,
  request_idempotency_key VARCHAR(128) NOT NULL,
  normalized_request_hash VARCHAR(64)  NULL,
  request_payload         JSON         NULL,
  status                  VARCHAR(32)  NOT NULL DEFAULT 'created'
    COMMENT 'created/completed/failed/expired',
  schedule_count          INT          NOT NULL DEFAULT 0,
  expires_at              DATETIME     NOT NULL,
  created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_self_media_schedule_request_key (brand_id, request_idempotency_key),
  KEY idx_self_media_schedule_request_operator (operator_id, status, created_at),
  KEY idx_self_media_schedule_request_expires (expires_at),
  CONSTRAINT fk_self_media_schedule_request_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_self_media_schedule_request_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Request-level idempotency record for self-media automatic schedules';

CREATE TABLE IF NOT EXISTS self_media_publish_schedule (
  id                             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  request_id                     BIGINT UNSIGNED NULL,
  request_idempotency_key        VARCHAR(128)    NULL,
  article_id                     BIGINT UNSIGNED NOT NULL,
  distribution_task_id           BIGINT UNSIGNED NULL,
  brand_id                       BIGINT          NOT NULL,
  self_media_account_id          BIGINT UNSIGNED NOT NULL,
  browser_environment_id         BIGINT UNSIGNED NOT NULL,
  browser_environment_account_id BIGINT UNSIGNED NOT NULL,
  platform                       VARCHAR(32)     NOT NULL,

  schedule_strategy              VARCHAR(32)     NOT NULL DEFAULT 'platform_schedule'
    COMMENT 'platform_schedule/semi_auto/immediate_publish_exception',
  planned_publish_at             DATETIME        NOT NULL,
  platform_scheduled_at          DATETIME        NULL,
  schedule_drift_seconds         INT             NULL,
  schedule_drift_reason          VARCHAR(64)     NULL,

  status                         VARCHAR(32)     NOT NULL DEFAULT 'pending'
    COMMENT 'pending/filling/filled_verified/scheduling/scheduled/publish_due/checking_publish_result/published_confirmed/publish_unknown/schedule_failed/publish_failed/cancelled/cancel_pending_platform/manual_required/routed_to_semi_auto',
  queue_kind                     VARCHAR(32)     NOT NULL DEFAULT 'schedule_execution'
    COMMENT 'schedule_execution/publish_result_check',
  queue_priority                 INT             NOT NULL DEFAULT 100,

  platform_schedule_id           VARCHAR(128)    NULL,
  platform_publish_id            VARCHAR(128)    NULL,
  platform_published_url         VARCHAR(1000)   NULL,

  base_idempotency_key           VARCHAR(64)     NOT NULL,
  generation_no                  INT             NOT NULL DEFAULT 1,
  active_unique_key              TINYINT GENERATED ALWAYS AS (
    IF(status IN (
      'pending',
      'filling',
      'filled_verified',
      'scheduling',
      'scheduled',
      'publish_due',
      'checking_publish_result',
      'publish_unknown',
      'cancel_pending_platform'
    ), 1, NULL)
  ) STORED COMMENT 'active-only uniqueness flag',
  active_distribution_task_id    BIGINT UNSIGNED GENERATED ALWAYS AS (
    IF(status IN (
      'pending',
      'filling',
      'filled_verified',
      'scheduling',
      'scheduled',
      'publish_due',
      'checking_publish_result',
      'publish_unknown',
      'cancel_pending_platform'
    ), distribution_task_id, NULL)
  ) STORED COMMENT 'active-only distribution task binding',

  attempt_count                  INT             NOT NULL DEFAULT 0,
  max_attempts                   INT             NOT NULL DEFAULT 1,
  last_attempt_at                DATETIME        NULL,
  next_attempt_at                DATETIME        NULL,
  locked_until                   DATETIME        NULL,

  failure_code                   VARCHAR(64)     NULL,
  failure_message                VARCHAR(512)    NULL,
  diagnostics_json               JSON            NULL,

  created_by                     BIGINT          NULL,
  updated_by                     BIGINT          NULL,
  created_at                     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  scheduled_at                   DATETIME        NULL,
  cancelled_at                   DATETIME        NULL,
  cancel_requested_at            DATETIME        NULL,
  published_confirmed_at         DATETIME        NULL,

  UNIQUE KEY uk_self_media_schedule_active_idempotency (base_idempotency_key, active_unique_key),
  UNIQUE KEY uk_self_media_schedule_active_task (active_distribution_task_id),
  KEY idx_self_media_schedule_request (request_id),
  KEY idx_self_media_schedule_brand_platform (brand_id, platform, status, planned_publish_at),
  KEY idx_self_media_schedule_account_time (self_media_account_id, planned_publish_at),
  KEY idx_self_media_schedule_env_queue (browser_environment_id, queue_kind, status, queue_priority, next_attempt_at),
  KEY idx_self_media_schedule_due_check (status, platform_scheduled_at, queue_priority),
  KEY idx_self_media_schedule_lock (status, locked_until),
  CONSTRAINT fk_self_media_schedule_request FOREIGN KEY (request_id) REFERENCES self_media_publish_schedule_request(id),
  CONSTRAINT fk_self_media_schedule_article FOREIGN KEY (article_id) REFERENCES article_draft(id),
  CONSTRAINT fk_self_media_schedule_task FOREIGN KEY (distribution_task_id) REFERENCES distribution_tasks(id),
  CONSTRAINT fk_self_media_schedule_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_self_media_schedule_account FOREIGN KEY (self_media_account_id) REFERENCES self_media_account(id),
  CONSTRAINT fk_self_media_schedule_env FOREIGN KEY (browser_environment_id) REFERENCES browser_environment(id),
  CONSTRAINT fk_self_media_schedule_env_account FOREIGN KEY (browser_environment_account_id) REFERENCES browser_environment_account(id),
  CONSTRAINT fk_self_media_schedule_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_self_media_schedule_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Automatic self-media platform scheduling and publish confirmation state';
