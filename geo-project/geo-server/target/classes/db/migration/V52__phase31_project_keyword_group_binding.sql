-- ============================================================
-- V52: bind keyword group to company and project-keyword-group relation
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'keyword_group'
      AND column_name = 'company_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE keyword_group ADD COLUMN company_id BIGINT NULL AFTER id',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'keyword_group'
      AND index_name = 'idx_company_type_updated'
);
SET @ddl_sql := IF(
    @idx_exists = 0,
    'ALTER TABLE keyword_group ADD KEY idx_company_type_updated (company_id, `type`, updated_at)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @fk_exists := (
    SELECT COUNT(1)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'keyword_group'
      AND constraint_name = 'fk_keyword_group_company'
      AND constraint_type = 'FOREIGN KEY'
);
SET @ddl_sql := IF(
    @fk_exists = 0,
    'ALTER TABLE keyword_group ADD CONSTRAINT fk_keyword_group_company FOREIGN KEY (company_id) REFERENCES company(id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

CREATE TABLE IF NOT EXISTS project_keyword_group_rel (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id        BIGINT NOT NULL,
    keyword_group_id  BIGINT NOT NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_group (project_id, keyword_group_id),
    KEY idx_group_project (keyword_group_id, project_id),
    CONSTRAINT fk_project_keyword_group_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_keyword_group_group FOREIGN KEY (keyword_group_id) REFERENCES keyword_group(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='project and keyword-group relation';
