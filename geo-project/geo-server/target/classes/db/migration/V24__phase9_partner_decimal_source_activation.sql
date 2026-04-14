-- ============================================================
-- V24: partner core adjustments
-- 1) money fields migrate to DECIMAL(18,2)
-- 2) company source_type / partner_name
-- 3) partner role gets company/project write permissions
-- ============================================================

ALTER TABLE partner_account
    MODIFY COLUMN current_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN total_recharge DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN total_deduction DECIMAL(18,2) NOT NULL DEFAULT 0.00;

ALTER TABLE partner_account_txn
    MODIFY COLUMN amount DECIMAL(18,2) NOT NULL,
    MODIFY COLUMN balance_before DECIMAL(18,2) NOT NULL,
    MODIFY COLUMN balance_after DECIMAL(18,2) NOT NULL;

ALTER TABLE company_account
    MODIFY COLUMN current_balance DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN total_recharge DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN total_deduction DECIMAL(18,2) NOT NULL DEFAULT 0.00;

ALTER TABLE company_account_txn
    MODIFY COLUMN amount DECIMAL(18,2) NOT NULL,
    MODIFY COLUMN balance_before DECIMAL(18,2) NOT NULL,
    MODIFY COLUMN balance_after DECIMAL(18,2) NOT NULL;

ALTER TABLE package_plan
    MODIFY COLUMN standard_price DECIMAL(18,2) NOT NULL;

ALTER TABLE project
    MODIFY COLUMN package_price DECIMAL(18,2) NOT NULL,
    MODIFY COLUMN deduction_amount DECIMAL(18,2) NULL;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'company'
      AND column_name = 'source_type'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN source_type VARCHAR(16) NULL COMMENT ''internal|partner'' AFTER owner_type',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'company'
      AND column_name = 'partner_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN partner_name VARCHAR(128) NULL AFTER partner_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

UPDATE company
SET source_type = CASE WHEN partner_id IS NULL THEN 'internal' ELSE 'partner' END
WHERE source_type IS NULL OR source_type = '';

UPDATE company c
LEFT JOIN partner p ON p.id = c.partner_id
SET c.partner_name = p.partner_name
WHERE c.partner_id IS NOT NULL
  AND (c.partner_name IS NULL OR c.partner_name = '');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'company_source_type', 'internal', '内部新增', 10
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'company_source_type' AND dict_key = 'internal'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'company_source_type', 'partner', '合伙人新增', 20
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'company_source_type' AND dict_key = 'partner'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('company.write', 'project.write')
WHERE r.role_key = 'partner'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
