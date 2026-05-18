-- Split question pool polling batches/results/stats by A/B/C question tier.

ALTER TABLE poll_batches
    ADD COLUMN question_tier VARCHAR(1) NOT NULL DEFAULT 'A' COMMENT 'question tier: A/B/C' AFTER batch_no,
    DROP INDEX uk_poll_batch_identity,
    ADD UNIQUE KEY uk_poll_batch_identity (project_id, batch_date, batch_no, question_tier);

ALTER TABLE poll_results
    ADD COLUMN question_tier VARCHAR(1) NOT NULL DEFAULT 'A' COMMENT 'question tier: A/B/C' AFTER keyword_text_snapshot,
    DROP INDEX uk_poll_result_keyword_unique,
    ADD UNIQUE KEY uk_poll_result_keyword_unique (project_id, keyword_result_id, platform_id, batch_date, batch_no, question_tier),
    ADD KEY idx_poll_result_project_tier_date (project_id, question_tier, batch_date);

ALTER TABLE poll_daily_stats
    ADD COLUMN question_tier VARCHAR(1) NOT NULL DEFAULT 'A' COMMENT 'question tier: A/B/C' AFTER batch_no,
    DROP INDEX uk_poll_stats_unique,
    ADD UNIQUE KEY uk_poll_stats_unique (project_id, platform_id, batch_date, batch_no, question_tier);
