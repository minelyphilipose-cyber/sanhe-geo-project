-- Layered GEO question workorder module.

CREATE TABLE IF NOT EXISTS geo_question_workorder (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    package_binding_id BIGINT NULL,
    package_name VARCHAR(128) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'draft',
    version_label VARCHAR(32) NULL,
    target_a INT NOT NULL DEFAULT 0,
    target_b INT NOT NULL DEFAULT 0,
    target_c INT NOT NULL DEFAULT 0,
    committed_version_id BIGINT NULL,
    legacy_keyword_group_id BIGINT NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_geo_qw_company_status (company_id, status),
    KEY idx_geo_qw_package_binding (package_binding_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO question pool workorder';

CREATE TABLE IF NOT EXISTS geo_question_profile_draft (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workorder_id BIGINT NOT NULL,
    profile_json MEDIUMTEXT NULL,
    sync_to_customer_profile TINYINT(1) NOT NULL DEFAULT 0,
    validation_status VARCHAR(24) NOT NULL DEFAULT 'draft',
    auto_saved_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_geo_qpd_workorder (workorder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO question workorder profile draft';

CREATE TABLE IF NOT EXISTS geo_question_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workorder_id BIGINT NOT NULL,
    batch_no VARCHAR(32) NOT NULL,
    request_a INT NOT NULL DEFAULT 0,
    request_b INT NOT NULL DEFAULT 0,
    request_c INT NOT NULL DEFAULT 0,
    actual_a INT NOT NULL DEFAULT 0,
    actual_b INT NOT NULL DEFAULT 0,
    actual_c INT NOT NULL DEFAULT 0,
    reserved_a INT NOT NULL DEFAULT 0,
    reserved_b INT NOT NULL DEFAULT 0,
    reserved_c INT NOT NULL DEFAULT 0,
    active_running_flag TINYINT NULL,
    model_provider VARCHAR(64) NULL,
    model_id VARCHAR(128) NULL,
    model_name VARCHAR(128) NULL,
    scene_weights_json TEXT NULL,
    temperature DECIMAL(4,2) NOT NULL DEFAULT 0.70,
    prompt_snapshot MEDIUMTEXT NULL,
    param_snapshot TEXT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'pending',
    progress_json TEXT NULL,
    error_message TEXT NULL,
    partial_flag TINYINT(1) NOT NULL DEFAULT 0,
    cancel_requested TINYINT(1) NOT NULL DEFAULT 0,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_geo_qb_batch_no (batch_no),
    UNIQUE KEY uk_geo_qb_one_running (workorder_id, active_running_flag),
    KEY idx_geo_qb_workorder_status (workorder_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO question generation batch';

CREATE TABLE IF NOT EXISTS geo_question_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workorder_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    question_text VARCHAR(1000) NOT NULL,
    scene_code VARCHAR(64) NOT NULL,
    tier VARCHAR(1) NOT NULL,
    priority VARCHAR(64) NULL,
    monitor_frequency VARCHAR(64) NULL,
    score_relevance DECIMAL(5,2) NULL,
    score_intent DECIMAL(5,2) NULL,
    score_competition DECIMAL(5,2) NULL,
    score_conversion DECIMAL(5,2) NULL,
    score_coverage DECIMAL(5,2) NULL,
    total_score DECIMAL(5,2) NULL,
    related_need_id BIGINT NULL,
    related_need_text VARCHAR(500) NULL,
    design_reason VARCHAR(1000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'pending_review',
    replace_count INT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_geo_qi_workorder_tier_status (workorder_id, tier, status),
    KEY idx_geo_qi_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO question item';

CREATE TABLE IF NOT EXISTS geo_question_replace_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    old_question_text VARCHAR(1000) NOT NULL,
    new_question_text VARCHAR(1000) NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_geo_qrh_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO question replacement history';

CREATE TABLE IF NOT EXISTS geo_question_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    workorder_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    version_label VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'active',
    count_a INT NOT NULL DEFAULT 0,
    count_b INT NOT NULL DEFAULT 0,
    count_c INT NOT NULL DEFAULT 0,
    is_partial TINYINT(1) NOT NULL DEFAULT 0,
    commit_mode VARCHAR(24) NOT NULL DEFAULT 'strict',
    snapshot_json MEDIUMTEXT NULL,
    legacy_keyword_group_id BIGINT NULL,
    committed_by BIGINT NULL,
    committed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_geo_qv_company (company_id, status),
    KEY idx_geo_qv_workorder (workorder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO question pool version';

CREATE TABLE IF NOT EXISTS geo_question_batch_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    event_code VARCHAR(64) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_geo_qbl_batch (batch_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GEO question generation key logs';

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'source_workorder_id'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN source_workorder_id BIGINT NULL COMMENT ''source GEO question workorder id''',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'source_batch_id'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN source_batch_id BIGINT NULL COMMENT ''source GEO question batch id''',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'source_question_id'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN source_question_id BIGINT NULL COMMENT ''source GEO question item id''',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'source_version_id'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN source_version_id BIGINT NULL COMMENT ''source GEO question version id''',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'scene_code'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN scene_code VARCHAR(64) NULL COMMENT ''GEO question scene code''',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'priority'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN priority VARCHAR(64) NULL COMMENT ''GEO question priority''',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'monitor_frequency'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN monitor_frequency VARCHAR(64) NULL COMMENT ''GEO question monitor frequency''',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'total_score'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN total_score DECIMAL(5,2) NULL COMMENT ''GEO question total score''',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'related_need'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN related_need VARCHAR(500) NULL COMMENT ''GEO question related need''',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'design_reason'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN design_reason VARCHAR(1000) NULL COMMENT ''GEO question design reason''',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;
