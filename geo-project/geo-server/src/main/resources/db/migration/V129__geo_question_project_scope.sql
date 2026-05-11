-- Add project-level scope for layered GEO question workorders.

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'geo_question_workorder' AND column_name = 'project_id'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE geo_question_workorder ADD COLUMN project_id BIGINT NULL AFTER company_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'geo_question_workorder' AND index_name = 'idx_geo_qw_project_status'
);
SET @ddl_sql := IF(@idx_exists = 0,
    'ALTER TABLE geo_question_workorder ADD INDEX idx_geo_qw_project_status (project_id, status)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'geo_question_version' AND column_name = 'project_id'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE geo_question_version ADD COLUMN project_id BIGINT NULL AFTER company_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'geo_question_version' AND index_name = 'idx_geo_qv_project'
);
SET @ddl_sql := IF(@idx_exists = 0,
    'ALTER TABLE geo_question_version ADD INDEX idx_geo_qv_project (project_id, status)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;
