SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_platform_config'
      AND COLUMN_NAME = 'platform_logo_object_key'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN platform_logo_object_key VARCHAR(500) NULL COMMENT ''平台Logo对象存储Key'' AFTER platform_logo_url',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
