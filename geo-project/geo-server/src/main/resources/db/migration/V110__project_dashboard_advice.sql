CREATE TABLE IF NOT EXISTS project_dashboard_advice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    summary TEXT NULL,
    highlights JSON NULL,
    improvement_directions JSON NULL,
    next_actions JSON NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    published_at DATETIME NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_dashboard_advice_project_status (project_id, status),
    KEY idx_project_dashboard_advice_status (project_id, status),
    CONSTRAINT fk_project_dashboard_advice_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='project dashboard service observations and next actions';
