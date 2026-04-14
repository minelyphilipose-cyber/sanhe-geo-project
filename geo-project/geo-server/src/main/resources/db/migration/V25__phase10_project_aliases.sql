-- ============================================================
-- V25: project aliases
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'project_aliases'
);

SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN project_aliases VARCHAR(1000) NULL COMMENT ''aliases split by comma'' AFTER project_name',
    'SELECT 1'
);

PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

