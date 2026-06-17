SET @col_nullable := (
  SELECT IS_NULLABLE
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'self_media_publish_schedule'
    AND COLUMN_NAME = 'browser_environment_id'
);
SET @sql := IF(@col_nullable = 'NO',
  'ALTER TABLE self_media_publish_schedule MODIFY COLUMN browser_environment_id BIGINT UNSIGNED NULL',
  'SELECT ''column self_media_publish_schedule.browser_environment_id already nullable'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_nullable := (
  SELECT IS_NULLABLE
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'self_media_publish_schedule'
    AND COLUMN_NAME = 'browser_environment_account_id'
);
SET @sql := IF(@col_nullable = 'NO',
  'ALTER TABLE self_media_publish_schedule MODIFY COLUMN browser_environment_account_id BIGINT UNSIGNED NULL',
  'SELECT ''column self_media_publish_schedule.browser_environment_account_id already nullable'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
