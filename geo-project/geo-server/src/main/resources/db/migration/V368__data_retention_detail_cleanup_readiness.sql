-- WP8 bounded detail cleanup and fail-closed readiness evidence.
-- This is forward-only schema expansion. It does not enable any runtime writer.

SET @v368_operation_count_column := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'data_retention_operation'
       AND column_name = 'detail_purged_item_count'
);
SET @v368_sql := CASE
    WHEN @v368_operation_count_column = 0 THEN
        'ALTER TABLE data_retention_operation ADD COLUMN detail_purged_item_count BIGINT UNSIGNED NULL AFTER detail_purged_at'
    WHEN @v368_operation_count_column = 1 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V368 operation purge count column is ambiguous'''
END;
PREPARE v368_stmt FROM @v368_sql;
EXECUTE v368_stmt;
DEALLOCATE PREPARE v368_stmt;

SET @v368_operation_count_definition := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'data_retention_operation'
       AND column_name = 'detail_purged_item_count'
       AND column_type = 'bigint unsigned' AND is_nullable = 'YES'
);
SET @v368_sql := IF(@v368_operation_count_definition = 1, 'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V368 operation purge count definition mismatch''');
PREPARE v368_stmt FROM @v368_sql;
EXECUTE v368_stmt;
DEALLOCATE PREPARE v368_stmt;

SET @v368_operation_shape_check := (
    SELECT COUNT(*) FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE() AND table_name = 'data_retention_operation'
       AND constraint_type = 'CHECK' AND constraint_name = 'chk_retention_operation_detail_purge_shape'
);
SET @v368_sql := CASE
    WHEN @v368_operation_shape_check = 0 THEN
        'ALTER TABLE data_retention_operation ADD CONSTRAINT chk_retention_operation_detail_purge_shape CHECK ((detail_purged_at IS NULL AND detail_purged_item_count IS NULL) OR (detail_purged_at IS NOT NULL AND detail_purged_item_count IS NOT NULL))'
    WHEN @v368_operation_shape_check = 1 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V368 operation purge shape check is ambiguous'''
END;
PREPARE v368_stmt FROM @v368_sql;
EXECUTE v368_stmt;
DEALLOCATE PREPARE v368_stmt;

SET @v368_operation_shape_definition := (
    SELECT COUNT(*)
      FROM information_schema.check_constraints
     WHERE constraint_schema = DATABASE()
       AND constraint_name = 'chk_retention_operation_detail_purge_shape'
       AND LOWER(REPLACE(REPLACE(REPLACE(check_clause,'`',''),' ',''),'_utf8mb4',''))
           LIKE '%detail_purged_at%isnull%detail_purged_item_count%isnull%or%detail_purged_at%isnotnull%detail_purged_item_count%isnotnull%'
);
SET @v368_sql := IF(@v368_operation_shape_definition = 1, 'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V368 operation purge shape check definition mismatch''');
PREPARE v368_stmt FROM @v368_sql;
EXECUTE v368_stmt;
DEALLOCATE PREPARE v368_stmt;

SET @v368_operation_run_count_column := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'data_retention_operation_run'
       AND column_name = 'detail_purged_attempt_count'
);
SET @v368_sql := CASE
    WHEN @v368_operation_run_count_column = 0 THEN
        'ALTER TABLE data_retention_operation_run ADD COLUMN detail_purged_attempt_count BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER relation_type'
    WHEN @v368_operation_run_count_column = 1 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V368 operation-run purge count column is ambiguous'''
END;
PREPARE v368_stmt FROM @v368_sql;
EXECUTE v368_stmt;
DEALLOCATE PREPARE v368_stmt;

SET @v368_operation_run_count_definition := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'data_retention_operation_run'
       AND column_name = 'detail_purged_attempt_count'
       AND column_type = 'bigint unsigned' AND is_nullable = 'NO' AND column_default = '0'
);
SET @v368_sql := IF(@v368_operation_run_count_definition = 1, 'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V368 operation-run purge count definition mismatch''');
PREPARE v368_stmt FROM @v368_sql;
EXECUTE v368_stmt;
DEALLOCATE PREPARE v368_stmt;

