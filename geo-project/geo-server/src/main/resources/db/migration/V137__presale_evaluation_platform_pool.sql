ALTER TABLE ai_platform_config
  ADD COLUMN presale_evaluate_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether enabled as presale evaluation/judge model'
  AFTER enabled_for_presale;

ALTER TABLE presale_ai_prompt_judge_result
  ADD COLUMN judge_platform_code VARCHAR(40) DEFAULT NULL COMMENT 'actual judge platform code'
  AFTER judge_attempt_count;
