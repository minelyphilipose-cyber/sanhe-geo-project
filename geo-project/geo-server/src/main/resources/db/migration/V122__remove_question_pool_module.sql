-- ============================================================
-- V122: remove legacy question pool module
-- ============================================================

DELETE da
FROM dispatch_alert da
JOIN dispatch_task dt ON dt.id = da.task_id
WHERE dt.task_type = 'QUESTION_STRATEGY_GENERATION';

DELETE FROM dispatch_task
WHERE task_type = 'QUESTION_STRATEGY_GENERATION';

UPDATE project
SET stage = 'executing'
WHERE stage = 'building_questions';

SET @fk_name := (
    SELECT constraint_name
    FROM information_schema.key_column_usage
    WHERE table_schema = DATABASE()
      AND table_name = 'poll_results'
      AND column_name = 'question_id'
      AND referenced_table_name = 'question_pool_item'
    LIMIT 1
);
SET @sql := IF(@fk_name IS NULL, 'SELECT 1', CONCAT('ALTER TABLE poll_results DROP FOREIGN KEY ', @fk_name));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'poll_results'
      AND index_name = 'uk_poll_result_question_unique'
);
SET @sql := IF(@idx_exists = 0, 'SELECT 1', 'ALTER TABLE poll_results DROP INDEX uk_poll_result_question_unique');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'poll_results'
      AND column_name = 'question_id'
);
SET @sql := IF(@col_exists = 0, 'SELECT 1', 'ALTER TABLE poll_results DROP COLUMN question_id');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'package_plan'
      AND column_name = 'question_pool_size'
);
SET @new_col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'package_plan'
      AND column_name = 'keyword_group_limit'
);
SET @sql := IF(@old_col_exists > 0 AND @new_col_exists = 0,
    'ALTER TABLE package_plan CHANGE COLUMN question_pool_size keyword_group_limit INT NOT NULL DEFAULT 100 COMMENT ''keyword group total limit''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'package_plan'
      AND column_name = 'core_question_count'
);
SET @sql := IF(@col_exists = 0, 'SELECT 1', 'ALTER TABLE package_plan DROP COLUMN core_question_count');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'plan_question_pool_size'
);
SET @new_col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'plan_keyword_group_limit'
);
SET @sql := IF(@old_col_exists > 0 AND @new_col_exists = 0,
    'ALTER TABLE project CHANGE COLUMN plan_question_pool_size plan_keyword_group_limit INT NULL COMMENT ''snapshot: keyword group total limit''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'plan_core_question_count'
);
SET @sql := IF(@col_exists = 0, 'SELECT 1', 'ALTER TABLE project DROP COLUMN plan_core_question_count');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'company_package_binding'
      AND column_name = 'question_pool_limit'
);
SET @new_col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'company_package_binding'
      AND column_name = 'keyword_group_limit'
);
SET @sql := IF(@old_col_exists > 0 AND @new_col_exists = 0,
    'ALTER TABLE company_package_binding CHANGE COLUMN question_pool_limit keyword_group_limit INT NOT NULL COMMENT ''keyword group total limit''',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS article_question_rel;
DROP TABLE IF EXISTS question_pool_item;
DROP TABLE IF EXISTS question_pool_version;

DELETE rp
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.perm_key IN ('question_pool.core.confirm', 'question_pool.core.delete');

DELETE FROM sys_permission
WHERE perm_key IN ('question_pool.core.confirm', 'question_pool.core.delete');

DELETE FROM sys_dict_item
WHERE dict_type = 'dispatch_task_type'
  AND dict_key = 'QUESTION_STRATEGY_GENERATION';

DELETE FROM sys_dict_item
WHERE dict_type = 'project_stage'
  AND dict_key = 'building_questions';
