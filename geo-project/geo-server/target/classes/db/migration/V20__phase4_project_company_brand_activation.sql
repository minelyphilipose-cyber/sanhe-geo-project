-- ============================================================
-- V20: project company binding + optional brand + default paused
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'company_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN company_id BIGINT NULL AFTER project_code',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

UPDATE project p
LEFT JOIN brand b ON b.id = p.brand_id
SET p.company_id = b.company_id
WHERE p.company_id IS NULL;

SET @ddl_sql := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'project'
              AND column_name = 'brand_id'
              AND is_nullable = 'NO'
        ),
        'ALTER TABLE project MODIFY COLUMN brand_id BIGINT NULL',
        'SELECT 1'
    )
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND index_name = 'idx_project_company_id'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE project ADD KEY idx_project_company_id (company_id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @fk_exists := (
    SELECT COUNT(1)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND constraint_type = 'FOREIGN KEY'
      AND constraint_name = 'fk_project_company'
);
SET @ddl_sql := IF(
    @fk_exists = 0,
    'ALTER TABLE project ADD CONSTRAINT fk_project_company FOREIGN KEY (company_id) REFERENCES company(id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

UPDATE project
SET status = 'paused'
WHERE status NOT IN ('active', 'paused');
