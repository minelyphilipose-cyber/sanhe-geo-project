SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE batch_article_generation_task ADD COLUMN suggested_platform_codes JSON NULL COMMENT ''system suggested platform codes for this topic'' AFTER template_source',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'batch_article_generation_task'
    AND column_name = 'suggested_platform_codes'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE batch_article_generation_task ADD COLUMN selected_platform_codes JSON NULL COMMENT ''operator selected platform codes for this topic'' AFTER suggested_platform_codes',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'batch_article_generation_task'
    AND column_name = 'selected_platform_codes'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE batch_article_generation_task ADD COLUMN readiness_warning_confirmed TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''operator confirmed readiness warnings for this task'' AFTER selected_platform_codes',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'batch_article_generation_task'
    AND column_name = 'readiness_warning_confirmed'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE batch_article_generation_task ADD COLUMN readiness_warning_codes JSON NULL COMMENT ''readiness warning codes carried by this task'' AFTER readiness_warning_confirmed',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'batch_article_generation_task'
    AND column_name = 'readiness_warning_codes'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
