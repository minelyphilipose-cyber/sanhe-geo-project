-- ============================================================
-- V55 rollback draft, dev only. Do not put into Flyway.
-- ============================================================

-- 1) search -> decision 无法无损回滚；当前实际库迁移前无 type='search' 数据。
--    如特定环境存在 search 数据，需迁移前额外备份 keyword_affix_word search rows。

-- 2) 恢复 qa.industry enabled
UPDATE keyword_affix_word k
JOIN keyword_affix_word_qa_industry_backup b ON b.id = k.id
SET k.enabled = b.old_enabled
WHERE k.`type` = 'qa'
  AND k.affix_kind = 'industry';

-- 3) area -> region，仅恢复迁移前 region 行
UPDATE keyword_group_word w
JOIN keyword_group_word_region_backup b ON b.id = w.id
SET w.column_type = b.old_column_type
WHERE w.column_type = 'area';

-- 4) 删除 blacklist_word
DROP TABLE IF EXISTS blacklist_word;

-- 5) 删除 keyword_group 索引和扩展字段
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group' AND index_name = 'idx_keyword_group_project'
);
SET @ddl_sql := IF(@idx_exists > 0,
    'ALTER TABLE keyword_group DROP INDEX idx_keyword_group_project',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group' AND column_name = 'project_id'
);
SET @ddl_sql := IF(@col_exists > 0,
    'ALTER TABLE keyword_group DROP COLUMN project_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group' AND column_name = 'function_industry_tag'
);
SET @ddl_sql := IF(@col_exists > 0,
    'ALTER TABLE keyword_group DROP COLUMN function_industry_tag',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group' AND column_name = 'area_enabled'
);
SET @ddl_sql := IF(@col_exists > 0,
    'ALTER TABLE keyword_group DROP COLUMN area_enabled',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

-- 6) 删除 keyword_affix_word 新索引
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND index_name = 'idx_kw_affix_manual_scope'
);
SET @ddl_sql := IF(@idx_exists > 0,
    'ALTER TABLE keyword_affix_word DROP INDEX idx_kw_affix_manual_scope',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND index_name = 'idx_kw_affix_approval'
);
SET @ddl_sql := IF(@idx_exists > 0,
    'ALTER TABLE keyword_affix_word DROP INDEX idx_kw_affix_approval',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND index_name = 'idx_kw_affix_options'
);
SET @ddl_sql := IF(@idx_exists > 0,
    'ALTER TABLE keyword_affix_word DROP INDEX idx_kw_affix_options',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

-- 7) 删除 keyword_affix_word 扩展字段，按依赖倒序
SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'approved_at'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN approved_at', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'approved_by'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN approved_by', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'approval_reason'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN approval_reason', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'approval_status'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN approval_status', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'added_by_user_id'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN added_by_user_id', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'last_used_at'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN last_used_at', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'scope_id'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN scope_id', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'scope_type'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN scope_type', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'is_temporary'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN is_temporary', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'is_manual'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN is_manual', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'industry_tag'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN industry_tag', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'visual_tag'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN visual_tag', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'sub_category'
);
SET @ddl_sql := IF(@col_exists > 0, 'ALTER TABLE keyword_affix_word DROP COLUMN sub_category', 'SELECT 1');
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

-- 8) 删除独立拓词类型字典
DELETE FROM sys_dict_item
WHERE dict_type = 'keyword_group_type'
  AND dict_key IN ('brand', 'decision', 'transaction', 'comparison', 'qa', 'function');

-- 9) 开发期备份表默认保留，便于排查。
-- DROP TABLE IF EXISTS keyword_group_word_region_backup;
-- DROP TABLE IF EXISTS keyword_affix_word_qa_industry_backup;
