SET @llm_response_snapshot_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'geo_question_batch'
      AND column_name = 'llm_response_snapshot'
);

SET @add_llm_response_snapshot_sql = IF(
    @llm_response_snapshot_exists = 0,
    'ALTER TABLE geo_question_batch ADD COLUMN llm_response_snapshot MEDIUMTEXT NULL COMMENT ''raw LLM response snapshot'' AFTER prompt_snapshot',
    'SELECT 1'
);

PREPARE add_llm_response_snapshot_stmt FROM @add_llm_response_snapshot_sql;
EXECUTE add_llm_response_snapshot_stmt;
DEALLOCATE PREPARE add_llm_response_snapshot_stmt;
