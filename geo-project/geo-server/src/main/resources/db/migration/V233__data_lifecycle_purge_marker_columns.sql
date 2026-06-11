-- ============================================================
-- V233: data lifecycle purge marker columns
-- ============================================================

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'distribution_tasks'
     AND column_name = 'payload_purged_at'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE distribution_tasks ADD COLUMN payload_purged_at DATETIME NULL COMMENT ''request/response/fill payload slim marker'' AFTER response_payload',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'batch_article_generation_task'
     AND column_name = 'snapshot_purged_at'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE batch_article_generation_task ADD COLUMN snapshot_purged_at DATETIME NULL COMMENT ''prompt/input/response snapshot slim marker'' AFTER response_snapshot',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'presale_ai_call'
     AND column_name = 'payload_purged_at'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE presale_ai_call ADD COLUMN payload_purged_at DATETIME NULL COMMENT ''raw payload slim marker after llm_usage_daily_summary coverage''',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'distribution_tasks'
     AND index_name = 'idx_distribution_payload_purge'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE distribution_tasks ADD KEY idx_distribution_payload_purge (payload_purged_at, finished_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'batch_article_generation_task'
     AND index_name = 'idx_batch_article_snapshot_purge'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE batch_article_generation_task ADD KEY idx_batch_article_snapshot_purge (snapshot_purged_at, finished_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'presale_ai_call'
     AND index_name = 'idx_presale_ai_call_payload_purge'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE presale_ai_call ADD KEY idx_presale_ai_call_payload_purge (payload_purged_at, created_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
