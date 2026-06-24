-- Keep question-poll shard history when a keyword group/result is retired.
-- The shard item already stores keyword_text_snapshot, so the mutable keyword_result_id
-- should not prevent deleting or soft-retiring keyword-group data.

SET @fk_exists := (
    SELECT COUNT(1)
      FROM information_schema.referential_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'poll_batch_shard_items'
       AND constraint_name = 'fk_poll_shard_item_keyword'
);
SET @ddl_sql := IF(@fk_exists = 0,
    'SELECT 1',
    'ALTER TABLE poll_batch_shard_items DROP FOREIGN KEY fk_poll_shard_item_keyword'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

ALTER TABLE poll_batch_shard_items
    MODIFY COLUMN keyword_result_id BIGINT NULL;

SET @fk_exists := (
    SELECT COUNT(1)
      FROM information_schema.referential_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'poll_batch_shard_items'
       AND constraint_name = 'fk_poll_shard_item_keyword'
);
SET @ddl_sql := IF(@fk_exists > 0,
    'SELECT 1',
    'ALTER TABLE poll_batch_shard_items ADD CONSTRAINT fk_poll_shard_item_keyword FOREIGN KEY (keyword_result_id) REFERENCES keyword_group_result(id) ON DELETE SET NULL'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;
