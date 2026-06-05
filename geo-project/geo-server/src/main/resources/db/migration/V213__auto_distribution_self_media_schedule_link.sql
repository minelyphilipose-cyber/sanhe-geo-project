SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE table_schema = DATABASE()
    AND table_name = 'content_auto_distribution_item'
    AND column_name = 'self_media_schedule_id'
);

SET @sql := IF(@column_exists = 0,
  'ALTER TABLE content_auto_distribution_item ADD COLUMN self_media_schedule_id BIGINT UNSIGNED NULL AFTER publish_item_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE table_schema = DATABASE()
    AND table_name = 'content_auto_distribution_item'
    AND index_name = 'idx_auto_distribution_self_media_schedule'
);

SET @sql := IF(@index_exists = 0,
  'ALTER TABLE content_auto_distribution_item ADD KEY idx_auto_distribution_self_media_schedule (self_media_schedule_id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
