-- ============================================================
-- V295: self-media runtime status phase 1 foundation
-- ============================================================

CREATE TABLE IF NOT EXISTS extension_runtime_status (
  id                              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  install_id                      VARCHAR(128)  NOT NULL,
  extension_session_id            BIGINT UNSIGNED NULL,
  browser_environment_id          BIGINT UNSIGNED NULL,
  browser_environment_account_id  BIGINT UNSIGNED NULL,
  brand_id                        BIGINT        NULL,
  platform                        VARCHAR(32)   NULL,
  environment_key                 VARCHAR(128)  NULL,
  provider_profile_id             VARCHAR(128)  NOT NULL,
  extension_version               VARCHAR(32)   NOT NULL,
  protocol_version                VARCHAR(32)   NOT NULL DEFAULT '1',
  current_url                     VARCHAR(1024) NULL,
  detected_platform               VARCHAR(32)   NULL,
  detected_account_name           VARCHAR(255)  NULL,
  detected_platform_account_id    VARCHAR(128)  NULL,
  login_status                    VARCHAR(32)   NOT NULL DEFAULT 'unknown',
  runtime_stage                   VARCHAR(64)   NULL,
  runtime_stage_at                DATETIME      NULL,
  runtime_stage_message           VARCHAR(512)  NULL,
  capabilities_json               JSON          NULL,
  last_task_id                    BIGINT        NULL,
  last_error_code                 VARCHAR(128)  NULL,
  last_error_message              VARCHAR(512)  NULL,
  last_seen_at                    DATETIME      NOT NULL,
  created_at                      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_extension_runtime_provider_install (provider_profile_id, install_id),
  KEY idx_extension_runtime_env_platform (browser_environment_id, detected_platform),
  KEY idx_extension_runtime_env_account (browser_environment_account_id),
  KEY idx_extension_runtime_seen (last_seen_at),
  CONSTRAINT fk_extension_runtime_session FOREIGN KEY (extension_session_id) REFERENCES extension_session(id),
  CONSTRAINT fk_extension_runtime_environment FOREIGN KEY (browser_environment_id) REFERENCES browser_environment(id),
  CONSTRAINT fk_extension_runtime_environment_account FOREIGN KEY (browser_environment_account_id) REFERENCES browser_environment_account(id),
  CONSTRAINT fk_extension_runtime_brand FOREIGN KEY (brand_id) REFERENCES brand(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Latest extension runtime state reported from AdsPower environments';

CREATE TABLE IF NOT EXISTS local_agent_runtime_status (
  id                         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  machine_id                 VARCHAR(128) NOT NULL,
  active_profile             VARCHAR(32)  NOT NULL,
  session_id                 BIGINT       NULL,
  operator_id                BIGINT       NULL,
  helper_version             VARCHAR(32)  NOT NULL,
  protocol_version           VARCHAR(32)  NOT NULL DEFAULT '1',
  helper_name                VARCHAR(128) NULL,
  adspower_api_ok            TINYINT(1)   NOT NULL DEFAULT 0,
  adspower_api_base          VARCHAR(255) NULL,
  running_task_count         INT          NOT NULL DEFAULT 0,
  capacity                   INT          NOT NULL DEFAULT 1,
  supported_platforms_json   JSON         NULL,
  capabilities_json          JSON         NULL,
  last_error_code            VARCHAR(128) NULL,
  last_error_message         VARCHAR(512) NULL,
  last_seen_at               DATETIME     NOT NULL,
  created_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_local_agent_runtime_machine_profile (machine_id, active_profile),
  KEY idx_local_agent_runtime_session (session_id),
  KEY idx_local_agent_runtime_operator (operator_id),
  KEY idx_local_agent_runtime_seen (last_seen_at),
  CONSTRAINT fk_local_agent_runtime_session FOREIGN KEY (session_id) REFERENCES local_agent_session(id),
  CONSTRAINT fk_local_agent_runtime_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Latest local helper runtime state reported by paired helpers';

ALTER TABLE self_media_publish_schedule
  ADD COLUMN runtime_stage VARCHAR(64) NULL AFTER diagnostics_json,
  ADD COLUMN runtime_stage_at DATETIME NULL AFTER runtime_stage,
  ADD COLUMN runtime_stage_message VARCHAR(512) NULL AFTER runtime_stage_at,
  ADD COLUMN runtime_worker_id VARCHAR(128) NULL AFTER runtime_stage_message,
  ADD COLUMN runtime_extension_install_id VARCHAR(128) NULL AFTER runtime_worker_id,
  ADD KEY idx_self_media_schedule_runtime_stage (runtime_stage, runtime_stage_at);
