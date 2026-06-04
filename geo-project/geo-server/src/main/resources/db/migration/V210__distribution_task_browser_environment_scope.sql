SET @schema_name = DATABASE();

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'distribution_tasks' AND COLUMN_NAME = 'browser_environment_id') = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN browser_environment_id BIGINT UNSIGNED NULL AFTER self_media_account_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'distribution_tasks' AND COLUMN_NAME = 'browser_environment_account_id') = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN browser_environment_account_id BIGINT UNSIGNED NULL AFTER browser_environment_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'distribution_tasks' AND COLUMN_NAME = 'environment_key') = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN environment_key VARCHAR(64) NULL AFTER browser_environment_account_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'distribution_tasks' AND COLUMN_NAME = 'environment_provider') = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN environment_provider VARCHAR(32) NULL AFTER environment_key',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'distribution_tasks' AND COLUMN_NAME = 'provider_profile_id') = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN provider_profile_id VARCHAR(128) NULL AFTER environment_provider',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'distribution_tasks' AND INDEX_NAME = 'idx_distribution_task_environment_scope') = 0,
  'ALTER TABLE distribution_tasks ADD INDEX idx_distribution_task_environment_scope (environment_key, dispatch_mode, status, fill_token_issued_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
