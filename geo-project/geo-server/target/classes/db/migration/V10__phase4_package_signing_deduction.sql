-- ============================================================
-- V10: phase4 package plan and signing deduction
-- ============================================================

CREATE TABLE IF NOT EXISTS package_plan (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    package_type    VARCHAR(32) NOT NULL,
    package_name    VARCHAR(64) NOT NULL,
    standard_price  BIGINT NOT NULL COMMENT 'cent',
    service_months  INT NOT NULL,
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    sort_order      INT NOT NULL DEFAULT 100,
    remark          VARCHAR(500) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_package_plan_type (package_type),
    KEY idx_package_plan_enabled (enabled),
    KEY idx_package_plan_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='package plan';

INSERT INTO package_plan (package_type, package_name, standard_price, service_months, enabled, sort_order, remark)
SELECT 'trial_6980', 'GEO Trial 6980', 698000, 3, 1, 10, 'default package'
WHERE NOT EXISTS (SELECT 1 FROM package_plan WHERE package_type = 'trial_6980');

INSERT INTO package_plan (package_type, package_name, standard_price, service_months, enabled, sort_order, remark)
SELECT 'standard_12800', 'GEO Standard 12800', 1280000, 12, 1, 20, 'default package'
WHERE NOT EXISTS (SELECT 1 FROM package_plan WHERE package_type = 'standard_12800');

INSERT INTO package_plan (package_type, package_name, standard_price, service_months, enabled, sort_order, remark)
SELECT 'growth_26800', 'GEO Growth 26800', 2680000, 12, 1, 30, 'default package'
WHERE NOT EXISTS (SELECT 1 FROM package_plan WHERE package_type = 'growth_26800');

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'discount_rate_snapshot'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN discount_rate_snapshot DECIMAL(5,4) NULL COMMENT ''partner discount at signing'' AFTER partner_id',
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
      AND column_name = 'deduction_amount'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN deduction_amount BIGINT NULL COMMENT ''deduction amount in cent'' AFTER discount_rate_snapshot',
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
      AND column_name = 'deduction_txn_no'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN deduction_txn_no VARCHAR(64) NULL COMMENT ''deduction transaction no'' AFTER deduction_amount',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'partner_account_txn'
      AND index_name = 'uk_pat_project_signing'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE partner_account_txn ADD UNIQUE KEY uk_pat_project_signing (biz_type, related_project_id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;
