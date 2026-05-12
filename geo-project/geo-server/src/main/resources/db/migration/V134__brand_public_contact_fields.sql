-- V134: add brand public contact fields

SET @sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE brand ADD COLUMN public_phone VARCHAR(64) NULL COMMENT ''brand public phone'' AFTER phone',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'brand'
    AND column_name = 'public_phone'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE brand ADD COLUMN public_address VARCHAR(255) NULL COMMENT ''brand public address'' AFTER public_phone',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'brand'
    AND column_name = 'public_address'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
