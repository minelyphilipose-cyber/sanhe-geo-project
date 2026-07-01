-- ============================================================
-- V281: partner phase 1 start request points transaction audit fields
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'partner_account_txn'
      AND column_name = 'related_company_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE partner_account_txn ADD COLUMN related_company_id BIGINT NULL COMMENT ''related company id'' AFTER related_project_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'partner_account_txn'
      AND column_name = 'related_start_request_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE partner_account_txn ADD COLUMN related_start_request_id BIGINT NULL COMMENT ''related project start request id'' AFTER related_company_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'partner_account_txn'
      AND column_name = 'package_snapshot_json'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE partner_account_txn ADD COLUMN package_snapshot_json JSON NULL COMMENT ''package snapshot used by this transaction'' AFTER related_start_request_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'partner_account_txn'
      AND index_name = 'idx_pat_related_company'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE partner_account_txn ADD KEY idx_pat_related_company (related_company_id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'partner_account_txn'
      AND index_name = 'idx_pat_start_request'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE partner_account_txn ADD KEY idx_pat_start_request (related_start_request_id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;
