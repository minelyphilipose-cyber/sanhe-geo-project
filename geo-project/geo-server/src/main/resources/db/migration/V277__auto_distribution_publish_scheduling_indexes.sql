-- Improve auto-distribution publish scheduling scans.

SET @idx_exists := (
    SELECT COUNT(1)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'content_batch_publish_item'
       AND index_name = 'idx_batch_publish_item_due_lane'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE content_batch_publish_item ADD KEY idx_batch_publish_item_due_lane (status, planned_at, id, platform_key, target_site_id, target_brand_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'content_auto_distribution_item'
       AND index_name = 'idx_auto_distribution_site_schedule'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE content_auto_distribution_item ADD KEY idx_auto_distribution_site_schedule (plan_date, status, target_kind, target_id, planned_publish_at, id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
