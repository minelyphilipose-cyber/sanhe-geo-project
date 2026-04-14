-- ============================================================
-- V29: dispatch task table
-- ============================================================

CREATE TABLE IF NOT EXISTS dispatch_task (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_no             VARCHAR(64) NOT NULL COMMENT 'task unique number',
    project_id          BIGINT NOT NULL,
    task_type           VARCHAR(64) NOT NULL,
    priority_level      TINYINT NOT NULL COMMENT 'P0=0,P1=1,P2=2,P3=3',
    status              VARCHAR(32) NOT NULL DEFAULT 'pending',
    window_start        DATE NOT NULL,
    window_end          DATE NOT NULL,
    due_time            DATETIME NOT NULL,
    payload_json        JSON NULL,
    retry_count         INT NOT NULL DEFAULT 0,
    max_retry           INT NOT NULL DEFAULT 3,
    first_started_at    DATETIME NULL,
    last_started_at     DATETIME NULL,
    last_error          VARCHAR(1000) NULL,
    next_retry_at       DATETIME NULL,
    timeout_at          DATETIME NULL,
    finished_at         DATETIME NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dispatch_task_no (task_no),
    UNIQUE KEY uk_dispatch_task_idempotent (project_id, task_type, window_start, window_end),
    KEY idx_dispatch_task_status_due (status, due_time),
    KEY idx_dispatch_task_retry (status, next_retry_at),
    KEY idx_dispatch_task_priority (priority_level, created_at),
    KEY idx_dispatch_task_project_type (project_id, task_type),
    CONSTRAINT fk_dispatch_task_project FOREIGN KEY (project_id) REFERENCES project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='dispatch task queue backup';
