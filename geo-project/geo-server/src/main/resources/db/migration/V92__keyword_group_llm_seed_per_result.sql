-- Adjust LLM seed storage for multi-seed accumulation.

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'keyword_group'
      AND column_name = 'seed_text'
);
SET @ddl_sql := IF(@col_exists = 1,
    'ALTER TABLE keyword_group DROP COLUMN seed_text',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'keyword_group_result'
      AND column_name = 'seed_text'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN seed_text VARCHAR(30) NULL COMMENT ''LLM seed for this row, NULL for cartesian'' AFTER source_type',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;
