-- ============================================================
-- V276: partner collaboration phase 1 request, quota and presale schema
-- ============================================================

CREATE TABLE IF NOT EXISTS project_start_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    applicant_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'submitted|approved|rejected|cancelled',
    active_submitted_project_id BIGINT
        GENERATED ALWAYS AS (IF(status = 'submitted', project_id, NULL)) STORED,
    request_no VARCHAR(64) NOT NULL,
    submitted_at DATETIME NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    reject_reason_code VARCHAR(64) NULL,
    reject_reason_text VARCHAR(500) NULL,
    assigned_internal_owner_id BIGINT NULL,
    points_required_snapshot DECIMAL(18,2) NULL,
    discount_rate_snapshot DECIMAL(10,4) NULL,
    package_snapshot_json JSON NULL,
    partner_allocated_quota_json JSON NULL,
    internal_delivery_snapshot_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_psr_request_no (request_no),
    UNIQUE KEY uk_psr_active_submitted_project (active_submitted_project_id),
    KEY idx_psr_project_status (project_id, status, id),
    KEY idx_psr_partner_status (partner_id, status, submitted_at),
    KEY idx_psr_company_status (company_id, status, submitted_at),
    KEY idx_psr_applicant (applicant_user_id),
    KEY idx_psr_reviewer (reviewed_by),
    KEY idx_psr_internal_owner (assigned_internal_owner_id),
    CONSTRAINT fk_psr_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_psr_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_psr_partner FOREIGN KEY (partner_id) REFERENCES partner(id),
    CONSTRAINT fk_psr_applicant FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_psr_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user(id),
    CONSTRAINT fk_psr_internal_owner FOREIGN KEY (assigned_internal_owner_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='partner project start request';

CREATE TABLE IF NOT EXISTS project_quota_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    start_request_id BIGINT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'draft|submitted|locked|released',
    partner_allocated_quota_json JSON NOT NULL,
    internal_delivery_snapshot_json JSON NULL,
    locked_at DATETIME NULL,
    released_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_pqs_project_status (project_id, status),
    KEY idx_pqs_company_status (company_id, status),
    KEY idx_pqs_start_request (start_request_id),
    KEY idx_pqs_locked_at (locked_at),
    CONSTRAINT fk_pqs_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_pqs_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_pqs_start_request FOREIGN KEY (start_request_id) REFERENCES project_start_request(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='partner project quota snapshot';

CREATE TABLE IF NOT EXISTS partner_presale_report_quota_txn (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    partner_id BIGINT NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    request_payload_snapshot_json JSON NOT NULL,
    report_id BIGINT NULL,
    biz_type VARCHAR(32) NOT NULL COMMENT 'free_quota|points',
    points_amount DECIMAL(18,2) NULL,
    quota_amount INT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'reserved|confirmed|refunded|manual_review',
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(512) NULL,
    related_points_txn_id BIGINT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    confirmed_at DATETIME NULL,
    refunded_at DATETIME NULL,
    UNIQUE KEY uk_partner_presale_request (partner_id, request_id),
    KEY idx_pprqt_partner_status (partner_id, status, created_at),
    KEY idx_pprqt_report (report_id),
    KEY idx_pprqt_related_points_txn (related_points_txn_id),
    KEY idx_pprqt_created_by (created_by),
    CONSTRAINT fk_pprqt_partner FOREIGN KEY (partner_id) REFERENCES partner(id),
    CONSTRAINT fk_pprqt_report FOREIGN KEY (report_id) REFERENCES presale_report(id),
    CONSTRAINT fk_pprqt_related_points_txn FOREIGN KEY (related_points_txn_id) REFERENCES partner_account_txn(id),
    CONSTRAINT fk_pprqt_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='partner presale report quota transaction';

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'package_plan' AND column_name = 'audience_type'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN audience_type VARCHAR(16) NOT NULL DEFAULT ''internal'' COMMENT ''internal|partner'' AFTER package_name',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'package_plan' AND column_name = 'package_status'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN package_status VARCHAR(16) NOT NULL DEFAULT ''active'' COMMENT ''draft|active|inactive'' AFTER audience_type',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'package_plan' AND column_name = 'partner_points'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN partner_points DECIMAL(18,2) NULL COMMENT ''partner package points'' AFTER standard_price',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'package_plan' AND column_name = 'partner_visible_config_json'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN partner_visible_config_json JSON NULL COMMENT ''partner visible package config'' AFTER partner_points',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'package_plan' AND column_name = 'internal_delivery_config_json'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN internal_delivery_config_json JSON NULL COMMENT ''internal delivery package config'' AFTER partner_visible_config_json',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'package_plan' AND column_name = 'deleted_at'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN deleted_at DATETIME NULL COMMENT ''soft deleted at'' AFTER remark',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'package_plan' AND column_name = 'deleted_by'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN deleted_by BIGINT NULL COMMENT ''soft deleted by'' AFTER deleted_at',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

UPDATE package_plan
SET package_status = CASE WHEN enabled = 1 THEN 'active' ELSE 'inactive' END
WHERE package_status IS NULL OR package_status = '';

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'package_plan' AND index_name = 'idx_package_plan_audience_status'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE package_plan ADD KEY idx_package_plan_audience_status (audience_type, package_status, deleted_at)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_package_binding' AND column_name = 'package_snapshot_json'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company_package_binding ADD COLUMN package_snapshot_json JSON NULL COMMENT ''package snapshot at binding or lock'' AFTER channel_quota_snapshot',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_package_binding' AND column_name = 'partner_visible_snapshot_json'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company_package_binding ADD COLUMN partner_visible_snapshot_json JSON NULL COMMENT ''partner visible package snapshot'' AFTER package_snapshot_json',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_package_binding' AND column_name = 'internal_delivery_snapshot_json'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company_package_binding ADD COLUMN internal_delivery_snapshot_json JSON NULL COMMENT ''internal delivery package snapshot'' AFTER partner_visible_snapshot_json',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_package_binding' AND column_name = 'locked_at'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company_package_binding ADD COLUMN locked_at DATETIME NULL COMMENT ''package binding locked at'' AFTER internal_delivery_snapshot_json',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_package_binding' AND column_name = 'locked_by_project_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company_package_binding ADD COLUMN locked_by_project_id BIGINT NULL COMMENT ''project that locked binding'' AFTER locked_at',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_package_binding' AND column_name = 'locked_by_approval_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company_package_binding ADD COLUMN locked_by_approval_id BIGINT NULL COMMENT ''start request that locked binding'' AFTER locked_by_project_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'company_package_binding' AND index_name = 'idx_cpb_locked_project'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE company_package_binding ADD KEY idx_cpb_locked_project (locked_by_project_id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'company_package_binding' AND index_name = 'idx_cpb_locked_approval'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE company_package_binding ADD KEY idx_cpb_locked_approval (locked_by_approval_id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @fk_exists := (
    SELECT COUNT(1) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'company_package_binding'
      AND constraint_name = 'fk_cpb_locked_project'
      AND constraint_type = 'FOREIGN KEY'
);
SET @ddl_sql := IF(
    @fk_exists = 0,
    'ALTER TABLE company_package_binding ADD CONSTRAINT fk_cpb_locked_project FOREIGN KEY (locked_by_project_id) REFERENCES project(id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @fk_exists := (
    SELECT COUNT(1) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'company_package_binding'
      AND constraint_name = 'fk_cpb_locked_approval'
      AND constraint_type = 'FOREIGN KEY'
);
SET @ddl_sql := IF(
    @fk_exists = 0,
    'ALTER TABLE company_package_binding ADD CONSTRAINT fk_cpb_locked_approval FOREIGN KEY (locked_by_approval_id) REFERENCES project_start_request(id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company' AND column_name = 'partner_staff_owner_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE company ADD COLUMN partner_staff_owner_id BIGINT NULL COMMENT ''partner staff owner user id'' AFTER sales_owner_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'company' AND index_name = 'idx_company_partner_staff_owner'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE company ADD KEY idx_company_partner_staff_owner (partner_staff_owner_id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @fk_exists := (
    SELECT COUNT(1) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'company'
      AND constraint_name = 'fk_company_partner_staff_owner'
      AND constraint_type = 'FOREIGN KEY'
);
SET @ddl_sql := IF(
    @fk_exists = 0,
    'ALTER TABLE company ADD CONSTRAINT fk_company_partner_staff_owner FOREIGN KEY (partner_staff_owner_id) REFERENCES sys_user(id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'presale_report' AND column_name = 'partner_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE presale_report ADD COLUMN partner_id BIGINT NULL COMMENT ''partner owner id'' AFTER assigned_to',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'presale_report' AND column_name = 'partner_presale_charge_type'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE presale_report ADD COLUMN partner_presale_charge_type VARCHAR(32) NULL COMMENT ''free_quota|points'' AFTER partner_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'presale_report' AND column_name = 'partner_presale_points'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE presale_report ADD COLUMN partner_presale_points DECIMAL(18,2) NULL COMMENT ''partner presale report points charged'' AFTER partner_presale_charge_type',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'presale_report' AND column_name = 'partner_presale_quota_txn_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE presale_report ADD COLUMN partner_presale_quota_txn_id BIGINT NULL COMMENT ''partner presale quota txn id'' AFTER partner_presale_points',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'presale_report' AND column_name = 'partner_presale_points_txn_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE presale_report ADD COLUMN partner_presale_points_txn_id BIGINT NULL COMMENT ''partner points txn id'' AFTER partner_presale_quota_txn_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'presale_report' AND column_name = 'request_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE presale_report ADD COLUMN request_id VARCHAR(128) NULL COMMENT ''partner idempotency request id'' AFTER partner_presale_points_txn_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'presale_report' AND column_name = 'request_hash'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE presale_report ADD COLUMN request_hash VARCHAR(128) NULL COMMENT ''normalized request hash'' AFTER request_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'presale_report' AND column_name = 'request_payload_snapshot_json'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE presale_report ADD COLUMN request_payload_snapshot_json JSON NULL COMMENT ''partner request payload snapshot'' AFTER request_hash',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'presale_report' AND index_name = 'idx_presale_report_partner_created_at'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE presale_report ADD KEY idx_presale_report_partner_created_at (partner_id, created_at)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'presale_report' AND index_name = 'uk_presale_report_partner_request'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE presale_report ADD UNIQUE KEY uk_presale_report_partner_request (partner_id, request_id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;
