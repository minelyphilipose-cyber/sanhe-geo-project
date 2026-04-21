-- =========================================================================
-- V67: presale_report_version schema compatibility patch
--
-- Why:
--   Some environments still have an older presale_report_version schema.
--   Current entity mappings reference columns that may not exist yet.
--
-- Note:
--   Do NOT use "ADD COLUMN IF NOT EXISTS" for broad MySQL compatibility.
--   We use information_schema checks + dynamic SQL to keep this idempotent.
-- =========================================================================

SET @db_name = DATABASE();

-- derived_from_version_id
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'derived_from_version_id') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN derived_from_version_id BIGINT NULL COMMENT ''derived source version id'' AFTER version_no',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- total_llm_calls
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'total_llm_calls') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN total_llm_calls INT NULL COMMENT ''total llm calls''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- completed_llm_calls
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'completed_llm_calls') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN completed_llm_calls INT NULL COMMENT ''completed llm calls''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- is_degraded
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'is_degraded') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN is_degraded TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''is degraded''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- degraded_platforms
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'degraded_platforms') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN degraded_platforms JSON NULL COMMENT ''degraded platforms''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- failure_reason
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'failure_reason') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN failure_reason TEXT NULL COMMENT ''failure reason''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- raw_snapshot_json
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'raw_snapshot_json') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN raw_snapshot_json JSON NULL COMMENT ''raw snapshot''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- computed_snapshot_json
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'computed_snapshot_json') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN computed_snapshot_json JSON NULL COMMENT ''computed snapshot''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- editable_content_json
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'editable_content_json') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN editable_content_json JSON NULL COMMENT ''editable snapshot''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- frozen_at
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'frozen_at') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN frozen_at DATETIME NULL COMMENT ''frozen at''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- frozen_by
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'frozen_by') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN frozen_by BIGINT NULL COMMENT ''frozen by''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- frozen_reason
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'frozen_reason') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN frozen_reason VARCHAR(100) NULL COMMENT ''frozen reason''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- content_updated_at
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'content_updated_at') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN content_updated_at DATETIME NULL COMMENT ''content updated at''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- export_success_count
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'export_success_count') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN export_success_count INT NOT NULL DEFAULT 0 COMMENT ''export success count''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- export_success_at
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'export_success_at') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN export_success_at DATETIME NULL COMMENT ''export success at''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- created_by
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db_name
     AND TABLE_NAME = 'presale_report_version'
     AND COLUMN_NAME = 'created_by') = 0,
  'ALTER TABLE presale_report_version ADD COLUMN created_by BIGINT NULL COMMENT ''created by''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

