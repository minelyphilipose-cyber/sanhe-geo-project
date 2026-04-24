-- =========================================================================
-- V75: add rendered request prompt to presale_ai_prompt_result
-- Rollback:
--   ALTER TABLE presale_ai_prompt_result
--     DROP COLUMN request_prompt_content;
-- =========================================================================

ALTER TABLE presale_ai_prompt_result
    ADD COLUMN request_prompt_content TEXT NULL COMMENT '实际请求大模型的 prompt 内容(变量渲染后)'
    AFTER analyze_call_id;
