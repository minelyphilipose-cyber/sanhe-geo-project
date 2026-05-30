-- ============================================================
-- V199: browser environment account model for self-media v1
-- ============================================================
-- WARNING:
-- MySQL DDL is auto-committed and cannot be fully rolled back by Flyway
-- transactions. Run a backup before applying this migration.
--
-- Rollback during the v1 development window, before production data exists:
--   DROP TABLE browser_environment_account;
--   DROP TABLE browser_environment;
-- Do not use the DROP rollback blindly after real environment bindings exist.

CREATE TABLE IF NOT EXISTS browser_environment (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  brand_id            BIGINT       NOT NULL,
  provider            VARCHAR(32)  NOT NULL DEFAULT 'adspower',
  environment_key     VARCHAR(64)  NOT NULL,
  provider_profile_id VARCHAR(128) NOT NULL,
  name                VARCHAR(128) NULL,
  status              VARCHAR(32)  NOT NULL DEFAULT 'active'
    COMMENT 'active/disabled/deleted',
  last_started_at     DATETIME     NULL,
  last_stopped_at     DATETIME     NULL,
  last_error_code     VARCHAR(64)  NULL,
  last_error_message  VARCHAR(512) NULL,
  created_by          BIGINT       NULL,
  updated_by          BIGINT       NULL,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at          DATETIME     NULL,
  active_unique_key   TINYINT GENERATED ALWAYS AS (
    IF(deleted_at IS NULL, 1, NULL)
  ) STORED COMMENT 'active-only uniqueness flag',
  UNIQUE KEY uk_browser_env_key_active (brand_id, environment_key, active_unique_key),
  UNIQUE KEY uk_browser_env_provider_profile_active (provider, provider_profile_id, active_unique_key),
  KEY idx_browser_env_brand_status (brand_id, status),
  CONSTRAINT fk_browser_env_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_browser_env_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_browser_env_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Fingerprint browser environment for self-media publishing';

CREATE TABLE IF NOT EXISTS browser_environment_account (
  id                           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  brand_id                     BIGINT          NOT NULL,
  browser_environment_id       BIGINT UNSIGNED NOT NULL,
  self_media_account_id        BIGINT UNSIGNED NOT NULL,
  platform                     VARCHAR(32)     NOT NULL,
  expected_platform_account_id VARCHAR(128)    NULL,
  expected_account_name        VARCHAR(128)    NULL,
  login_status                 VARCHAR(32)     NOT NULL DEFAULT 'unknown'
    COMMENT 'unknown/logged_in/login_required/mismatch/expired/error',
  last_verified_at             DATETIME        NULL,
  last_login_seen_at           DATETIME        NULL,
  last_error_code              VARCHAR(64)     NULL,
  last_error_message           VARCHAR(512)    NULL,
  created_by                   BIGINT          NULL,
  updated_by                   BIGINT          NULL,
  created_at                   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at                   DATETIME        NULL,
  active_unique_key            TINYINT GENERATED ALWAYS AS (
    IF(deleted_at IS NULL, 1, NULL)
  ) STORED COMMENT 'active-only uniqueness flag',
  UNIQUE KEY uk_env_account_self_media_active (self_media_account_id, active_unique_key),
  KEY idx_env_account_env (browser_environment_id, login_status),
  KEY idx_env_account_brand_platform (brand_id, platform, login_status),
  CONSTRAINT fk_env_account_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_env_account_env FOREIGN KEY (browser_environment_id) REFERENCES browser_environment(id),
  CONSTRAINT fk_env_account_self_media FOREIGN KEY (self_media_account_id) REFERENCES self_media_account(id),
  CONSTRAINT fk_env_account_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_env_account_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Self-media account binding and login state inside a browser environment';
