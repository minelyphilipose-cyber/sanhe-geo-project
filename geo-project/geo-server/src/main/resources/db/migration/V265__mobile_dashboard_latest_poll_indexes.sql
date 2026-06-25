SET @idx_exists := (
    SELECT COUNT(1)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'poll_results'
       AND index_name = 'idx_poll_result_latest_snapshot'
);
SET @sql := IF(@idx_exists = 0,
    'ALTER TABLE poll_results ADD KEY idx_poll_result_latest_snapshot (project_id, question_tier, status, keyword_result_id, platform_code, batch_date, updated_at, id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
