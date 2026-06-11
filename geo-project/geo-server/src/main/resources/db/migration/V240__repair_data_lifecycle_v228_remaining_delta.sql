-- ============================================================
-- V240: repair remaining V228 schema deltas left by databases
-- that applied the early V228 and the already-applied V235.
--
-- Do not edit applied migrations. This version is intentionally
-- idempotent for databases that already ran the final V228.
-- ============================================================

SET @idx_exists := (
  SELECT COUNT(1)
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'poll_results'
     AND index_name = 'idx_poll_result_date_project_tier'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE poll_results ADD KEY idx_poll_result_date_project_tier (batch_date, project_id, question_tier)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE poll_keyword_daily_summary
  MODIFY contact_mention_total BIGINT NOT NULL DEFAULT 0 COMMENT 'SUM(contact_mention_count) from poll_results, null as 0';

ALTER TABLE poll_platform_daily_summary
  MODIFY contact_mention_total BIGINT NOT NULL DEFAULT 0 COMMENT 'SUM(contact_mention_count) from poll_results, null as 0';

ALTER TABLE article_generation_daily_summary
  MODIFY dim_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 canonical hash of generation_date,project_id,article_type,target_channel,status';
