-- ============================================================
-- V282: partner presale report quota configuration and audit
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'partner' AND column_name = 'presale_report_free_quota_limit'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE partner ADD COLUMN presale_report_free_quota_limit INT NOT NULL DEFAULT 0 COMMENT ''free presale report quota limit'' AFTER discount_rate',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'partner' AND column_name = 'presale_report_extra_points'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE partner ADD COLUMN presale_report_extra_points DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT ''points per extra presale report'' AFTER presale_report_free_quota_limit',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'partner_account_txn' AND column_name = 'related_presale_report_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE partner_account_txn ADD COLUMN related_presale_report_id BIGINT NULL COMMENT ''related presale report id'' AFTER related_start_request_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'partner_account_txn' AND index_name = 'idx_pat_related_presale_report'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE partner_account_txn ADD KEY idx_pat_related_presale_report (related_presale_report_id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;
