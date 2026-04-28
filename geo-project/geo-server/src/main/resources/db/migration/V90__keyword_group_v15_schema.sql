-- ============================================================
-- V90: keyword group V1.5 schema
-- ============================================================

-- 0) 确保 sys_dict_item 存在 (dict_type, dict_key) 唯一索引
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_dict_item'
      AND index_name = 'uk_dict_type_key'
);
SET @ddl_sql := IF(@idx_exists = 0,
    'ALTER TABLE sys_dict_item ADD UNIQUE KEY uk_dict_type_key (dict_type, dict_key)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

-- 1) 独立拓词类型字典 keyword_group_type
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
VALUES ('keyword_group_type', 'brand', '品牌词', 10, 1, '拓词管理类型')
ON DUPLICATE KEY UPDATE dict_value = VALUES(dict_value), sort_order = VALUES(sort_order), enabled = VALUES(enabled), remark = VALUES(remark);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
VALUES ('keyword_group_type', 'decision', '决策词', 20, 1, '拓词管理类型')
ON DUPLICATE KEY UPDATE dict_value = VALUES(dict_value), sort_order = VALUES(sort_order), enabled = VALUES(enabled), remark = VALUES(remark);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
VALUES ('keyword_group_type', 'transaction', '成交词', 30, 1, '拓词管理类型')
ON DUPLICATE KEY UPDATE dict_value = VALUES(dict_value), sort_order = VALUES(sort_order), enabled = VALUES(enabled), remark = VALUES(remark);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
VALUES ('keyword_group_type', 'comparison', '对比词', 40, 1, '拓词管理类型')
ON DUPLICATE KEY UPDATE dict_value = VALUES(dict_value), sort_order = VALUES(sort_order), enabled = VALUES(enabled), remark = VALUES(remark);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
VALUES ('keyword_group_type', 'qa', '问答词', 50, 1, '拓词管理类型')
ON DUPLICATE KEY UPDATE dict_value = VALUES(dict_value), sort_order = VALUES(sort_order), enabled = VALUES(enabled), remark = VALUES(remark);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
VALUES ('keyword_group_type', 'function', '功能词', 60, 1, '拓词管理类型')
ON DUPLICATE KEY UPDATE dict_value = VALUES(dict_value), sort_order = VALUES(sort_order), enabled = VALUES(enabled), remark = VALUES(remark);

-- 2) keyword_affix_word 扩展字段
SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'sub_category'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN sub_category VARCHAR(50) NULL COMMENT ''词库子分类'' AFTER affix_kind',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'visual_tag'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN visual_tag VARCHAR(20) NULL COMMENT ''视觉标签: toB/toC/common'' AFTER sub_category',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'industry_tag'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN industry_tag VARCHAR(30) NULL COMMENT ''功能词行业标签'' AFTER visual_tag',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'is_manual'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN is_manual TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否手动添加'' AFTER enabled',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'is_temporary'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN is_temporary TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否临时词'' AFTER is_manual',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'scope_type'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN scope_type VARCHAR(16) NULL COMMENT ''作用域: company/project/global；is_temporary=1 时必填，is_temporary=0 时通常为 NULL 或 global'' AFTER is_temporary',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'scope_id'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN scope_id BIGINT NULL COMMENT ''作用域 ID；is_temporary=1 时必填，is_temporary=0 时通常为 NULL'' AFTER scope_type',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'last_used_at'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN last_used_at DATETIME NULL COMMENT ''最后被保存使用时间'' AFTER scope_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'added_by_user_id'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN added_by_user_id BIGINT NULL COMMENT ''添加人用户 ID'' AFTER last_used_at',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'approval_status'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT ''approved'' COMMENT ''审批状态: pending/approved/rejected'' AFTER added_by_user_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'approval_reason'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN approval_reason TEXT NULL COMMENT ''审批申请理由或拒绝原因'' AFTER approval_status',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'approved_by'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN approved_by BIGINT NULL COMMENT ''审批人用户 ID'' AFTER approval_reason',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND column_name = 'approved_at'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_affix_word ADD COLUMN approved_at DATETIME NULL COMMENT ''审批时间'' AFTER approved_by',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

