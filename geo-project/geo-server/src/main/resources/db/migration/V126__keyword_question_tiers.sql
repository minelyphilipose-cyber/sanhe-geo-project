-- Add A/B/C question tier quotas and project allocations.

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND column_name = 'question_tier'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE keyword_group_result ADD COLUMN question_tier VARCHAR(1) NOT NULL DEFAULT ''A'' COMMENT ''question tier: A/B/C'' AFTER seed_text',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'keyword_group_result' AND index_name = 'idx_group_tier_sort'
);
SET @ddl_sql := IF(@idx_exists = 0,
    'ALTER TABLE keyword_group_result ADD KEY idx_group_tier_sort (group_id, question_tier, sort_order, id)',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'package_plan' AND column_name = 'keyword_group_limit_a'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN keyword_group_limit_a INT NOT NULL DEFAULT 0 COMMENT ''A tier keyword question limit'' AFTER keyword_group_limit',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'package_plan' AND column_name = 'keyword_group_limit_b'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN keyword_group_limit_b INT NOT NULL DEFAULT 0 COMMENT ''B tier keyword question limit'' AFTER keyword_group_limit_a',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'package_plan' AND column_name = 'keyword_group_limit_c'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN keyword_group_limit_c INT NOT NULL DEFAULT 0 COMMENT ''C tier keyword question limit'' AFTER keyword_group_limit_b',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

UPDATE package_plan
SET keyword_group_limit_a = keyword_group_limit,
    keyword_group_limit_b = 0,
    keyword_group_limit_c = 0
WHERE keyword_group_limit_a = 0
  AND keyword_group_limit_b = 0
  AND keyword_group_limit_c = 0;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_package_binding' AND column_name = 'keyword_group_limit_a'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE company_package_binding ADD COLUMN keyword_group_limit_a INT NOT NULL DEFAULT 0 COMMENT ''A tier keyword question limit snapshot'' AFTER keyword_group_limit',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_package_binding' AND column_name = 'keyword_group_limit_b'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE company_package_binding ADD COLUMN keyword_group_limit_b INT NOT NULL DEFAULT 0 COMMENT ''B tier keyword question limit snapshot'' AFTER keyword_group_limit_a',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'company_package_binding' AND column_name = 'keyword_group_limit_c'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE company_package_binding ADD COLUMN keyword_group_limit_c INT NOT NULL DEFAULT 0 COMMENT ''C tier keyword question limit snapshot'' AFTER keyword_group_limit_b',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

UPDATE company_package_binding
SET keyword_group_limit_a = keyword_group_limit,
    keyword_group_limit_b = 0,
    keyword_group_limit_c = 0
WHERE keyword_group_limit_a = 0
  AND keyword_group_limit_b = 0
  AND keyword_group_limit_c = 0;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'project' AND column_name = 'plan_keyword_group_limit_a'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_keyword_group_limit_a INT NULL COMMENT ''A tier project keyword question allocation'' AFTER plan_keyword_group_limit',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'project' AND column_name = 'plan_keyword_group_limit_b'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_keyword_group_limit_b INT NULL COMMENT ''B tier project keyword question allocation'' AFTER plan_keyword_group_limit_a',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'project' AND column_name = 'plan_keyword_group_limit_c'
);
SET @ddl_sql := IF(@col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_keyword_group_limit_c INT NULL COMMENT ''C tier project keyword question allocation'' AFTER plan_keyword_group_limit_b',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql; EXECUTE ddl_stmt; DEALLOCATE PREPARE ddl_stmt;

UPDATE project
SET plan_keyword_group_limit_a = COALESCE(plan_keyword_group_limit, 0),
    plan_keyword_group_limit_b = 0,
    plan_keyword_group_limit_c = 0
WHERE plan_keyword_group_limit_a IS NULL
  AND plan_keyword_group_limit_b IS NULL
  AND plan_keyword_group_limit_c IS NULL;
