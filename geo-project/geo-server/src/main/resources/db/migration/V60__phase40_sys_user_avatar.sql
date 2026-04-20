-- ============================================================
-- V60: sys_user avatar fields
-- ============================================================

SET @avatar_url_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'avatar_url'
);

SET @avatar_url_ddl := IF(
  @avatar_url_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN avatar_url VARCHAR(255) NULL AFTER email',
  'SELECT 1'
);

PREPARE stmt FROM @avatar_url_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @avatar_object_key_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'avatar_object_key'
);

SET @avatar_object_key_ddl := IF(
  @avatar_object_key_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN avatar_object_key VARCHAR(255) NULL AFTER avatar_url',
  'SELECT 1'
);

PREPARE stmt FROM @avatar_object_key_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
