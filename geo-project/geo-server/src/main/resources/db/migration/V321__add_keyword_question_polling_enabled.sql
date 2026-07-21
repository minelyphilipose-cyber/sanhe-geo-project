ALTER TABLE keyword_group_result
    ADD COLUMN polling_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether the A-tier question participates in monitoring polls' AFTER monitor_frequency;

CREATE INDEX idx_kgr_group_tier_poll_sort_id
    ON keyword_group_result (group_id, question_tier, polling_enabled, sort_order, id);
