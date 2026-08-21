-- Persisted self-media schedule items, quota reservations and generation idempotency.

ALTER TABLE batch_article_generation_batch
  ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER error_message,
  ADD UNIQUE KEY uk_batch_article_generation_idempotency (idempotency_key);

CREATE TABLE project_self_media_schedule_item (
  id                           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  batch_id                     BIGINT UNSIGNED NOT NULL,
  project_id                   BIGINT NOT NULL,
  brand_id                     BIGINT NOT NULL,
  company_id                   BIGINT NOT NULL,
  self_media_account_id        BIGINT UNSIGNED NOT NULL,
  platform                     VARCHAR(64) NOT NULL,
  status                       VARCHAR(32) NOT NULL DEFAULT 'planned'
    COMMENT 'planned/generation_queued/generating/generated/scheduled/generation_failed/schedule_failed/cancelled/skipped',
  planned_execution_at         DATETIME NOT NULL,
  planned_publish_at           DATETIME NOT NULL,
  generation_due_at            DATETIME NOT NULL,
  generation_idempotency_key   VARCHAR(128) NOT NULL,
  generation_batch_id          BIGINT UNSIGNED NULL,
  generation_task_id           BIGINT UNSIGNED NULL,
  generation_claim_token       VARCHAR(64) NULL,
  generation_claimed_at        DATETIME NULL,
  article_id                   BIGINT UNSIGNED NULL,
  schedule_id                  BIGINT UNSIGNED NULL,
  schedule_idempotency_key     VARCHAR(128) NOT NULL,
  schedule_attempt_count       INT NOT NULL DEFAULT 0,
  next_schedule_attempt_at     DATETIME NULL,
  prompt_template_id           BIGINT UNSIGNED NULL,
  prompt_template_version_id   BIGINT UNSIGNED NULL,
  prompt_template_name         VARCHAR(255) NULL,
  generation_attempt_count     INT NOT NULL DEFAULT 0,
  next_generation_attempt_at   DATETIME NULL,
  generation_error_message     VARCHAR(512) NULL,
  schedule_error_code          VARCHAR(64) NULL,
  schedule_error_message       VARCHAR(512) NULL,
  quota_reservation_status     VARCHAR(16) NOT NULL DEFAULT 'pending'
    COMMENT 'pending/reserved/transferred/refunded',
  cancelled_by                 BIGINT NULL,
  cancelled_at                 DATETIME NULL,
  cancellation_reason          VARCHAR(512) NULL,
  created_by                   BIGINT NULL,
  updated_by                   BIGINT NULL,
  created_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_project_self_media_item_generation_key (generation_idempotency_key),
  UNIQUE KEY uk_project_self_media_item_schedule_key (schedule_idempotency_key),
  KEY idx_project_self_media_item_batch (batch_id, id),
  KEY idx_project_self_media_item_due (status, next_generation_attempt_at, generation_due_at),
  KEY idx_project_self_media_item_claim (status, generation_claimed_at),
  KEY idx_project_self_media_item_account_time (self_media_account_id, planned_execution_at),
  KEY idx_project_self_media_item_schedule (schedule_id),
  CONSTRAINT fk_project_self_media_item_batch FOREIGN KEY (batch_id)
    REFERENCES project_self_media_schedule_batch(id),
  CONSTRAINT fk_project_self_media_item_project FOREIGN KEY (project_id) REFERENCES project(id),
  CONSTRAINT fk_project_self_media_item_brand FOREIGN KEY (brand_id) REFERENCES brand(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Persisted project self-media schedule item and delayed article generation state';
