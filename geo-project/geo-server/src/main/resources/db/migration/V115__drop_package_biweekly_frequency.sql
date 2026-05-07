-- V100: remove package-level biweekly service frequency.
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'package_plan'
      AND column_name = 'biweekly_frequency'
);

SET @sql := IF(
    @col_exists > 0,
    'ALTER TABLE package_plan DROP COLUMN biweekly_frequency',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DELETE FROM sys_dict_item WHERE dict_type = 'biweekly_frequency';
