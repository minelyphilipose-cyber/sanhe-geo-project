-- =========================================================================
-- V88: allow one comparison judge verdict per competitor under one prompt result
-- Rollback:
--   ALTER TABLE presale_ai_prompt_judge_result DROP KEY uk_prompt_result_competitor;
--   ALTER TABLE presale_ai_prompt_judge_result ADD UNIQUE KEY uk_prompt_result_id (prompt_result_id);
-- =========================================================================

ALTER TABLE presale_ai_prompt_judge_result
  DROP KEY uk_prompt_result_id,
  ADD UNIQUE KEY uk_prompt_result_competitor (prompt_result_id, competitor_name);