SET @v368_run_count_column := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'data_retention_run'
       AND column_name = 'operationless_attempt_purged_count'
);
SET @v368_sql := CASE
    WHEN @v368_run_count_column = 0 THEN
        'ALTER TABLE data_retention_run ADD COLUMN operationless_attempt_purged_count BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER attempt_detail_purged_at'
    WHEN @v368_run_count_column = 1 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V368 run purge count column is ambiguous'''
END;
PREPARE v368_stmt FROM @v368_sql;
EXECUTE v368_stmt;
DEALLOCATE PREPARE v368_stmt;

SET @v368_run_count_definition := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'data_retention_run'
       AND column_name = 'operationless_attempt_purged_count'
       AND column_type = 'bigint unsigned' AND is_nullable = 'NO' AND column_default = '0'
);
SET @v368_sql := IF(@v368_run_count_definition = 1, 'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V368 run purge count definition mismatch''');
PREPARE v368_stmt FROM @v368_sql;
EXECUTE v368_stmt;
DEALLOCATE PREPARE v368_stmt;

CREATE TABLE IF NOT EXISTS data_retention_detail_cleanup_run (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cleanup_run_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trigger_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    active_slot TINYINT UNSIGNED GENERATED ALWAYS AS
        (CASE WHEN status = 'running' THEN 1 ELSE NULL END) STORED,
    application_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    retention_cutoff_at DATETIME(3) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    heartbeat_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    candidate_operation_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    purged_operation_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    skipped_operation_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    warning_candidate_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    purged_operationless_run_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    purged_attempt_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    purged_item_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    eligible_backlog_attempt_count BIGINT UNSIGNED NULL,
    eligible_backlog_item_count BIGINT UNSIGNED NULL,
    oldest_unpurged_anchor_at DATETIME(3) NULL,
    retention_table_bytes BIGINT UNSIGNED NULL,
    projected_five_year_bytes BIGINT UNSIGNED NULL,
    last_error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    last_error VARCHAR(2000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_retention_detail_cleanup_run_id (cleanup_run_id),
    UNIQUE KEY uk_retention_detail_cleanup_active (active_slot),
    KEY idx_retention_detail_cleanup_status (status, finished_at),
    KEY idx_retention_detail_cleanup_started (started_at),
    CONSTRAINT chk_retention_detail_cleanup_source CHECK (trigger_source IN ('scheduler','manual_test')),
    CONSTRAINT chk_retention_detail_cleanup_status CHECK (status IN ('running','succeeded','failed')),
    CONSTRAINT chk_retention_detail_cleanup_shape CHECK (
        (status = 'running' AND finished_at IS NULL AND last_error_code IS NULL AND last_error IS NULL)
        OR (status = 'succeeded' AND finished_at IS NOT NULL AND last_error_code IS NULL AND last_error IS NULL
            AND eligible_backlog_attempt_count IS NOT NULL AND eligible_backlog_item_count IS NOT NULL
            AND retention_table_bytes IS NOT NULL)
        OR (status = 'failed' AND finished_at IS NOT NULL AND last_error_code IS NOT NULL)
    ),
    CONSTRAINT chk_retention_detail_cleanup_error_size CHECK
        (last_error IS NULL OR OCTET_LENGTH(last_error) <= 2048)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='permanent WP8 detail cleanup run and readiness evidence';

SET @v368_cleanup_column_count := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'data_retention_detail_cleanup_run'
);
SET @v368_cleanup_index_count := (
    SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'data_retention_detail_cleanup_run'
       AND index_name IN ('PRIMARY','uk_retention_detail_cleanup_run_id',
           'uk_retention_detail_cleanup_active','idx_retention_detail_cleanup_status',
           'idx_retention_detail_cleanup_started')
);
SET @v368_cleanup_check_count := (
    SELECT COUNT(*) FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE() AND table_name = 'data_retention_detail_cleanup_run'
       AND constraint_type = 'CHECK'
       AND constraint_name IN ('chk_retention_detail_cleanup_source',
           'chk_retention_detail_cleanup_status','chk_retention_detail_cleanup_shape',
           'chk_retention_detail_cleanup_error_size')
);
SET @v368_cleanup_active_definition := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'data_retention_detail_cleanup_run'
       AND column_name = 'active_slot' AND column_type = 'tinyint unsigned'
       AND is_nullable = 'YES' AND extra LIKE '%STORED GENERATED%'
       AND LOWER(generation_expression) LIKE '%status%running%'
);
SET @v368_cleanup_index_signature := (
    SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':',seq_in_index,':',column_name)
                        ORDER BY index_name,seq_in_index SEPARATOR '|')
      FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'data_retention_detail_cleanup_run'
       AND index_name IN ('PRIMARY','uk_retention_detail_cleanup_run_id',
           'uk_retention_detail_cleanup_active','idx_retention_detail_cleanup_status',
           'idx_retention_detail_cleanup_started')
);
SET @v368_cleanup_index_expected := CONCAT(
    'idx_retention_detail_cleanup_started:1:1:started_at|',
    'idx_retention_detail_cleanup_status:1:1:status|idx_retention_detail_cleanup_status:1:2:finished_at|',
    'PRIMARY:0:1:id|uk_retention_detail_cleanup_active:0:1:active_slot|',
    'uk_retention_detail_cleanup_run_id:0:1:cleanup_run_id'
);
SET @v368_cleanup_core_definition_count := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'data_retention_detail_cleanup_run'
       AND (
         (column_name='id' AND column_type='bigint unsigned' AND is_nullable='NO' AND extra LIKE '%auto_increment%') OR
         (column_name='cleanup_run_id' AND column_type='char(36)' AND character_set_name='ascii' AND collation_name='ascii_bin' AND is_nullable='NO') OR
         (column_name='trigger_source' AND column_type='varchar(16)' AND character_set_name='ascii' AND collation_name='ascii_bin' AND is_nullable='NO') OR
         (column_name='status' AND column_type='varchar(16)' AND character_set_name='ascii' AND collation_name='ascii_bin' AND is_nullable='NO') OR
         (column_name='application_version' AND column_type='varchar(64)' AND character_set_name='ascii' AND collation_name='ascii_bin' AND is_nullable='NO') OR
         (column_name IN ('retention_cutoff_at','started_at','heartbeat_at') AND column_type='datetime(3)' AND is_nullable='NO') OR
         (column_name='finished_at' AND column_type='datetime(3)' AND is_nullable='YES') OR
         (column_name IN ('candidate_operation_count','purged_operation_count','skipped_operation_count',
                          'warning_candidate_count','purged_operationless_run_count','purged_attempt_count','purged_item_count')
            AND column_type='bigint unsigned' AND is_nullable='NO' AND column_default='0') OR
         (column_name IN ('eligible_backlog_attempt_count','eligible_backlog_item_count','retention_table_bytes','projected_five_year_bytes')
            AND column_type='bigint unsigned' AND is_nullable='YES') OR
         (column_name='oldest_unpurged_anchor_at' AND column_type='datetime(3)' AND is_nullable='YES') OR
         (column_name='last_error_code' AND column_type='varchar(64)' AND character_set_name='ascii' AND collation_name='ascii_bin' AND is_nullable='YES') OR
         (column_name='last_error' AND column_type='varchar(2000)' AND is_nullable='YES') OR
         (column_name IN ('created_at','updated_at') AND column_type='datetime(3)' AND is_nullable='NO')
       )
);
SET @v368_sql := IF(
    @v368_cleanup_column_count = 26
    AND @v368_cleanup_core_definition_count = 25
    AND @v368_cleanup_active_definition = 1
    AND @v368_cleanup_index_count = 5
    AND @v368_cleanup_index_signature = @v368_cleanup_index_expected
    AND @v368_cleanup_check_count = 4,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V368 cleanup run schema validation failed'''
);
PREPARE v368_stmt FROM @v368_sql;
EXECUTE v368_stmt;
DEALLOCATE PREPARE v368_stmt;
