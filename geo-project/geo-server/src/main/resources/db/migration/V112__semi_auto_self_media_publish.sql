-- ============================================================
-- V112: Semi-auto self-media publish foundation
-- ============================================================
-- WARNING: This migration uses defensive idempotent patterns
-- (information_schema checks, IF NOT EXISTS) only because some local
-- development environments had partial residue from earlier drafts.
--
-- This is not a team-wide DDL convention. New migrations should use plain DDL.
-- If V112 fails partway in any environment, do not manually mark it as success:
-- investigate the failure and clean up the partial schema first.

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'self_media_account' AND column_name = 'auth_mode') = 0,
  'ALTER TABLE self_media_account ADD COLUMN auth_mode VARCHAR(16) NOT NULL DEFAULT ''OAUTH'' COMMENT ''OAUTH/COOKIE/API_KEY/MANUAL'' AFTER status',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'self_media_account' AND column_name = 'deleted_at') = 0,
  'ALTER TABLE self_media_account ADD COLUMN deleted_at DATETIME NULL AFTER updated_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'self_media_account' AND column_name = 'deleted_by') = 0,
  'ALTER TABLE self_media_account ADD COLUMN deleted_by BIGINT NULL AFTER deleted_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'self_media_account' AND index_name = 'idx_self_media_auth_mode') = 0,
  'ALTER TABLE self_media_account ADD KEY idx_self_media_auth_mode (platform, auth_mode, status)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'self_media_account' AND index_name = 'idx_self_media_deleted_at') = 0,
  'ALTER TABLE self_media_account ADD KEY idx_self_media_deleted_at (deleted_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND column_name = 'cover_image_url') = 0,
  'ALTER TABLE article_draft ADD COLUMN cover_image_url VARCHAR(1000) NULL COMMENT ''semi-auto cover image url'' AFTER title',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND column_name = 'tags_json') = 0,
  'ALTER TABLE article_draft ADD COLUMN tags_json JSON NULL COMMENT ''platform tags backup'' AFTER cover_image_url',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND column_name = 'category') = 0,
  'ALTER TABLE article_draft ADD COLUMN category VARCHAR(64) NULL COMMENT ''platform category backup'' AFTER tags_json',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'distribution_tasks' AND column_name = 'dispatch_mode') = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN dispatch_mode VARCHAR(16) NOT NULL DEFAULT ''AUTO'' COMMENT ''AUTO/SEMI_AUTO/MANUAL'' AFTER integration_method',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'distribution_tasks' AND column_name = 'fill_token_issued_at') = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN fill_token_issued_at DATETIME NULL AFTER locked_until',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'distribution_tasks' AND column_name = 'filled_at') = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN filled_at DATETIME NULL AFTER fill_token_issued_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'distribution_tasks' AND column_name = 'published_at') = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN published_at DATETIME NULL AFTER filled_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'distribution_tasks' AND column_name = 'published_by') = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN published_by BIGINT NULL AFTER published_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'distribution_tasks' AND column_name = 'last_heartbeat_at') = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN last_heartbeat_at DATETIME NULL AFTER published_by',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'distribution_tasks' AND column_name = 'fill_payload') = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN fill_payload JSON NULL AFTER request_payload',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'distribution_tasks' AND index_name = 'idx_distribution_mode_status') = 0,
  'ALTER TABLE distribution_tasks ADD KEY idx_distribution_mode_status (dispatch_mode, status, created_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'distribution_tasks' AND index_name = 'idx_distribution_reclaim') = 0,
  'SELECT 1',
  'ALTER TABLE distribution_tasks DROP INDEX idx_distribution_reclaim'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'distribution_tasks' AND index_name = 'idx_distribution_token_reclaim') = 0,
  'ALTER TABLE distribution_tasks ADD KEY idx_distribution_token_reclaim (dispatch_mode, status, fill_token_issued_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'distribution_tasks' AND index_name = 'idx_distribution_heartbeat_reclaim') = 0,
  'ALTER TABLE distribution_tasks ADD KEY idx_distribution_heartbeat_reclaim (dispatch_mode, status, last_heartbeat_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE distribution_tasks
