-- ============================================================
-- V39: brand statement generation fields + perms
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand'
      AND column_name = 'standard_statement'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN standard_statement JSON NULL COMMENT ''structured brand statement'' AFTER business_standard_statement',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand'
      AND column_name = 'statement_status'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN statement_status VARCHAR(16) NULL COMMENT ''pending/draft/locked'' AFTER standard_statement',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand'
      AND column_name = 'statement_generated_at'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN statement_generated_at DATETIME NULL AFTER statement_status',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand'
      AND column_name = 'statement_locked_at'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN statement_locked_at DATETIME NULL AFTER statement_generated_at',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand'
      AND column_name = 'statement_locked_by'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN statement_locked_by BIGINT NULL AFTER statement_locked_at',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand'
      AND column_name = 'statement_version'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN statement_version INT NULL AFTER statement_locked_by',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand'
      AND column_name = 'statement_history'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN statement_history JSON NULL AFTER statement_version',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dispatch_task_type', 'BRAND_STATEMENT_GENERATION', '品牌标准表达生成', 15, 1, 'event triggered'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_dict_item
    WHERE dict_type = 'dispatch_task_type'
      AND dict_key = 'BRAND_STATEMENT_GENERATION'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'brand.statement.lock', 'Brand Statement Lock', 'brand', 'statement_lock', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'brand.statement.lock');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('brand.statement.lock')
WHERE r.role_key IN ('super_admin', 'manager', 'delivery_manager')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission x
      WHERE x.role_id = r.id
        AND x.permission_id = p.id
  );
