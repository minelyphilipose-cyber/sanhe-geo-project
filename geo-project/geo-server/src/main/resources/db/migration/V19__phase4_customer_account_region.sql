-- ============================================================
-- V19: direct customer account + region code/name fields
-- ============================================================

CREATE TABLE IF NOT EXISTS company_account (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id      BIGINT NOT NULL,
    current_balance BIGINT NOT NULL DEFAULT 0 COMMENT 'cent',
    total_recharge  BIGINT NOT NULL DEFAULT 0 COMMENT 'cent',
    total_deduction BIGINT NOT NULL DEFAULT 0 COMMENT 'cent',
    currency        VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status          VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|frozen|closed',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_company_account_company_id (company_id),
    KEY idx_company_account_status (status),
    CONSTRAINT fk_company_account_company FOREIGN KEY (company_id) REFERENCES company(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='direct customer virtual account';

CREATE TABLE IF NOT EXISTS company_account_txn (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id        BIGINT NOT NULL,
    account_id        BIGINT NOT NULL,
    txn_no            VARCHAR(64) NOT NULL,
    txn_type          VARCHAR(32) NOT NULL COMMENT 'recharge|deduction|manual_adjust',
    biz_type          VARCHAR(32) NOT NULL COMMENT 'company_prepaid|project_signing|finance_adjust',
    amount            BIGINT NOT NULL COMMENT 'deduction negative',
    balance_before    BIGINT NOT NULL,
    balance_after     BIGINT NOT NULL,
    related_project_id BIGINT NULL,
    operator_user_id  BIGINT NOT NULL,
    offline_reference VARCHAR(128) NULL,
    reason            VARCHAR(255) NOT NULL,
    remark            VARCHAR(500) NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_company_account_txn_no (txn_no),
    UNIQUE KEY uk_cat_project_signing (biz_type, related_project_id),
    KEY idx_cat_company_id (company_id),
    KEY idx_cat_account_id (account_id),
    KEY idx_cat_project_id (related_project_id),
    KEY idx_cat_created_at (created_at),
    CONSTRAINT fk_cat_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_cat_account FOREIGN KEY (account_id) REFERENCES company_account(id),
    CONSTRAINT fk_cat_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='direct customer account transactions';

INSERT INTO company_account (company_id, current_balance, total_recharge, total_deduction, currency, status)
SELECT c.id, 0, 0, 0, 'CNY', 'active'
FROM company c
LEFT JOIN company_account ca ON ca.company_id = c.id
WHERE ca.id IS NULL;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'company'
      AND column_name = 'province_code'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN province_code VARCHAR(16) NULL AFTER city',
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
      AND column_name = 'province_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN province_name VARCHAR(64) NULL AFTER province_code',
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
      AND column_name = 'city_code'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN city_code VARCHAR(16) NULL AFTER province_name',
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
      AND column_name = 'city_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN city_name VARCHAR(64) NULL AFTER city_code',
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
      AND column_name = 'district_code'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN district_code VARCHAR(16) NULL AFTER city_name',
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
      AND column_name = 'district_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN district_name VARCHAR(64) NULL AFTER district_code',
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
      AND column_name = 'province_code'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN province_code VARCHAR(16) NULL AFTER service_area',
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
      AND column_name = 'province_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN province_name VARCHAR(64) NULL AFTER province_code',
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
      AND column_name = 'city_code'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN city_code VARCHAR(16) NULL AFTER province_name',
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
      AND column_name = 'city_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN city_name VARCHAR(64) NULL AFTER city_code',
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
      AND column_name = 'district_code'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN district_code VARCHAR(16) NULL AFTER city_name',
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
      AND column_name = 'district_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN district_name VARCHAR(64) NULL AFTER district_code',
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
      AND column_name = 'province_code'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN province_code VARCHAR(16) NULL AFTER partner_id',
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
      AND column_name = 'province_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN province_name VARCHAR(64) NULL AFTER province_code',
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
      AND column_name = 'city_code'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN city_code VARCHAR(16) NULL AFTER province_name',
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
      AND column_name = 'city_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN city_name VARCHAR(64) NULL AFTER city_code',
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
      AND column_name = 'district_code'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN district_code VARCHAR(16) NULL AFTER city_name',
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
      AND column_name = 'district_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN district_name VARCHAR(64) NULL AFTER district_code',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;
