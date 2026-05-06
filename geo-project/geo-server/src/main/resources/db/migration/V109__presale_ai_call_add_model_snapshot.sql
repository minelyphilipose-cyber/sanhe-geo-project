-- =========================================================================
-- V100: add platform/model snapshot columns to presale_ai_call
-- Rollback:
--   ALTER TABLE presale_ai_call
--     DROP COLUMN platform_code_snapshot,
--     DROP COLUMN platform_name_snapshot,
--     DROP COLUMN model_id_snapshot,
--     DROP COLUMN model_name_snapshot;
-- =========================================================================

ALTER TABLE presale_ai_call
    ADD COLUMN platform_code_snapshot VARCHAR(64) NULL COMMENT '调用时平台编码快照' AFTER platform_code,
    ADD COLUMN platform_name_snapshot VARCHAR(128) NULL COMMENT '调用时平台名称快照' AFTER platform_code_snapshot,
    ADD COLUMN model_id_snapshot VARCHAR(128) NULL COMMENT '调用时实际模型 ID 快照' AFTER platform_name_snapshot,
    ADD COLUMN model_name_snapshot VARCHAR(128) NULL COMMENT '调用时模型展示名快照' AFTER model_id_snapshot;
