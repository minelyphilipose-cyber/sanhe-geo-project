-- ============================================================
-- V31: ai platform config dispatch extensions
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_platform_config'
      AND column_name = 'rpm_limit'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN rpm_limit INT NOT NULL DEFAULT 60 COMMENT ''rpm limit'' AFTER priority_level',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_platform_config'
      AND column_name = 'tpm_limit'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN tpm_limit INT NOT NULL DEFAULT 60000 COMMENT ''tpm limit'' AFTER rpm_limit',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_platform_config'
      AND column_name = 'primary_key_ref'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN primary_key_ref VARCHAR(128) NULL COMMENT ''primary key ref'' AFTER api_key',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_platform_config'
      AND column_name = 'backup_key_ref'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN backup_key_ref VARCHAR(128) NULL COMMENT ''backup key ref'' AFTER primary_key_ref',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_platform_config'
      AND column_name = 'backup_provider_name'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN backup_provider_name VARCHAR(128) NULL COMMENT ''backup provider name'' AFTER backup_key_ref',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_platform_config'
      AND column_name = 'backup_api_url'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN backup_api_url VARCHAR(255) NULL COMMENT ''backup api url'' AFTER backup_provider_name',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_platform_config'
      AND column_name = 'backup_model_id'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN backup_model_id VARCHAR(128) NULL COMMENT ''backup model id'' AFTER backup_api_url',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_platform_config'
      AND column_name = 'current_health_status'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN current_health_status VARCHAR(32) NOT NULL DEFAULT ''normal'' COMMENT ''platform health status'' AFTER degraded_reason',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_platform_config'
      AND column_name = 'last_failure_at'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN last_failure_at DATETIME NULL COMMENT ''last failure time'' AFTER current_health_status',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @ddl_sql := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'ai_platform_config'
              AND column_name = 'api_key'
              AND is_nullable = 'NO'
        ),
        'ALTER TABLE ai_platform_config MODIFY COLUMN api_key VARCHAR(512) NULL COMMENT ''encrypted api key''',
        'SELECT 1'
    )
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;
