-- ============================================================
-- V118: repair cookie credential columns for environments that
-- applied an early V112 draft before the final credential schema.
-- ============================================================

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'self_media_cookie_credential'
     AND column_name = 'cookie_iv_base64') = 0,
  'ALTER TABLE self_media_cookie_credential ADD COLUMN cookie_iv_base64 VARCHAR(64) NULL AFTER cookies_ciphertext',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'self_media_cookie_credential'
     AND column_name = 'encrypted_dek') = 0,
  'ALTER TABLE self_media_cookie_credential ADD COLUMN encrypted_dek TEXT NULL AFTER cookie_iv_base64',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'self_media_cookie_credential'
     AND column_name = 'master_key_id') = 0,
  'ALTER TABLE self_media_cookie_credential ADD COLUMN master_key_id VARCHAR(128) NULL AFTER encrypted_dek',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'self_media_cookie_credential'
     AND column_name = 'cipher_alg') = 0,
  'ALTER TABLE self_media_cookie_credential ADD COLUMN cipher_alg VARCHAR(32) NOT NULL DEFAULT ''AES-256-GCM'' AFTER master_key_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'self_media_cookie_credential'
     AND column_name = 'aad_context') = 0,
  'ALTER TABLE self_media_cookie_credential ADD COLUMN aad_context VARCHAR(512) NULL AFTER cipher_alg',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'self_media_cookie_credential'
     AND column_name = 'captured_fingerprint_json') = 0,
  'ALTER TABLE self_media_cookie_credential ADD COLUMN captured_fingerprint_json JSON NULL AFTER user_agent',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'self_media_cookie_credential'
     AND column_name = 'required_cookie_status') = 0,
  'ALTER TABLE self_media_cookie_credential ADD COLUMN required_cookie_status JSON NULL COMMENT ''records which required cookies were captured, e.g. {sessionid: present}'' AFTER captured_fingerprint_json',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
