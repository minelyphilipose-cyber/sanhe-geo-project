-- ============================================================
-- V114: Extension token lookup hash and recommended version
-- ============================================================
-- NOTE: This branch already contains V113 for company package quota.
-- If migration ordering changes before merge, this file may be renumbered.

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'extension_session'
      AND column_name = 'token_lookup_hash'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE extension_session
       ADD COLUMN token_lookup_hash VARCHAR(64) NULL AFTER operator_id,
       ADD UNIQUE KEY uk_extension_session_lookup_hash (token_lookup_hash)',
    'SELECT ''column extension_session.token_lookup_hash already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'extension_version_config'
      AND column_name = 'recommended_version'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE extension_version_config
       ADD COLUMN recommended_version VARCHAR(32) NULL AFTER latest_version',
    'SELECT ''column extension_version_config.recommended_version already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
