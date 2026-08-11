-- poll_results is the source of truth for the platform dimension and inherits
-- MySQL 8's utf8mb4_0900_ai_ci. Align only the downstream field used in the
-- cross-table UNION, preserving its existing length and nullability.

SET @v336_daily_summary_platform_ready := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'poll_entity_judge_daily_summary'
      AND column_name = 'platform_code'
      AND data_type = 'varchar'
      AND character_maximum_length = 64
      AND character_set_name = 'utf8mb4'
      AND collation_name = 'utf8mb4_0900_ai_ci'
      AND is_nullable = 'YES'
);

SET @v336_daily_summary_platform_ddl := IF(
    @v336_daily_summary_platform_ready = 1,
    'SELECT 1',
    'ALTER TABLE poll_entity_judge_daily_summary MODIFY COLUMN platform_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL'
);

PREPARE v336_daily_summary_platform_stmt FROM @v336_daily_summary_platform_ddl;
EXECUTE v336_daily_summary_platform_stmt;
DEALLOCATE PREPARE v336_daily_summary_platform_stmt;
