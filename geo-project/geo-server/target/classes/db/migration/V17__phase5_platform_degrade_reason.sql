-- ============================================================
-- V17: remove quota fields and add degraded reason
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_platform_config'
      AND column_name = 'degraded_reason'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN degraded_reason VARCHAR(255) NULL COMMENT ''degraded reason'' AFTER degraded',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_platform_config'
      AND column_name = 'remaining_quota'
);
SET @ddl_sql := IF(
    @col_exists = 1,
    'ALTER TABLE ai_platform_config DROP COLUMN remaining_quota',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_platform_config'
      AND column_name = 'used_quota'
);
SET @ddl_sql := IF(
    @col_exists = 1,
    'ALTER TABLE ai_platform_config DROP COLUMN used_quota',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;
