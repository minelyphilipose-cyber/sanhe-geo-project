-- =========================================================================
-- V89: add concrete request prompt content to presale_ai_call
-- Rollback:
--   ALTER TABLE presale_ai_call
--     DROP COLUMN request_prompt_content;
-- =========================================================================

ALTER TABLE presale_ai_call
    ADD COLUMN request_prompt_content LONGTEXT NULL COMMENT '实际请求大模型的 prompt 内容'
    AFTER parent_call_id;
