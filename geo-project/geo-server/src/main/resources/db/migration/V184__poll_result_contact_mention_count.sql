SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'poll_results'
      AND COLUMN_NAME = 'contact_mention_count'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE poll_results ADD COLUMN contact_mention_count INT NOT NULL DEFAULT 0 COMMENT ''联系方式出现次数'' AFTER contact_mentioned',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
