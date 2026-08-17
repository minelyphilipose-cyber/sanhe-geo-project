CREATE TABLE IF NOT EXISTS self_media_login_observation (
  id                             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  brand_id                       BIGINT          NULL,
  self_media_account_id          BIGINT UNSIGNED NULL,
  browser_environment_id         BIGINT UNSIGNED NULL,
  browser_environment_account_id BIGINT UNSIGNED NULL,
  login_verification_id          BIGINT UNSIGNED NULL,
  platform                       VARCHAR(32)     NOT NULL,
  report_source                  VARCHAR(64)     NOT NULL,
  observation_result             VARCHAR(32)     NOT NULL,
  result_code                    VARCHAR(64)     NULL,
  result_message                 VARCHAR(512)    NULL,
  actual_account_name            VARCHAR(128)    NULL,
  actual_platform_account_id     VARCHAR(128)    NULL,
  current_url                    VARCHAR(1024)   NULL,
  extension_version              VARCHAR(32)     NULL,
  protocol_version               VARCHAR(32)     NULL,
  authoritative                  TINYINT(1)      NOT NULL DEFAULT 0,
  operator_id                    BIGINT          NULL,
  created_at                     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_login_observation_platform_time (platform, created_at),
  KEY idx_login_observation_account_time (self_media_account_id, created_at),
  KEY idx_login_observation_environment_time (browser_environment_id, created_at),
  KEY idx_login_observation_result_time (observation_result, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only self-media login observations; passive failures never overwrite login facts';

CREATE TABLE IF NOT EXISTS self_media_auth_work_order (
  id                       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  brand_id                 BIGINT          NOT NULL,
  browser_environment_id   BIGINT UNSIGNED NULL,
  status                   VARCHAR(32)     NOT NULL DEFAULT 'system_diagnosis',
  priority                 VARCHAR(16)     NOT NULL DEFAULT 'normal',
  owner_user_id            BIGINT          NULL,
  customer_action_required TINYINT(1)      NOT NULL DEFAULT 0,
  issue_summary            VARCHAR(1000)   NULL,
  latest_note              VARCHAR(1000)   NULL,
  next_follow_up_at        DATETIME        NULL,
  resolved_at              DATETIME        NULL,
  created_by               BIGINT          NULL,
  updated_by               BIGINT          NULL,
  created_at               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_auth_work_order_brand (brand_id),
  KEY idx_auth_work_order_owner_status (owner_user_id, status, next_follow_up_at),
  KEY idx_auth_work_order_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Brand-environment authorization operations work order';

CREATE TABLE IF NOT EXISTS self_media_auth_work_order_event (
  id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  work_order_id  BIGINT UNSIGNED NOT NULL,
  action         VARCHAR(64)     NOT NULL,
  from_status    VARCHAR(32)     NULL,
  to_status      VARCHAR(32)     NULL,
  note           VARCHAR(1000)   NULL,
  operator_id    BIGINT          NULL,
  created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_auth_work_order_event_order_time (work_order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only authorization work order event trail';

ALTER TABLE system_alerts
  ADD COLUMN claimed_by BIGINT NULL COMMENT 'operator who claimed the alert' AFTER snoozed_until,
  ADD COLUMN claimed_at DATETIME NULL COMMENT 'alert claim time' AFTER claimed_by,
  ADD COLUMN operation_note VARCHAR(1000) NULL COMMENT 'latest operations note' AFTER claimed_at,
  ADD COLUMN occurrence_count INT NOT NULL DEFAULT 1 COMMENT 'number of refresh occurrences' AFTER operation_note,
  ADD COLUMN last_occurred_at DATETIME NULL COMMENT 'latest occurrence time' AFTER occurrence_count,
  ADD KEY idx_system_alert_claimed (claimed_by, is_resolved, created_at);
