-- ============================================================
-- V91: keyword group LLM questions
-- ============================================================

-- 1) keyword_group: add seed_text on group level.
SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'keyword_group'
      AND column_name = 'seed_text'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group ADD COLUMN seed_text VARCHAR(300) NULL COMMENT ''LLM question seed text'' AFTER function_industry_tag',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

-- 2) keyword_group_result: add result source.
SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'keyword_group_result'
      AND column_name = 'source_type'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT ''cartesian'' COMMENT ''cartesian/llm'' AFTER keyword_text',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'keyword_group_result'
      AND index_name = 'idx_group_source_sort'
);
SET @ddl_sql := IF(@idx_exists = 0,
    'ALTER TABLE keyword_group_result ADD KEY idx_group_source_sort (group_id, source_type, sort_order, id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

-- 3) keyword_group currently has no deleted field in existing environments.
-- Add it defensively before the unique key requested by V91.
SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'keyword_group'
      AND column_name = 'deleted'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''soft delete marker'' AFTER updated_at',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

-- 4) Rename duplicate active names before adding unique key.
UPDATE keyword_group
SET name = CONCAT(name, '_', id)
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY company_id, name ORDER BY id) AS rn
        FROM keyword_group
        WHERE deleted = 0
    ) t
    WHERE rn > 1
);

-- 5) Enforce no duplicate active group name under the same company.
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'keyword_group'
      AND index_name = 'uk_company_name_deleted'
);
SET @ddl_sql := IF(@idx_exists = 0,
    'ALTER TABLE keyword_group ADD UNIQUE KEY uk_company_name_deleted (company_id, name, deleted)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;
