SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_platform_config'
      AND COLUMN_NAME = 'platform_logo_url'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN platform_logo_url VARCHAR(1000) NULL COMMENT ''平台Logo地址'' AFTER platform_home_url',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
