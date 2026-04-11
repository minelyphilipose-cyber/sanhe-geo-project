-- ============================================================
-- V4: backfill sys_user.email safely
-- ============================================================

SET @col_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'email'
);

SET @ddl := IF(
  @col_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN email VARCHAR(128) NULL AFTER phone',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
