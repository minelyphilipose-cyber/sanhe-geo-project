-- ============================================================
-- V40: question strategy generation fields and dict
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'question_pool_item'
      AND column_name = 'content_strategy'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE question_pool_item ADD COLUMN content_strategy TEXT NULL COMMENT ''content strategy suggestion'' AFTER is_core',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'question_pool_item'
      AND column_name = 'strategy_keywords'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE question_pool_item ADD COLUMN strategy_keywords JSON NULL COMMENT ''strategy keywords'' AFTER content_strategy',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'question_pool_item'
      AND column_name = 'strategy_suggested_type'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE question_pool_item ADD COLUMN strategy_suggested_type VARCHAR(32) NULL COMMENT ''faq/scenario_content/industry_article'' AFTER strategy_keywords',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'question_pool_item'
      AND column_name = 'strategy_generated_at'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE question_pool_item ADD COLUMN strategy_generated_at DATETIME NULL AFTER strategy_suggested_type',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'question_pool_item'
      AND column_name = 'strategy_status'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE question_pool_item ADD COLUMN strategy_status VARCHAR(16) NOT NULL DEFAULT ''none'' COMMENT ''none/generated/edited'' AFTER strategy_generated_at',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dispatch_task_type', 'QUESTION_STRATEGY_GENERATION', '问题场景内容建议生成', 70, 1, 'question strategy generation'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_dict_item
    WHERE dict_type = 'dispatch_task_type'
      AND dict_key = 'QUESTION_STRATEGY_GENERATION'
);
