CREATE TABLE IF NOT EXISTS project_channel_allocation (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    channel_code VARCHAR(32) NOT NULL,
    period_type_snapshot VARCHAR(16) NOT NULL,
    package_quota_limit_snapshot INT NOT NULL DEFAULT 0,
    allocated_count INT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_project_channel_allocation (project_id, channel_code),
    KEY idx_company_channel (company_id, channel_code),
    KEY idx_company_revision (company_id, revision),
    CONSTRAINT fk_project_channel_allocation_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_project_channel_allocation_company FOREIGN KEY (company_id) REFERENCES company(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='project channel allocation';

CREATE TABLE IF NOT EXISTS project_channel_allocation_audit (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT NULL,
    operate_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    project_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    channel_code VARCHAR(32) NOT NULL,
    before_value INT NULL,
    after_value INT NULL,
    source_action VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_project_operate_at (project_id, operate_at),
    KEY idx_company_channel_operate_at (company_id, channel_code, operate_at),
    CONSTRAINT fk_project_channel_audit_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_project_channel_audit_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_project_channel_audit_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='project channel allocation audit';
