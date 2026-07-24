-- Add frozen aliases/short names for baseline competitor sources.

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'baseline_competitor_source'
      AND column_name = 'aliases_json'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE baseline_competitor_source ADD COLUMN aliases_json JSON NULL COMMENT ''Frozen competitor aliases/short names'' AFTER competitor_name',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;
