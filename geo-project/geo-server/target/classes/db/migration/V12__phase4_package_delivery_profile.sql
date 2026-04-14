-- ============================================================
-- V12: package plan business delivery profile
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'package_plan'
      AND column_name = 'question_pool_size'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN question_pool_size INT NOT NULL DEFAULT 100 COMMENT ''question pool total size'' AFTER service_months',
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
      AND column_name = 'core_question_count'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN core_question_count INT NOT NULL DEFAULT 20 COMMENT ''core questions count'' AFTER question_pool_size',
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
      AND column_name = 'platform_p0_count'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN platform_p0_count INT NOT NULL DEFAULT 2 COMMENT ''P0 platform count'' AFTER core_question_count',
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
      AND column_name = 'platform_p1_count'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN platform_p1_count INT NOT NULL DEFAULT 2 COMMENT ''P1 platform count'' AFTER platform_p0_count',
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
      AND column_name = 'platform_p2_count'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN platform_p2_count INT NOT NULL DEFAULT 1 COMMENT ''P2 platform count'' AFTER platform_p1_count',
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
      AND column_name = 'per_question_platform_calls'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN per_question_platform_calls INT NOT NULL DEFAULT 1 COMMENT ''calls per question per platform'' AFTER platform_p2_count',
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
      AND column_name = 'biweekly_frequency'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN biweekly_frequency TINYINT NOT NULL DEFAULT 1 COMMENT ''1:with biweekly brief, 2:without biweekly brief'' AFTER per_question_platform_calls',
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
      AND column_name = 'monthly_report_depth'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN monthly_report_depth VARCHAR(8) NOT NULL DEFAULT ''L2'' COMMENT ''monthly report depth level'' AFTER biweekly_frequency',
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
      AND column_name = 'quarterly_report_depth'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN quarterly_report_depth VARCHAR(8) NOT NULL DEFAULT ''L2'' COMMENT ''quarterly report depth level'' AFTER monthly_report_depth',
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
      AND column_name = 'consultant_intensity'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN consultant_intensity VARCHAR(8) NOT NULL DEFAULT ''L2'' COMMENT ''consultant involvement intensity'' AFTER quarterly_report_depth',
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
      AND column_name = 'competitor_insight_depth'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN competitor_insight_depth VARCHAR(8) NOT NULL DEFAULT ''L2'' COMMENT ''competitor insight depth'' AFTER consultant_intensity',
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
      AND column_name = 'media_distribution_intensity'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN media_distribution_intensity VARCHAR(8) NOT NULL DEFAULT ''L2'' COMMENT ''media distribution intensity'' AFTER competitor_insight_depth',
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
      AND column_name = 'commitment_target_intensity'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN commitment_target_intensity VARCHAR(8) NOT NULL DEFAULT ''L2'' COMMENT ''commitment target intensity'' AFTER media_distribution_intensity',
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
      AND column_name = 'target_metric_type'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN target_metric_type VARCHAR(64) NOT NULL DEFAULT ''brand_mention_rate'' COMMENT ''quantified commitment metric type'' AFTER commitment_target_intensity',
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
      AND column_name = 'target_metric_value'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN target_metric_value DECIMAL(10,4) NOT NULL DEFAULT 0.0500 COMMENT ''quantified commitment metric target value'' AFTER target_metric_type',
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
      AND column_name = 'target_window_days'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE package_plan ADD COLUMN target_window_days INT NOT NULL DEFAULT 90 COMMENT ''target evaluation window in days'' AFTER target_metric_value',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

-- default values for three baseline package types
UPDATE package_plan
SET question_pool_size = 100,
    core_question_count = 20,
    platform_p0_count = 2,
    platform_p1_count = 2,
    platform_p2_count = 1,
    per_question_platform_calls = 1,
    biweekly_frequency = 2,
    monthly_report_depth = 'L2',
    quarterly_report_depth = 'L1',
    consultant_intensity = 'L2',
    competitor_insight_depth = 'L2',
    media_distribution_intensity = 'L2',
    commitment_target_intensity = 'L2',
    target_metric_type = 'brand_mention_rate',
    target_metric_value = 0.0500,
    target_window_days = 90
WHERE package_type = 'trial_6980';

UPDATE package_plan
SET question_pool_size = 300,
    core_question_count = 60,
    platform_p0_count = 3,
    platform_p1_count = 4,
    platform_p2_count = 3,
    per_question_platform_calls = 1,
    biweekly_frequency = 1,
    monthly_report_depth = 'L3',
    quarterly_report_depth = 'L3',
    consultant_intensity = 'L3',
    competitor_insight_depth = 'L3',
    media_distribution_intensity = 'L3',
    commitment_target_intensity = 'L3',
    target_metric_type = 'brand_mention_rate',
    target_metric_value = 0.1200,
    target_window_days = 90
WHERE package_type = 'standard_12800';

UPDATE package_plan
SET question_pool_size = 600,
    core_question_count = 120,
    platform_p0_count = 4,
    platform_p1_count = 6,
    platform_p2_count = 6,
    per_question_platform_calls = 2,
    biweekly_frequency = 1,
    monthly_report_depth = 'L5',
    quarterly_report_depth = 'L5',
    consultant_intensity = 'L5',
    competitor_insight_depth = 'L4',
    media_distribution_intensity = 'L5',
    commitment_target_intensity = 'L5',
    target_metric_type = 'brand_mention_rate',
    target_metric_value = 0.2000,
    target_window_days = 90
WHERE package_type = 'growth_26800';

-- dictionary seeds
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'intensity_level', 'L1', '低', 10
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'intensity_level' AND dict_key = 'L1');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'intensity_level', 'L2', '较低', 20
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'intensity_level' AND dict_key = 'L2');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'intensity_level', 'L3', '中', 30
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'intensity_level' AND dict_key = 'L3');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'intensity_level', 'L4', '较高', 40
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'intensity_level' AND dict_key = 'L4');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'intensity_level', 'L5', '高', 50
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'intensity_level' AND dict_key = 'L5');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'biweekly_frequency', '1', '有双周简报', 10
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'biweekly_frequency' AND dict_key = '1');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'biweekly_frequency', '2', '无双周简报', 20
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'biweekly_frequency' AND dict_key = '2');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'target_metric_type', 'brand_mention_rate', '品牌提及率', 10
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'target_metric_type' AND dict_key = 'brand_mention_rate');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'target_metric_type', 'project_relevance_rate', '项目关联率', 20
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'target_metric_type' AND dict_key = 'project_relevance_rate');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'target_metric_type', 'top3_visibility_rate', 'Top3曝光率', 30
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'target_metric_type' AND dict_key = 'top3_visibility_rate');
