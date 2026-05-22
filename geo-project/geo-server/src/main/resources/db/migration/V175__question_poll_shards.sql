-- V175: shard question-poll batches so large daily polls can retry and recover by platform/question slice.

ALTER TABLE dispatch_task
    ADD COLUMN resource_wait_count INT NOT NULL DEFAULT 0 COMMENT 'capacity/resource wait count independent from retry_count' AFTER retry_count;

ALTER TABLE poll_batches
    MODIFY COLUMN dispatch_task_id BIGINT NULL,
    ADD COLUMN status VARCHAR(24) NOT NULL DEFAULT 'ready' COMMENT 'planning|ready|finished|failed' AFTER finished_at,
    ADD COLUMN total_shard_count INT NOT NULL DEFAULT 0 COMMENT 'planned shard count for this batch' AFTER total_platform_count,
    ADD COLUMN completed_shard_count INT NOT NULL DEFAULT 0 COMMENT 'terminal completed shard count' AFTER total_shard_count,
    ADD COLUMN planning_started_at DATETIME NULL COMMENT 'planning started time' AFTER triggered_at,
    ADD COLUMN ready_at DATETIME NULL COMMENT 'all shards planned time' AFTER planning_started_at;

ALTER TABLE poll_daily_stats
    MODIFY COLUMN dispatch_task_id BIGINT NULL;

CREATE TABLE IF NOT EXISTS poll_batch_shards (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    dispatch_task_id BIGINT NULL,
    project_id BIGINT NOT NULL,
    platform_id BIGINT NOT NULL,
    platform_code VARCHAR(64) NOT NULL,
    platform_name VARCHAR(128) NOT NULL,
    batch_date DATE NOT NULL,
    batch_no INT NOT NULL DEFAULT 1,
    question_tier VARCHAR(1) NOT NULL DEFAULT 'A',
    shard_no INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ready' COMMENT 'ready|running|completed|failed',
    expected_count INT NOT NULL DEFAULT 0,
    completed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    resource_wait_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(900) NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_poll_shard_identity (batch_id, platform_id, shard_no),
    KEY idx_poll_shard_task (dispatch_task_id),
    KEY idx_poll_shard_batch_status (batch_id, status),
    KEY idx_poll_shard_project_date (project_id, question_tier, batch_date),
    CONSTRAINT fk_poll_shard_batch FOREIGN KEY (batch_id) REFERENCES poll_batches(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_shard_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_shard_platform FOREIGN KEY (platform_id) REFERENCES ai_platform_config(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_shard_task FOREIGN KEY (dispatch_task_id) REFERENCES dispatch_task(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='question poll shard snapshot';

CREATE TABLE IF NOT EXISTS poll_batch_shard_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    shard_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    keyword_result_id BIGINT NOT NULL,
    keyword_text_snapshot TEXT NOT NULL,
    sort_order INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'pending' COMMENT 'pending|completed|failed',
    poll_result_id BIGINT NULL,
    last_error VARCHAR(900) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_poll_shard_item_keyword (shard_id, keyword_result_id),
    KEY idx_poll_shard_item_batch (batch_id),
    KEY idx_poll_shard_item_status (shard_id, status),
    KEY idx_poll_shard_item_keyword_result (keyword_result_id),
    CONSTRAINT fk_poll_shard_item_shard FOREIGN KEY (shard_id) REFERENCES poll_batch_shards(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_shard_item_batch FOREIGN KEY (batch_id) REFERENCES poll_batches(id) ON DELETE CASCADE,
    CONSTRAINT fk_poll_shard_item_keyword FOREIGN KEY (keyword_result_id) REFERENCES keyword_group_result(id) ON DELETE RESTRICT,
    CONSTRAINT fk_poll_shard_item_result FOREIGN KEY (poll_result_id) REFERENCES poll_results(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='question poll shard item snapshot';
