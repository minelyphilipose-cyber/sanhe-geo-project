CREATE TABLE IF NOT EXISTS package_channel_quota_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    package_plan_id BIGINT NOT NULL,
    channel_code VARCHAR(32) NOT NULL,
    period_type VARCHAR(16) NOT NULL,
    quota_limit INT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_package_channel_period (package_plan_id, channel_code, period_type),
    KEY idx_channel_period (channel_code, period_type),
    CONSTRAINT fk_pkg_channel_quota_plan FOREIGN KEY (package_plan_id) REFERENCES package_plan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='package channel quota config';

CREATE TABLE IF NOT EXISTS company_package_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    package_plan_id BIGINT NOT NULL,
    package_type VARCHAR(64) NOT NULL,
    package_name VARCHAR(128) NOT NULL,
    standard_price DECIMAL(12,2) NOT NULL,
    service_months INT NOT NULL,
    question_pool_limit INT NOT NULL,
    channel_quota_snapshot JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    active_flag TINYINT NULL,
    bound_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unbound_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_company_active_binding (company_id, active_flag),
    KEY idx_company_package (package_plan_id),
    CONSTRAINT fk_company_package_binding_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_company_package_binding_plan FOREIGN KEY (package_plan_id) REFERENCES package_plan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='company package binding';

CREATE TABLE IF NOT EXISTS company_channel_quota_usage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    channel_code VARCHAR(32) NOT NULL,
    period_type VARCHAR(16) NOT NULL,
    period_key VARCHAR(32) NOT NULL,
    quota_limit INT NOT NULL,
    used_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_company_channel_period (company_id, channel_code, period_type, period_key),
    KEY idx_company_period (company_id, period_type, period_key),
    CONSTRAINT fk_company_channel_usage_company FOREIGN KEY (company_id) REFERENCES company(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='company channel quota usage';

CREATE TABLE IF NOT EXISTS company_channel_quota_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    channel_code VARCHAR(32) NOT NULL,
    period_type VARCHAR(16) NOT NULL,
    period_key VARCHAR(32) NOT NULL,
    delta_count INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL,
    biz_type VARCHAR(32) NOT NULL,
    biz_id VARCHAR(64) NOT NULL,
    reserved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at DATETIME NULL,
    refunded_at DATETIME NULL,
    expire_checked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_quota_biz (biz_type, biz_id),
    KEY idx_reserved_timeout (status, reserved_at),
    KEY idx_company_channel (company_id, channel_code, period_type, period_key),
    CONSTRAINT fk_company_channel_ledger_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_company_channel_ledger_project FOREIGN KEY (project_id) REFERENCES project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='company channel quota ledger';

DROP TABLE IF EXISTS project_publish_quota;

ALTER TABLE package_plan
    DROP COLUMN platform_p0_count,
    DROP COLUMN platform_p1_count,
    DROP COLUMN platform_p2_count,
    DROP COLUMN per_question_platform_calls,
    DROP COLUMN per_question_calls_p0,
    DROP COLUMN per_question_calls_p1,
    DROP COLUMN per_question_calls_p2;

ALTER TABLE project
    DROP COLUMN package_type,
    DROP COLUMN package_price,
    DROP COLUMN service_months,
    DROP COLUMN plan_platform_p0_count,
    DROP COLUMN plan_platform_p1_count,
    DROP COLUMN plan_platform_p2_count,
    DROP COLUMN plan_per_question_platform_calls,
    DROP COLUMN plan_per_question_calls_p0,
    DROP COLUMN plan_per_question_calls_p1,
    DROP COLUMN plan_per_question_calls_p2;
