SET @col := (SELECT COUNT(1)
             FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'dispatch_alert'
               AND COLUMN_NAME = 'dedupe_key');
SET @sql := IF(@col = 0,
    'ALTER TABLE dispatch_alert ADD COLUMN dedupe_key VARCHAR(191) NULL COMMENT ''business dedupe key'' AFTER project_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(1)
             FROM INFORMATION_SCHEMA.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'dispatch_alert'
               AND INDEX_NAME = 'idx_dispatch_alert_dedupe');
SET @sql := IF(@idx = 0,
    'ALTER TABLE dispatch_alert ADD INDEX idx_dispatch_alert_dedupe (dedupe_key, status, created_at)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
