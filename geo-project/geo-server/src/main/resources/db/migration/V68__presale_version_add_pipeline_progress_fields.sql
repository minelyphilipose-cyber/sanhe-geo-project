-- =========================================================================
-- V68: presale_report_version pipeline progress fields
-- Rollback: reversible via dropping added columns (manual rollback script)
-- =========================================================================

SET @db_name = DATABASE();

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'generation_stage') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN generation_stage VARCHAR(40) NULL COMMENT ''pipeline stage'' AFTER generation_status',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'batch1_total_calls') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN batch1_total_calls INT NULL COMMENT ''batch1 total calls'' AFTER completed_llm_calls',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'batch1_completed_calls') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN batch1_completed_calls INT NOT NULL DEFAULT 0 COMMENT ''batch1 completed calls'' AFTER batch1_total_calls',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'batch2_total_calls') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN batch2_total_calls INT NULL COMMENT ''batch2 total calls'' AFTER batch1_completed_calls',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'batch2_completed_calls') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN batch2_completed_calls INT NOT NULL DEFAULT 0 COMMENT ''batch2 completed calls'' AFTER batch2_total_calls',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'extracted_competitor_count') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN extracted_competitor_count TINYINT NULL COMMENT ''extracted competitor count'' AFTER batch2_completed_calls',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

