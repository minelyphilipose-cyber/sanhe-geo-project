SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'mobile_dashboard_share'
     AND column_name = 'share_code') = 0,
  'ALTER TABLE mobile_dashboard_share ADD COLUMN share_code VARCHAR(16) NULL AFTER project_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'mobile_dashboard_share'
     AND index_name = 'uk_mobile_dashboard_share_code') = 0,
  'ALTER TABLE mobile_dashboard_share ADD UNIQUE KEY uk_mobile_dashboard_share_code (share_code)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