-- 2.1) keyword_affix_word 新索引
SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND index_name = 'idx_kw_affix_options'
);
SET @ddl_sql := IF(@idx_exists = 0,
    'ALTER TABLE keyword_affix_word ADD KEY idx_kw_affix_options (`type`, affix_kind, enabled, industry_tag, sort_order, id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND index_name = 'idx_kw_affix_approval'
);
SET @ddl_sql := IF(@idx_exists = 0,
    'ALTER TABLE keyword_affix_word ADD KEY idx_kw_affix_approval (approval_status, is_manual, updated_at)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'keyword_affix_word' AND index_name = 'idx_kw_affix_manual_scope'
);
SET @ddl_sql := IF(@idx_exists = 0,
    'ALTER TABLE keyword_affix_word ADD KEY idx_kw_affix_manual_scope (is_manual, is_temporary, scope_type, scope_id, approval_status, enabled)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

-- 3) keyword_group_word.column_type 枚举扩展在代码层完成:
--    area/prefix/core/industry/suffix/core_a/compare/core_b
--    DB 当前为 VARCHAR，无需 ALTER 枚举约束。

-- 4) region -> area 一次性迁移 + 备份表
CREATE TABLE IF NOT EXISTS keyword_group_word_region_backup (
    id BIGINT PRIMARY KEY,
    old_column_type VARCHAR(16) NOT NULL,
    backed_up_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='backup before keyword_group_word region to area migration';

INSERT INTO keyword_group_word_region_backup (id, old_column_type)
SELECT id, column_type
FROM keyword_group_word
WHERE column_type = 'region'
  AND NOT EXISTS (
      SELECT 1 FROM keyword_group_word_region_backup b WHERE b.id = keyword_group_word.id
  );

UPDATE keyword_group_word
SET column_type = 'area'
WHERE column_type = 'region';

-- 5) qa.industry 软下架，开发期保留备份方便回滚
CREATE TABLE IF NOT EXISTS keyword_affix_word_qa_industry_backup (
    id BIGINT PRIMARY KEY,
    old_enabled TINYINT(1) NOT NULL,
    backed_up_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='backup before qa industry soft disable';

INSERT INTO keyword_affix_word_qa_industry_backup (id, old_enabled)
SELECT id, enabled
FROM keyword_affix_word
WHERE `type` = 'qa'
  AND affix_kind = 'industry'
  AND NOT EXISTS (
      SELECT 1 FROM keyword_affix_word_qa_industry_backup b WHERE b.id = keyword_affix_word.id
  );

UPDATE keyword_affix_word
SET enabled = 0
WHERE `type` = 'qa'
  AND affix_kind = 'industry';

-- 6) keyword_group 扩展字段
SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group' AND column_name = 'area_enabled'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group ADD COLUMN area_enabled TINYINT(1) NULL COMMENT ''是否启用地区词，NULL 表示按类型默认'' AFTER `type`',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group' AND column_name = 'function_industry_tag'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group ADD COLUMN function_industry_tag VARCHAR(30) NULL COMMENT ''功能词行业标签'' AFTER area_enabled',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group' AND column_name = 'project_id'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group ADD COLUMN project_id BIGINT NULL COMMENT ''关联项目 ID，阶段三配额预留'' AFTER company_id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group' AND index_name = 'idx_keyword_group_project'
);
SET @ddl_sql := IF(@idx_exists = 0,
    'ALTER TABLE keyword_group ADD KEY idx_keyword_group_project (project_id, updated_at)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

-- 老组地区词兜底：只要已保存过地区列，就显式开启 area_enabled，避免按新类型默认值误隐藏
UPDATE keyword_group g
SET area_enabled = 1
WHERE area_enabled IS NULL
  AND EXISTS (
      SELECT 1 FROM keyword_group_word w
      WHERE w.group_id = g.id
        AND w.column_type IN ('area', 'region')
  );

-- 7) blacklist_word 表
CREATE TABLE IF NOT EXISTS blacklist_word (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    word_text VARCHAR(128) NOT NULL,
    normalized_word VARCHAR(128) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    remark VARCHAR(255) NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_blacklist_word_normalized (normalized_word),
    KEY idx_blacklist_enabled_updated (enabled, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='keyword generation blacklist words; normalized_word must be recomputed when normalization rules change';

-- 8) 防御性 search -> decision，当前实际库无 search 数据，但保留兼容旧环境
UPDATE keyword_affix_word
SET `type` = 'decision'
WHERE `type` = 'search';
