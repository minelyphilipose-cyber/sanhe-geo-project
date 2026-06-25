-- Support recent CAPACITY_RETRY_EXHAUSTED scans without walking old dispatch dead letters.

SET @idx := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'dispatch_task'
               AND INDEX_NAME = 'idx_dispatch_task_status_updated');
SET @sql := IF(@idx = 0,
    'ALTER TABLE dispatch_task ADD INDEX idx_dispatch_task_status_updated (status, updated_at)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
