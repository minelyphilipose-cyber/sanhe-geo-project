-- ============================================================
-- V120: repair extension_session token columns for environments
-- that applied an early V112 draft before the final session schema.
-- ============================================================

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'extension_session'
     AND column_name = 'token_lookup_hash') = 0,
  'ALTER TABLE extension_session ADD COLUMN token_lookup_hash VARCHAR(64) NULL AFTER operator_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'extension_session'
     AND index_name = 'uk_extension_session_lookup_hash') = 0,
  'ALTER TABLE extension_session ADD UNIQUE KEY uk_extension_session_lookup_hash (token_lookup_hash)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'extension_session'
     AND column_name = 'token_hash_alg') = 0,
  'ALTER TABLE extension_session ADD COLUMN token_hash_alg VARCHAR(16) NOT NULL DEFAULT ''SHA-256'' AFTER token_hash',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'extension_session'
     AND column_name = 'token_salt') = 0,
  'ALTER TABLE extension_session ADD COLUMN token_salt VARCHAR(64) NULL AFTER token_hash_alg',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'extension_session'
     AND column_name = 'device_fingerprint_hash_alg') = 0,
  'ALTER TABLE extension_session ADD COLUMN device_fingerprint_hash_alg VARCHAR(16) NOT NULL DEFAULT ''SHA-256'' AFTER device_fingerprint_hash',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
