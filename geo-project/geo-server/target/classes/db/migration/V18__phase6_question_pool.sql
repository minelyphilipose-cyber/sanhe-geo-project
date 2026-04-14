-- ============================================================
-- V18: question pool with version history
-- ============================================================

CREATE TABLE IF NOT EXISTS question_pool_version (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id     BIGINT NOT NULL,
    version_no     INT NOT NULL,
    change_reason  VARCHAR(255) NULL,
    created_by     BIGINT NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_qpv_project_version (project_id, version_no),
    KEY idx_qpv_project_created_at (project_id, created_at),
    CONSTRAINT fk_qpv_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_qpv_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='question pool version header';

CREATE TABLE IF NOT EXISTS question_pool_item (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    version_id     BIGINT NOT NULL,
    project_id     BIGINT NOT NULL,
    question_text  VARCHAR(500) NOT NULL,
    question_type  VARCHAR(32) NOT NULL COMMENT 'brand|location|industry|decision|transaction|qa|comparison|competitor',
    priority       VARCHAR(4) NOT NULL COMMENT 'A|B|C',
    is_core        TINYINT(1) NOT NULL DEFAULT 0,
    sort_order     INT NOT NULL DEFAULT 100,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_qpi_project_version (project_id, version_id),
    KEY idx_qpi_type_priority (question_type, priority),
    CONSTRAINT fk_qpi_version FOREIGN KEY (version_id) REFERENCES question_pool_version(id) ON DELETE CASCADE,
    CONSTRAINT fk_qpi_project FOREIGN KEY (project_id) REFERENCES project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='question pool items by version';
