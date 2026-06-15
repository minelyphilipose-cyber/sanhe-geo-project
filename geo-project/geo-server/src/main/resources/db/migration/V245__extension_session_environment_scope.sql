SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'extension_session'
     AND column_name = 'environment_key') = 0,
  'ALTER TABLE extension_session ADD COLUMN environment_key VARCHAR(128) NULL AFTER install_id',
  'SELECT ''column extension_session.environment_key already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'extension_session'
     AND column_name = 'provider_profile_id') = 0,
  'ALTER TABLE extension_session ADD COLUMN provider_profile_id VARCHAR(128) NULL AFTER environment_key',
  'SELECT ''column extension_session.provider_profile_id already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'extension_session'
     AND index_name = 'idx_extension_session_brand_environment') = 0,
  'ALTER TABLE extension_session ADD INDEX idx_extension_session_brand_environment (brand_id, environment_key, provider_profile_id, status)',
  'SELECT ''index idx_extension_session_brand_environment already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
