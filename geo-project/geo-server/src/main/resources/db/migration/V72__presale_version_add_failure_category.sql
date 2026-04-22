-- =========================================================================
-- V72: presale_report_version add failure_category
-- Rollback: manual drop column failure_category
-- =========================================================================

SET @db_name = DATABASE();

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'failure_category') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN failure_category VARCHAR(64) NULL COMMENT ''failure category'' AFTER failure_reason',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
