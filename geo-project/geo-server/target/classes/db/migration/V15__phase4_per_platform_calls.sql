-- ============================================================
-- V15: per-platform calls per question (P0/P1/P2)
-- ============================================================

-- package_plan: add per-platform call fields
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'package_plan'
      AND column_name = 'per_question_calls_p0'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN per_question_calls_p0 INT NOT NULL DEFAULT 1 COMMENT ''P0 calls per question'' AFTER per_question_platform_calls',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'package_plan'
      AND column_name = 'per_question_calls_p1'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN per_question_calls_p1 INT NOT NULL DEFAULT 1 COMMENT ''P1 calls per question'' AFTER per_question_calls_p0',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'package_plan'
      AND column_name = 'per_question_calls_p2'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN per_question_calls_p2 INT NOT NULL DEFAULT 1 COMMENT ''P2 calls per question'' AFTER per_question_calls_p1',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

-- project snapshot: add per-platform call fields
SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'plan_per_question_calls_p0'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_per_question_calls_p0 INT NULL COMMENT ''snapshot: P0 calls per question'' AFTER plan_per_question_platform_calls',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'plan_per_question_calls_p1'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_per_question_calls_p1 INT NULL COMMENT ''snapshot: P1 calls per question'' AFTER plan_per_question_calls_p0',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'plan_per_question_calls_p2'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_per_question_calls_p2 INT NULL COMMENT ''snapshot: P2 calls per question'' AFTER plan_per_question_calls_p1',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

-- backfill package_plan new fields from legacy unified field
UPDATE package_plan
SET per_question_calls_p0 = CASE
        WHEN per_question_calls_p0 IS NULL OR per_question_calls_p0 <= 0 THEN GREATEST(per_question_platform_calls, 1)
        ELSE per_question_calls_p0
    END,
    per_question_calls_p1 = CASE
        WHEN per_question_calls_p1 IS NULL OR per_question_calls_p1 <= 0 THEN GREATEST(per_question_platform_calls, 1)
        ELSE per_question_calls_p1
    END,
    per_question_calls_p2 = CASE
        WHEN per_question_calls_p2 IS NULL OR per_question_calls_p2 <= 0 THEN GREATEST(per_question_platform_calls, 1)
        ELSE per_question_calls_p2
    END;

-- keep legacy unified field aligned (compatibility)
UPDATE package_plan
SET per_question_platform_calls = GREATEST(
    COALESCE(per_question_calls_p0, 1),
    COALESCE(per_question_calls_p1, 1),
    COALESCE(per_question_calls_p2, 1)
);

-- backfill project snapshot new fields
UPDATE project
SET plan_per_question_calls_p0 = CASE
        WHEN plan_per_question_calls_p0 IS NULL OR plan_per_question_calls_p0 <= 0 THEN GREATEST(COALESCE(plan_per_question_platform_calls, 1), 1)
        ELSE plan_per_question_calls_p0
    END,
    plan_per_question_calls_p1 = CASE
        WHEN plan_per_question_calls_p1 IS NULL OR plan_per_question_calls_p1 <= 0 THEN GREATEST(COALESCE(plan_per_question_platform_calls, 1), 1)
        ELSE plan_per_question_calls_p1
    END,
    plan_per_question_calls_p2 = CASE
        WHEN plan_per_question_calls_p2 IS NULL OR plan_per_question_calls_p2 <= 0 THEN GREATEST(COALESCE(plan_per_question_platform_calls, 1), 1)
        ELSE plan_per_question_calls_p2
    END;
