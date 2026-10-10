CREATE TABLE project_self_media_planning_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    target_month VARCHAR(7) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_payload MEDIUMTEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    result_payload MEDIUMTEXT NULL,
    failure_message VARCHAR(512) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    active_month VARCHAR(7) GENERATED ALWAYS AS (CASE WHEN status = 'pending' THEN target_month ELSE NULL END) STORED,
    UNIQUE KEY uk_self_media_planning_request (project_id, idempotency_key),
    UNIQUE KEY uk_self_media_planning_active (project_id, active_month),
    KEY idx_self_media_planning_queue (status, id),
    KEY idx_self_media_planning_history (project_id, target_month, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
