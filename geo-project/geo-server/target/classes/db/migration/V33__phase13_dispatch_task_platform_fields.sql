-- ============================================================
-- V33: dispatch task platform/channel/error context fields
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'dispatch_task'
      AND column_name = 'platform_code'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE dispatch_task ADD COLUMN platform_code VARCHAR(64) NULL COMMENT ''executed platform code'' AFTER project_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'dispatch_task'
      AND column_name = 'current_channel'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE dispatch_task ADD COLUMN current_channel VARCHAR(32) NULL COMMENT ''primary|backup_key|backup_provider'' AFTER platform_code',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'dispatch_task'
      AND column_name = 'error_context'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE dispatch_task ADD COLUMN error_context JSON NULL COMMENT ''error context json'' AFTER last_error',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'dispatch_task'
      AND index_name = 'idx_dispatch_task_platform_status'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE dispatch_task ADD KEY idx_dispatch_task_platform_status (platform_code, status, updated_at)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;
