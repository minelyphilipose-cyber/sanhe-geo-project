-- ============================================================
-- V21: snapshot company/brand names on project
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'company_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN company_name VARCHAR(200) NULL AFTER company_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'brand_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN brand_name VARCHAR(128) NULL AFTER brand_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

UPDATE project p
LEFT JOIN company c ON c.id = p.company_id
LEFT JOIN brand b ON b.id = p.brand_id
SET p.company_name = c.company_name,
    p.brand_name = b.brand_name
WHERE p.company_name IS NULL
   OR p.brand_name IS NULL;
