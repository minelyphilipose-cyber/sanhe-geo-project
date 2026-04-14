-- ============================================================
-- V13: project package delivery snapshot
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'plan_question_pool_size'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_question_pool_size INT NULL COMMENT ''snapshot: question pool total'' AFTER service_months',
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
      AND column_name = 'plan_core_question_count'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_core_question_count INT NULL COMMENT ''snapshot: core question count'' AFTER plan_question_pool_size',
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
      AND column_name = 'plan_platform_p0_count'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_platform_p0_count INT NULL COMMENT ''snapshot: P0 platform count'' AFTER plan_core_question_count',
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
      AND column_name = 'plan_platform_p1_count'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_platform_p1_count INT NULL COMMENT ''snapshot: P1 platform count'' AFTER plan_platform_p0_count',
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
      AND column_name = 'plan_platform_p2_count'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_platform_p2_count INT NULL COMMENT ''snapshot: P2 platform count'' AFTER plan_platform_p1_count',
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
      AND column_name = 'plan_per_question_platform_calls'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_per_question_platform_calls INT NULL COMMENT ''snapshot: calls per question per platform'' AFTER plan_platform_p2_count',
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
      AND column_name = 'plan_biweekly_frequency'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_biweekly_frequency TINYINT NULL COMMENT ''snapshot: biweekly frequency'' AFTER plan_per_question_platform_calls',
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
      AND column_name = 'plan_monthly_report_depth'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_monthly_report_depth VARCHAR(8) NULL COMMENT ''snapshot: monthly report depth'' AFTER plan_biweekly_frequency',
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
      AND column_name = 'plan_quarterly_report_depth'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_quarterly_report_depth VARCHAR(8) NULL COMMENT ''snapshot: quarterly report depth'' AFTER plan_monthly_report_depth',
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
      AND column_name = 'plan_consultant_intensity'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_consultant_intensity VARCHAR(8) NULL COMMENT ''snapshot: consultant intensity'' AFTER plan_quarterly_report_depth',
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
      AND column_name = 'plan_competitor_insight_depth'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_competitor_insight_depth VARCHAR(8) NULL COMMENT ''snapshot: competitor insight depth'' AFTER plan_consultant_intensity',
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
      AND column_name = 'plan_media_distribution_intensity'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_media_distribution_intensity VARCHAR(8) NULL COMMENT ''snapshot: media distribution intensity'' AFTER plan_competitor_insight_depth',
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
      AND column_name = 'plan_commitment_target_intensity'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_commitment_target_intensity VARCHAR(8) NULL COMMENT ''snapshot: commitment target intensity'' AFTER plan_media_distribution_intensity',
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
      AND column_name = 'plan_target_metric_type'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_target_metric_type VARCHAR(64) NULL COMMENT ''snapshot: target metric type'' AFTER plan_commitment_target_intensity',
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
      AND column_name = 'plan_target_metric_value'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_target_metric_value DECIMAL(10,4) NULL COMMENT ''snapshot: target metric value'' AFTER plan_target_metric_type',
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
      AND column_name = 'plan_target_window_days'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN plan_target_window_days INT NULL COMMENT ''snapshot: target window days'' AFTER plan_target_metric_value',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

-- backfill old projects with current package_plan values by package_type
UPDATE project p
JOIN package_plan pp ON p.package_type = pp.package_type
SET p.plan_question_pool_size = COALESCE(p.plan_question_pool_size, pp.question_pool_size),
    p.plan_core_question_count = COALESCE(p.plan_core_question_count, pp.core_question_count),
    p.plan_platform_p0_count = COALESCE(p.plan_platform_p0_count, pp.platform_p0_count),
    p.plan_platform_p1_count = COALESCE(p.plan_platform_p1_count, pp.platform_p1_count),
    p.plan_platform_p2_count = COALESCE(p.plan_platform_p2_count, pp.platform_p2_count),
    p.plan_per_question_platform_calls = COALESCE(p.plan_per_question_platform_calls, pp.per_question_platform_calls),
    p.plan_biweekly_frequency = COALESCE(p.plan_biweekly_frequency, pp.biweekly_frequency),
    p.plan_monthly_report_depth = COALESCE(p.plan_monthly_report_depth, pp.monthly_report_depth),
    p.plan_quarterly_report_depth = COALESCE(p.plan_quarterly_report_depth, pp.quarterly_report_depth),
    p.plan_consultant_intensity = COALESCE(p.plan_consultant_intensity, pp.consultant_intensity),
    p.plan_competitor_insight_depth = COALESCE(p.plan_competitor_insight_depth, pp.competitor_insight_depth),
    p.plan_media_distribution_intensity = COALESCE(p.plan_media_distribution_intensity, pp.media_distribution_intensity),
    p.plan_commitment_target_intensity = COALESCE(p.plan_commitment_target_intensity, pp.commitment_target_intensity),
    p.plan_target_metric_type = COALESCE(p.plan_target_metric_type, pp.target_metric_type),
    p.plan_target_metric_value = COALESCE(p.plan_target_metric_value, pp.target_metric_value),
    p.plan_target_window_days = COALESCE(p.plan_target_window_days, pp.target_window_days);
