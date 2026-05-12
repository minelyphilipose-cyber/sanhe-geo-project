SET @old_article_batch_unique_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'article_batch'
      AND index_name = 'uk_article_batch_project_date_no'
);

SET @drop_old_article_batch_unique_sql := IF(
    @old_article_batch_unique_exists > 0,
    'ALTER TABLE article_batch DROP INDEX uk_article_batch_project_date_no',
    'SELECT 1'
);
PREPARE drop_old_article_batch_unique_stmt FROM @drop_old_article_batch_unique_sql;
EXECUTE drop_old_article_batch_unique_stmt;
DEALLOCATE PREPARE drop_old_article_batch_unique_stmt;

SET @new_article_batch_unique_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'article_batch'
      AND index_name = 'uk_article_batch_project_date_channel_slot'
);

SET @add_article_batch_channel_slot_unique_sql := IF(
    @new_article_batch_unique_exists = 0,
    'ALTER TABLE article_batch ADD UNIQUE KEY uk_article_batch_project_date_channel_slot (project_id, batch_date, target_channel, generation_slot_no)',
    'SELECT 1'
);
PREPARE add_article_batch_channel_slot_unique_stmt FROM @add_article_batch_channel_slot_unique_sql;
EXECUTE add_article_batch_channel_slot_unique_stmt;
DEALLOCATE PREPARE add_article_batch_channel_slot_unique_stmt;