SET dispatch_mode = 'MANUAL'
WHERE integration_method = 'manual' AND dispatch_mode = 'AUTO';

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE table_schema = DATABASE() AND table_name = 'distribution_tasks' AND constraint_name = 'fk_distribution_published_by') = 0,
  'ALTER TABLE distribution_tasks ADD CONSTRAINT fk_distribution_published_by FOREIGN KEY (published_by) REFERENCES sys_user(id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS brand_operator_assignment (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  brand_id BIGINT NOT NULL,
  operator_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL COMMENT 'PRIMARY/SECONDARY/VIEWER',
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  assigned_by BIGINT NULL,
  active_unique_key TINYINT GENERATED ALWAYS AS (
    CASE WHEN status = 'active' THEN 1 ELSE NULL END
  ) STORED,
  UNIQUE KEY uk_brand_operator_active (brand_id, operator_id, active_unique_key),
  KEY idx_brand_operator (operator_id, status),
  KEY idx_brand_assignment_brand (brand_id, status),
  CONSTRAINT fk_brand_assignment_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_brand_assignment_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id),
  CONSTRAINT fk_brand_assignment_assigned_by FOREIGN KEY (assigned_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='brand level operator assignment';

CREATE TABLE IF NOT EXISTS self_media_cookie_credential (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  self_media_account_id BIGINT UNSIGNED NOT NULL,
  brand_id BIGINT NOT NULL,
  platform VARCHAR(32) NOT NULL,
  version INT NOT NULL,
  cookies_ciphertext LONGTEXT NOT NULL,
  cookie_iv_base64 VARCHAR(64) NOT NULL,
  encrypted_dek TEXT NOT NULL,
  master_key_id VARCHAR(128) NOT NULL,
  cipher_alg VARCHAR(32) NOT NULL DEFAULT 'AES-256-GCM',
  aad_context VARCHAR(512) NOT NULL,
  user_agent VARCHAR(512) NULL,
  captured_fingerprint_json JSON NULL,
  required_cookie_status JSON NULL
    COMMENT 'records which required cookies were captured, e.g. {sessionid: present}',
  captured_by BIGINT NULL,
  captured_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  valid_from DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  valid_until DATETIME NULL,
  destroyed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cookie_credential_version (self_media_account_id, version),
  KEY idx_cookie_credential_active (self_media_account_id, valid_until, destroyed_at),
  KEY idx_cookie_credential_brand (brand_id, platform, created_at),
  CONSTRAINT fk_cookie_credential_account
    FOREIGN KEY (self_media_account_id) REFERENCES self_media_account(id),
  CONSTRAINT fk_cookie_credential_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_cookie_credential_captured_by FOREIGN KEY (captured_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='versioned encrypted cookie credentials for semi-auto self-media accounts';

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'self_media_cookie_credential'
     AND COLUMN_NAME = 'cookie_iv_base64') = 0,
  'ALTER TABLE self_media_cookie_credential ADD COLUMN cookie_iv_base64 VARCHAR(64) NOT NULL AFTER cookies_ciphertext',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS extension_session (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  token_hash_alg VARCHAR(16) NOT NULL DEFAULT 'SHA-256',
  install_id VARCHAR(128) NOT NULL,
  device_fingerprint_hash VARCHAR(128) NULL,
  device_fingerprint_hash_alg VARCHAR(16) NOT NULL DEFAULT 'SHA-256',
  extension_version VARCHAR(32) NULL,
  user_agent VARCHAR(512) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  bound_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at DATETIME NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  revoked_by BIGINT NULL,
  UNIQUE KEY uk_extension_token_hash (token_hash),
  KEY idx_extension_operator (operator_id, status),
  KEY idx_extension_install (install_id, status),
  CONSTRAINT fk_extension_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id),
  CONSTRAINT fk_extension_revoked_by FOREIGN KEY (revoked_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='browser extension long-token sessions';

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  actor_type VARCHAR(32) NOT NULL DEFAULT 'OPERATOR',
  actor_id BIGINT NULL,
  brand_id BIGINT NULL,
  account_id BIGINT UNSIGNED NULL,
  task_id BIGINT UNSIGNED NULL,
  target_type VARCHAR(64) NULL,
  target_id VARCHAR(128) NULL,
  result VARCHAR(16) NOT NULL COMMENT 'SUCCESS/FAILURE/DENIED',
  sensitive TINYINT(1) NOT NULL DEFAULT 0,
  mode VARCHAR(16) NOT NULL DEFAULT 'ASYNC'
    COMMENT 'SYNC=sensitive DB+file before return; ASYNC=background DB+file; FILE_ONLY=high-volume file log',
  ip_address VARCHAR(45) NULL,
  user_agent VARCHAR(512) NULL,
  extension_session_id BIGINT UNSIGNED NULL,
  device_fingerprint_hash VARCHAR(128) NULL,
  request_id VARCHAR(64) NULL,
  trace_id VARCHAR(64) NULL,
  detail_json JSON NULL,
  error_code VARCHAR(64) NULL,
  error_message VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_audit_event_id (event_id),
  KEY idx_audit_created (created_at),
  KEY idx_audit_actor_created (actor_id, created_at),
  KEY idx_audit_brand_created (brand_id, created_at),
  KEY idx_audit_actor_brand_created (actor_id, brand_id, created_at),
  KEY idx_audit_account_created (account_id, created_at),
  KEY idx_audit_task_created (task_id, created_at),
  KEY idx_audit_event_type_created (event_type, created_at),
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES sys_user(id),
  CONSTRAINT fk_audit_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_audit_account FOREIGN KEY (account_id) REFERENCES self_media_account(id),
  CONSTRAINT fk_audit_task FOREIGN KEY (task_id) REFERENCES distribution_tasks(id),
  CONSTRAINT fk_audit_extension_session FOREIGN KEY (extension_session_id) REFERENCES extension_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='security audit log';

CREATE TABLE IF NOT EXISTS extension_version_config (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  platform VARCHAR(32) NOT NULL DEFAULT 'chrome',
  min_version VARCHAR(32) NOT NULL,
  latest_version VARCHAR(32) NOT NULL,
  force_upgrade TINYINT(1) NOT NULL DEFAULT 0,
  download_url VARCHAR(1000) NULL,
  release_note VARCHAR(1000) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  active_unique_key TINYINT GENERATED ALWAYS AS (
    CASE WHEN status = 'active' THEN 1 ELSE NULL END
  ) STORED,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_extension_version_active (platform, active_unique_key),
  KEY idx_extension_version_status (status, updated_at),
  CONSTRAINT fk_extension_version_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_extension_version_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='browser extension minimum supported version config';

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'extension_session' AND column_name = 'token_hash_alg') = 0,
  'ALTER TABLE extension_session ADD COLUMN token_hash_alg VARCHAR(16) NOT NULL DEFAULT ''SHA-256'' AFTER token_hash',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'extension_session' AND column_name = 'device_fingerprint_hash_alg') = 0,
  'ALTER TABLE extension_session ADD COLUMN device_fingerprint_hash_alg VARCHAR(16) NOT NULL DEFAULT ''SHA-256'' AFTER device_fingerprint_hash',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'audit_log' AND index_name = 'idx_audit_actor_brand_created') = 0,
  'ALTER TABLE audit_log ADD KEY idx_audit_actor_brand_created (actor_id, brand_id, created_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'extension_version_config' AND column_name = 'created_by') = 0,
  'ALTER TABLE extension_version_config ADD COLUMN created_by BIGINT NULL AFTER status',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'extension_version_config' AND column_name = 'updated_by') = 0,
  'ALTER TABLE extension_version_config ADD COLUMN updated_by BIGINT NULL AFTER created_by',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE table_schema = DATABASE() AND table_name = 'extension_version_config' AND constraint_name = 'fk_extension_version_created_by') = 0,
  'ALTER TABLE extension_version_config ADD CONSTRAINT fk_extension_version_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.table_constraints
   WHERE table_schema = DATABASE() AND table_name = 'extension_version_config' AND constraint_name = 'fk_extension_version_updated_by') = 0,
  'ALTER TABLE extension_version_config ADD CONSTRAINT fk_extension_version_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
