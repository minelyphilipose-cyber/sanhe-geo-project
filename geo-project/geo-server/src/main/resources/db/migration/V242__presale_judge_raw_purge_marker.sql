-- ============================================================
-- V242: presale judge raw response slim marker
-- ============================================================

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'presale_ai_prompt_judge_result'
     AND column_name = 'raw_purged_at'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE presale_ai_prompt_judge_result ADD COLUMN raw_purged_at DATETIME NULL COMMENT ''raw_judge_response slim marker'' AFTER raw_judge_response',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'presale_ai_prompt_judge_result'
     AND index_name = 'idx_presale_judge_raw_purge'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE presale_ai_prompt_judge_result ADD KEY idx_presale_judge_raw_purge (raw_purged_at, updated_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
