-- ============================================================
-- V57: switch bi-daily poll source from question pool to keyword results
-- ============================================================

ALTER TABLE poll_results
    MODIFY COLUMN question_id BIGINT NULL,
    ADD COLUMN keyword_result_id BIGINT NULL AFTER question_id,
    ADD COLUMN keyword_text_snapshot VARCHAR(500) NULL COMMENT 'polled keyword text snapshot' AFTER keyword_result_id;

ALTER TABLE poll_results
    DROP INDEX uk_poll_result_unique,
    ADD UNIQUE KEY uk_poll_result_question_unique (project_id, question_id, platform_id, batch_date, batch_no),
    ADD UNIQUE KEY uk_poll_result_keyword_unique (project_id, keyword_result_id, platform_id, batch_date, batch_no),
    ADD KEY idx_poll_result_project_keyword (project_id, keyword_result_id, batch_date);
