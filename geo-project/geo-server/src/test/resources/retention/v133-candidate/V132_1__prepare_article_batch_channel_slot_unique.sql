SET @article_batch_channel_slot_index_signature := (
    SELECT CONCAT(
        MIN(non_unique),
        ':',
        GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'article_batch'
      AND index_name = 'uk_article_batch_project_date_channel_slot'
);

SET @prepare_article_batch_channel_slot_unique_sql := CASE
    WHEN @article_batch_channel_slot_index_signature IS NULL THEN
        'ALTER TABLE article_batch ADD UNIQUE KEY uk_article_batch_project_date_channel_slot (project_id, batch_date, target_channel, generation_slot_no)'
    WHEN @article_batch_channel_slot_index_signature =
         '0:project_id,batch_date,target_channel,generation_slot_no' THEN
        'SELECT 1'
    ELSE
        'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V132.1 found an incompatible uk_article_batch_project_date_channel_slot index'''
END;

PREPARE prepare_article_batch_channel_slot_unique_stmt
    FROM @prepare_article_batch_channel_slot_unique_sql;
EXECUTE prepare_article_batch_channel_slot_unique_stmt;
DEALLOCATE PREPARE prepare_article_batch_channel_slot_unique_stmt;
