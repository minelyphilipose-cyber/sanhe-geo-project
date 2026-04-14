-- ============================================================
-- V34: bi-daily question pool poll batches/results/stats
-- ============================================================

CREATE TABLE IF NOT EXISTS poll_batches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dispatch_task_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    batch_date DATE NOT NULL,
    batch_no INT NOT NULL DEFAULT 1,
    triggered_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    total_question_count INT NOT NULL DEFAULT 0,
    total_platform_count INT NOT NULL DEFAULT 0,
    question_count INT NOT NULL DEFAULT 0,
    completed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    hit_count INT NOT NULL DEFAULT 0,
    overall_hit_rate DECIMAL(8,4) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_poll_batch_identity (project_id, batch_date, batch_no),
    KEY idx_poll_batch_task (dispatch_task_id),
    KEY idx_poll_batch_project_date (project_id, batch_date),
    CONSTRAINT fk_poll_batch_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_batch_dispatch_task FOREIGN KEY (dispatch_task_id) REFERENCES dispatch_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='question pool bi-daily poll batches';

CREATE TABLE IF NOT EXISTS poll_results (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    dispatch_task_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    platform_id BIGINT NOT NULL,
    platform_code VARCHAR(64) NOT NULL,
    batch_date DATE NOT NULL,
    batch_no INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL COMMENT 'completed|failed',
    request_count INT NOT NULL DEFAULT 0,
    response_time_ms BIGINT NULL,
    is_hit TINYINT(1) NOT NULL DEFAULT 0,
    match_type VARCHAR(32) NULL COMMENT 'exact|partial|alias',
    site_mentioned TINYINT(1) NOT NULL DEFAULT 0,
    contact_mentioned TINYINT(1) NOT NULL DEFAULT 0,
    record_type VARCHAR(16) NOT NULL COMMENT 'hit|miss|error',
    detail_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_poll_result_unique (project_id, question_id, platform_id, batch_date, batch_no),
    KEY idx_poll_result_batch (batch_id),
    KEY idx_poll_result_task (dispatch_task_id),
    KEY idx_poll_result_platform (platform_id, batch_date),
    CONSTRAINT fk_poll_result_batch FOREIGN KEY (batch_id) REFERENCES poll_batches(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_result_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_result_question FOREIGN KEY (question_id) REFERENCES question_pool_item(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_result_platform FOREIGN KEY (platform_id) REFERENCES ai_platform_config(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_result_dispatch_task FOREIGN KEY (dispatch_task_id) REFERENCES dispatch_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='question x platform poll result details';

CREATE TABLE IF NOT EXISTS poll_daily_stats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    dispatch_task_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    platform_id BIGINT NOT NULL,
    platform_code VARCHAR(64) NOT NULL,
    platform_name VARCHAR(128) NOT NULL,
    batch_date DATE NOT NULL,
    batch_no INT NOT NULL DEFAULT 1,
    question_count INT NOT NULL DEFAULT 0,
    request_count INT NOT NULL DEFAULT 0,
    completed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    hit_count INT NOT NULL DEFAULT 0,
    site_mention_count INT NOT NULL DEFAULT 0,
    contact_mention_count INT NOT NULL DEFAULT 0,
    hit_rate DECIMAL(8,4) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_poll_stats_unique (project_id, platform_id, batch_date, batch_no),
    KEY idx_poll_stats_batch (batch_id),
    CONSTRAINT fk_poll_stats_batch FOREIGN KEY (batch_id) REFERENCES poll_batches(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_stats_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_stats_platform FOREIGN KEY (platform_id) REFERENCES ai_platform_config(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_stats_dispatch_task FOREIGN KEY (dispatch_task_id) REFERENCES dispatch_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='aggregated stats by project x platform x batch';

CREATE TABLE IF NOT EXISTS project_poll_rotation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    priority_level VARCHAR(4) NOT NULL COMMENT 'A|B|C',
    rotation_offset INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_poll_rotation (project_id, priority_level),
    CONSTRAINT fk_project_poll_rotation_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='rotation cursor for layered question selection';
