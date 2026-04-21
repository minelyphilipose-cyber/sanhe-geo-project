-- ============================================================
-- V66: ai platform model tiers + concurrency limit
-- ============================================================

SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_platform_config'
      AND COLUMN_NAME = 'low_model_id'
);
SET @sql := IF(
    @col = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN low_model_id VARCHAR(128) NULL COMMENT ''low performance model id'' AFTER model_id',
    'SELECT 1'
);
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @col := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_platform_config'
      AND COLUMN_NAME = 'concurrency_limit'
);
SET @sql := IF(
    @col = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN concurrency_limit INT NOT NULL DEFAULT 1 COMMENT ''platform concurrency limit'' AFTER tpm_limit',
    'SELECT 1'
);
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;
