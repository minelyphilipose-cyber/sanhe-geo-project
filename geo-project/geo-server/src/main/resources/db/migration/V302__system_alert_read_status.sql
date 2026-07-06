-- V302: separate station-message read state from business resolution state

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_alerts' AND COLUMN_NAME = 'read_at');
SET @sql := IF(@col = 0,
    'ALTER TABLE system_alerts ADD COLUMN read_at DATETIME NULL COMMENT ''message read time'' AFTER resolved_at',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_alerts' AND INDEX_NAME = 'idx_system_alerts_recipient_read');
SET @sql := IF(@idx = 0,
    'ALTER TABLE system_alerts ADD INDEX idx_system_alerts_recipient_read (recipient_user_id, read_at, created_at)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
