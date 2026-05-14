SET @add_keyword_group_id := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE batch_article_generation_task ADD COLUMN keyword_group_id BIGINT UNSIGNED NULL AFTER topic_as_question',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'batch_article_generation_task'
    AND column_name = 'keyword_group_id'
);
PREPARE add_keyword_group_id_stmt FROM @add_keyword_group_id;
EXECUTE add_keyword_group_id_stmt;
DEALLOCATE PREPARE add_keyword_group_id_stmt;

SET @add_keyword_group_name := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE batch_article_generation_task ADD COLUMN keyword_group_name VARCHAR(255) NULL AFTER keyword_group_id',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'batch_article_generation_task'
    AND column_name = 'keyword_group_name'
);
PREPARE add_keyword_group_name_stmt FROM @add_keyword_group_name;
EXECUTE add_keyword_group_name_stmt;
DEALLOCATE PREPARE add_keyword_group_name_stmt;
