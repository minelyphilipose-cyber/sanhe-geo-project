-- =========================================================================
-- V73: extend presale_ai_prompt_result with sentiment payload json fields
-- Rollback:
--   ALTER TABLE presale_ai_prompt_result
--     DROP COLUMN top_keywords_json,
--     DROP COLUMN negative_evidence_json;
-- =========================================================================

ALTER TABLE presale_ai_prompt_result
    ADD COLUMN top_keywords_json JSON NOT NULL DEFAULT ('[]') COMMENT '该 prompt 的情感关键词,LLM 输出,Assembler 聚合',
    ADD COLUMN negative_evidence_json JSON NOT NULL DEFAULT ('{}') COMMENT '该 prompt 的负面证据,LLM 输出单值对象';

