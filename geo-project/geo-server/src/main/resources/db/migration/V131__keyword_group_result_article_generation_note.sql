SET @article_generation_note_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'keyword_group_result'
      AND column_name = 'article_generation_note'
);

SET @article_generation_note_sql := IF(
    @article_generation_note_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN article_generation_note VARCHAR(1000) NULL COMMENT ''生成文章备注，用于大模型生成文章 prompt 补充'' AFTER design_reason',
    'SELECT 1'
);

PREPARE article_generation_note_stmt FROM @article_generation_note_sql;
EXECUTE article_generation_note_stmt;
DEALLOCATE PREPARE article_generation_note_stmt;
