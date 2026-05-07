-- V117: remove project snapshot biweekly service frequency.
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'plan_biweekly_frequency'
);

SET @sql := IF(
    @col_exists > 0,
    'ALTER TABLE project DROP COLUMN plan_biweekly_frequency',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
