-- ============================================================
-- V22: company profile fields (contact/business/channel)
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'company'
      AND column_name = 'contact_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN contact_name VARCHAR(64) NULL AFTER company_name',
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
      AND column_name = 'contact_phone'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN contact_phone VARCHAR(32) NULL AFTER contact_name',
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
      AND column_name = 'service_area'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN service_area VARCHAR(255) NULL AFTER district_name',
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
      AND column_name = 'business_direction'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN business_direction VARCHAR(255) NULL AFTER industry',
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
      AND column_name = 'competitors'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN competitors VARCHAR(500) NULL AFTER business_direction',
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
      AND column_name = 'official_website'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN official_website VARCHAR(255) NULL AFTER competitors',
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
      AND column_name = 'official_account'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN official_account VARCHAR(128) NULL AFTER official_website',
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
      AND column_name = 'video_account'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN video_account VARCHAR(128) NULL AFTER official_account',
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
      AND column_name = 'douyin_account'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN douyin_account VARCHAR(128) NULL AFTER video_account',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;
