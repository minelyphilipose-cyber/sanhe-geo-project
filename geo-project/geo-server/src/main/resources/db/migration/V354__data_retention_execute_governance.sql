-- WP2 data-retention execute governance foundation.
-- This migration creates durable operation identities and markers while keeping every
-- retention execute switch disabled. MySQL DDL implicitly commits, so each statement
-- is guarded for Flyway repair/re-migrate after a partially completed execution.

-- -----------------------------------------------------------------------------
-- 1. Extend data_retention_run.
-- -----------------------------------------------------------------------------

SET @v354_run_column_count := (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'data_retention_run'
       AND column_name IN (
           'trigger_id', 'trigger_source', 'requested_by', 'request_reason',
           'approval_ref', 'ticket_ref', 'scope_json', 'config_fingerprint',
           'config_fingerprint_schema_version', 'policy_version', 'application_version',
           'attempt_count', 'succeeded_attempt_count', 'failed_attempt_count',
           'skipped_attempt_count', 'unknown_attempt_count',
           'attempt_rollup_completed_at', 'attempt_detail_purged_at'
       )
);
SET @v354_sql := CASE
    WHEN @v354_run_column_count = 0 THEN
        'ALTER TABLE data_retention_run
           ADD COLUMN trigger_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
           ADD COLUMN trigger_source VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
           ADD COLUMN requested_by BIGINT NULL,
           ADD COLUMN request_reason VARCHAR(500) NULL,
           ADD COLUMN approval_ref VARCHAR(128) NULL,
           ADD COLUMN ticket_ref VARCHAR(128) NULL,
           ADD COLUMN scope_json JSON NULL,
           ADD COLUMN config_fingerprint BINARY(32) NULL,
           ADD COLUMN config_fingerprint_schema_version SMALLINT UNSIGNED NULL,
           ADD COLUMN policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
           ADD COLUMN application_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
           ADD COLUMN attempt_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
           ADD COLUMN succeeded_attempt_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
           ADD COLUMN failed_attempt_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
           ADD COLUMN skipped_attempt_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
           ADD COLUMN unknown_attempt_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
           ADD COLUMN attempt_rollup_completed_at DATETIME(3) NULL,
           ADD COLUMN attempt_detail_purged_at DATETIME(3) NULL'
    WHEN @v354_run_column_count = 18 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 data_retention_run columns are partially defined'''
END;
PREPARE v354_run_columns_stmt FROM @v354_sql;
EXECUTE v354_run_columns_stmt;
DEALLOCATE PREPARE v354_run_columns_stmt;

SET @v354_run_columns_ready := (
    SELECT COUNT(*) = 18
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'data_retention_run'
       AND (
           (column_name = 'trigger_id' AND column_type = 'char(36)' AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND is_nullable = 'YES') OR
           (column_name = 'trigger_source' AND column_type = 'varchar(24)' AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND is_nullable = 'YES') OR
           (column_name = 'requested_by' AND column_type = 'bigint' AND is_nullable = 'YES') OR
           (column_name = 'request_reason' AND column_type = 'varchar(500)' AND is_nullable = 'YES') OR
           (column_name = 'approval_ref' AND column_type = 'varchar(128)' AND is_nullable = 'YES') OR
           (column_name = 'ticket_ref' AND column_type = 'varchar(128)' AND is_nullable = 'YES') OR
           (column_name = 'scope_json' AND data_type = 'json' AND is_nullable = 'YES') OR
           (column_name = 'config_fingerprint' AND column_type = 'binary(32)' AND is_nullable = 'YES') OR
           (column_name = 'config_fingerprint_schema_version' AND column_type = 'smallint unsigned' AND is_nullable = 'YES') OR
           (column_name = 'policy_version' AND column_type = 'varchar(64)' AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND is_nullable = 'YES') OR
           (column_name = 'application_version' AND column_type = 'varchar(64)' AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND is_nullable = 'YES') OR
           (column_name IN ('attempt_count', 'succeeded_attempt_count', 'failed_attempt_count', 'skipped_attempt_count', 'unknown_attempt_count') AND column_type = 'bigint unsigned' AND is_nullable = 'NO' AND column_default = '0') OR
           (column_name IN ('attempt_rollup_completed_at', 'attempt_detail_purged_at') AND column_type = 'datetime(3)' AND is_nullable = 'YES')
       )
);
SET @v354_sql := IF(
    @v354_run_columns_ready,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 data_retention_run column definition mismatch'''
);
PREPARE v354_run_columns_validate_stmt FROM @v354_sql;
EXECUTE v354_run_columns_validate_stmt;
DEALLOCATE PREPARE v354_run_columns_validate_stmt;

SET @v354_run_index_count := (
    SELECT COUNT(DISTINCT index_name)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'data_retention_run'
       AND index_name IN (
           'uk_retention_run_trigger_domain', 'uk_retention_run_id_domain',
           'uk_retention_run_execution_contract', 'idx_retention_run_source_started'
       )
);
SET @v354_sql := CASE
    WHEN @v354_run_index_count = 0 THEN
        'ALTER TABLE data_retention_run
           ADD UNIQUE KEY uk_retention_run_trigger_domain (trigger_id, domain),
           ADD UNIQUE KEY uk_retention_run_id_domain (id, domain),
           ADD UNIQUE KEY uk_retention_run_execution_contract (id, domain, config_fingerprint_schema_version, config_fingerprint, policy_version),
           ADD KEY idx_retention_run_source_started (trigger_source, started_at)'
    WHEN @v354_run_index_count = 4 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 data_retention_run indexes are partially defined'''
END;
PREPARE v354_run_indexes_stmt FROM @v354_sql;
EXECUTE v354_run_indexes_stmt;
DEALLOCATE PREPARE v354_run_indexes_stmt;

SET @v354_run_index_signature := (
    SELECT GROUP_CONCAT(
               CONCAT(index_name, ':', non_unique, ':', seq_in_index, ':', column_name)
               ORDER BY index_name, seq_in_index SEPARATOR '|'
           )
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'data_retention_run'
       AND index_name IN (
           'uk_retention_run_trigger_domain', 'uk_retention_run_id_domain',
           'uk_retention_run_execution_contract', 'idx_retention_run_source_started'
       )
);
SET @v354_run_index_expected := CONCAT(
    'idx_retention_run_source_started:1:1:trigger_source|idx_retention_run_source_started:1:2:started_at|',
    'uk_retention_run_execution_contract:0:1:id|uk_retention_run_execution_contract:0:2:domain|uk_retention_run_execution_contract:0:3:config_fingerprint_schema_version|uk_retention_run_execution_contract:0:4:config_fingerprint|uk_retention_run_execution_contract:0:5:policy_version|',
    'uk_retention_run_id_domain:0:1:id|uk_retention_run_id_domain:0:2:domain|',
    'uk_retention_run_trigger_domain:0:1:trigger_id|uk_retention_run_trigger_domain:0:2:domain'
);
SET @v354_sql := IF(
    @v354_run_index_signature = @v354_run_index_expected,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 data_retention_run index definition mismatch'''
);
PREPARE v354_run_indexes_validate_stmt FROM @v354_sql;
EXECUTE v354_run_indexes_validate_stmt;
DEALLOCATE PREPARE v354_run_indexes_validate_stmt;

SET @v354_run_check_count := (
    SELECT COUNT(*)
      FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'data_retention_run'
       AND constraint_type = 'CHECK'
       AND constraint_name IN ('chk_retention_run_scope_size', 'chk_retention_run_fingerprint_pair')
);
SET @v354_sql := CASE
    WHEN @v354_run_check_count = 0 THEN
        'ALTER TABLE data_retention_run
           ADD CONSTRAINT chk_retention_run_scope_size CHECK (scope_json IS NULL OR OCTET_LENGTH(CAST(scope_json AS CHAR)) <= 8192),
           ADD CONSTRAINT chk_retention_run_fingerprint_pair CHECK ((config_fingerprint IS NULL AND config_fingerprint_schema_version IS NULL) OR (config_fingerprint IS NOT NULL AND config_fingerprint_schema_version IS NOT NULL AND config_fingerprint_schema_version >= 1))'
    WHEN @v354_run_check_count = 2 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 data_retention_run checks are partially defined'''
END;
PREPARE v354_run_checks_stmt FROM @v354_sql;
EXECUTE v354_run_checks_stmt;
DEALLOCATE PREPARE v354_run_checks_stmt;

-- -----------------------------------------------------------------------------
-- 2. Durable operation and operation/run relation.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS data_retention_operation (
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    origin_run_group_id BIGINT UNSIGNED NOT NULL,
    domain VARCHAR(64) NOT NULL,
    operation_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    batch_key_hash BINARY(32) NOT NULL,
    hash_schema_version SMALLINT UNSIGNED NOT NULL,
    item_count INT UNSIGNED NOT NULL,
    cutoff_at DATETIME(3) NULL,
    upper_bound_business_key BIGINT UNSIGNED NULL,
    poll_project_id BIGINT NULL,
    poll_batch_date DATE NULL,
    poll_question_tier VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
    poll_retention_cutoff_date DATE NULL,
    config_fingerprint_schema_version SMALLINT UNSIGNED NOT NULL,
    config_fingerprint BINARY(32) NOT NULL,
    policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_application_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    outcome_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    outcome_finalized_at DATETIME(3) NULL,
    outcome_finalization_source VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
    outcome_finalization_reason VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    detail_retention_anchor_at DATETIME(3) NULL,
    detail_purged_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_retention_operation_scope (domain, batch_key_hash),
    UNIQUE KEY uk_retention_operation_identity (operation_id, domain),
    UNIQUE KEY uk_retention_operation_execution_contract
        (operation_id, domain, config_fingerprint_schema_version, config_fingerprint, policy_version),
    UNIQUE KEY uk_retention_operation_website_contract
        (operation_id, domain, batch_key_hash, hash_schema_version, item_count, cutoff_at,
         upper_bound_business_key, config_fingerprint_schema_version, config_fingerprint, policy_version),
    UNIQUE KEY uk_retention_operation_poll_scope
        (operation_id, domain, poll_project_id, poll_batch_date, poll_question_tier),
    KEY idx_retention_operation_origin_run (origin_run_group_id, created_at),
    KEY idx_retention_operation_status (domain, outcome_status, created_at),
    KEY idx_retention_operation_detail_retention
        (detail_purged_at, detail_retention_anchor_at, outcome_status, operation_id),
    KEY idx_retention_operation_origin_domain (origin_run_group_id, domain),
    CONSTRAINT fk_retention_operation_origin_run_domain
        FOREIGN KEY (origin_run_group_id, domain)
        REFERENCES data_retention_run (id, domain) ON DELETE RESTRICT,
    CONSTRAINT chk_retention_operation_item_count CHECK (
        (operation_type = 'website_batch' AND item_count BETWEEN 1 AND 20)
        OR (operation_type = 'poll_slice' AND item_count = 1)
    ),
    CONSTRAINT chk_retention_operation_hash_version CHECK (hash_schema_version >= 1),
    CONSTRAINT chk_retention_operation_fingerprint_version CHECK (config_fingerprint_schema_version >= 1),
    CONSTRAINT chk_retention_operation_outcome_status CHECK (
        outcome_status IN ('pending','committed','rolled_back','unknown','manual_review','closed_no_mutation')
    ),
    CONSTRAINT chk_retention_operation_finalization CHECK (
        (outcome_status IN ('committed','closed_no_mutation')
         AND outcome_finalized_at IS NOT NULL
         AND outcome_finalization_source IS NOT NULL
         AND outcome_finalization_reason IS NOT NULL
         AND detail_retention_anchor_at IS NOT NULL
         AND detail_retention_anchor_at >= outcome_finalized_at)
        OR
        (outcome_status NOT IN ('committed','closed_no_mutation')
         AND outcome_finalized_at IS NULL
         AND outcome_finalization_source IS NULL
         AND outcome_finalization_reason IS NULL
         AND detail_retention_anchor_at IS NULL
         AND detail_purged_at IS NULL)
    ),
    CONSTRAINT chk_retention_operation_finalization_source CHECK (
        outcome_finalization_source IS NULL
        OR outcome_finalization_source IN ('website_commit','poll_marker','reconciliation','terminal_attempt')
    ),
    CONSTRAINT chk_retention_operation_detail_purge_age CHECK (
        detail_purged_at IS NULL
        OR detail_purged_at >= TIMESTAMPADD(DAY, 365, detail_retention_anchor_at)
    ),
    CONSTRAINT chk_retention_operation_domain_type CHECK (
        (operation_type = 'website_batch' AND domain = 'website_published_content')
        OR (operation_type = 'poll_slice' AND domain = 'poll_results')
    ),
    CONSTRAINT chk_retention_operation_scope_shape CHECK (
        (operation_type = 'website_batch'
         AND cutoff_at IS NOT NULL
         AND upper_bound_business_key IS NOT NULL
         AND poll_project_id IS NULL
         AND poll_batch_date IS NULL
         AND poll_question_tier IS NULL
         AND poll_retention_cutoff_date IS NULL)
        OR
        (operation_type = 'poll_slice'
         AND cutoff_at IS NULL
         AND upper_bound_business_key IS NULL
         AND poll_project_id IS NOT NULL
         AND poll_batch_date IS NOT NULL
         AND poll_question_tier IS NOT NULL
         AND poll_retention_cutoff_date IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='durable logical identities for retention mutations';

SET @v354_operation_ready := (
    SELECT COUNT(*) = 25
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'data_retention_operation'
);
SET @v354_sql := IF(
    @v354_operation_ready,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 data_retention_operation definition mismatch'''
);
PREPARE v354_operation_validate_stmt FROM @v354_sql;
EXECUTE v354_operation_validate_stmt;
DEALLOCATE PREPARE v354_operation_validate_stmt;

CREATE TABLE IF NOT EXISTS data_retention_operation_run (
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    run_group_id BIGINT UNSIGNED NOT NULL,
    domain VARCHAR(64) NOT NULL,
    config_fingerprint_schema_version SMALLINT UNSIGNED NOT NULL,
    config_fingerprint BINARY(32) NOT NULL,
    policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    relation_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    origin_operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (
            CASE WHEN relation_type = 'origin' THEN operation_id ELSE NULL END
        ) STORED,
    linked_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (operation_id, run_group_id),
    UNIQUE KEY uk_retention_operation_run_identity (operation_id, run_group_id, domain),
    UNIQUE KEY uk_retention_operation_run_origin_once (origin_operation_id),
    KEY idx_retention_operation_run_run_domain
        (run_group_id, domain, config_fingerprint_schema_version, config_fingerprint, policy_version),
    KEY idx_retention_operation_run_operation_domain
        (operation_id, domain, config_fingerprint_schema_version, config_fingerprint, policy_version),
    CONSTRAINT fk_retention_operation_run_operation_contract
        FOREIGN KEY (operation_id, domain, config_fingerprint_schema_version, config_fingerprint, policy_version)
        REFERENCES data_retention_operation
            (operation_id, domain, config_fingerprint_schema_version, config_fingerprint, policy_version)
        ON DELETE RESTRICT,
    CONSTRAINT fk_retention_operation_run_run_contract
        FOREIGN KEY (run_group_id, domain, config_fingerprint_schema_version, config_fingerprint, policy_version)
        REFERENCES data_retention_run
            (id, domain, config_fingerprint_schema_version, config_fingerprint, policy_version)
        ON DELETE RESTRICT,
    CONSTRAINT chk_retention_operation_run_fingerprint_version CHECK (config_fingerprint_schema_version >= 1),
    CONSTRAINT chk_retention_operation_run_relation_type CHECK (relation_type IN ('origin','retry','reconcile'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='durable many-run relation for one retention operation';

SET @v354_operation_run_ready := (
    SELECT COUNT(*) = 10
       AND SUM(column_name = 'origin_operation_id' AND extra LIKE '%STORED GENERATED%') = 1
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'data_retention_operation_run'
);
SET @v354_sql := IF(
    @v354_operation_run_ready,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 data_retention_operation_run definition mismatch'''
);
PREPARE v354_operation_run_validate_stmt FROM @v354_sql;
EXECUTE v354_operation_run_validate_stmt;
DEALLOCATE PREPARE v354_operation_run_validate_stmt;

-- -----------------------------------------------------------------------------
-- 3. Mutation attempt, item, and durable website commit marker.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS data_retention_batch_attempt (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    run_group_id BIGINT UNSIGNED NOT NULL,
    parent_attempt_id BIGINT UNSIGNED NULL,
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    sequence_no INT UNSIGNED NOT NULL,
    attempt_no INT UNSIGNED NOT NULL,
    domain VARCHAR(64) NOT NULL,
    attempt_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_json JSON NULL,
    metrics_json JSON NULL,
    last_error VARCHAR(2000) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_retention_attempt_run_sequence (run_group_id, sequence_no),
    UNIQUE KEY uk_retention_attempt_operation_no (operation_id, attempt_no),
    UNIQUE KEY uk_retention_attempt_operation_id (operation_id, run_group_id, domain, id),
    KEY idx_retention_attempt_run_status (run_group_id, status, created_at),
    KEY idx_retention_attempt_run_domain (run_group_id, domain),
    KEY idx_retention_attempt_parent (parent_attempt_id),
    KEY idx_retention_attempt_operation (operation_id, created_at),
    KEY idx_retention_attempt_operation_identity (operation_id, run_group_id, domain),
    CONSTRAINT fk_retention_attempt_run_domain
        FOREIGN KEY (run_group_id, domain)
        REFERENCES data_retention_run (id, domain) ON DELETE RESTRICT,
    CONSTRAINT fk_retention_attempt_operation_identity
        FOREIGN KEY (operation_id, run_group_id, domain)
        REFERENCES data_retention_operation_run (operation_id, run_group_id, domain) ON DELETE RESTRICT,
    CONSTRAINT chk_retention_attempt_sequence CHECK (sequence_no >= 1),
    CONSTRAINT chk_retention_attempt_number CHECK (attempt_no >= 1),
    CONSTRAINT chk_retention_attempt_scope_size CHECK (
        scope_json IS NULL OR OCTET_LENGTH(CAST(scope_json AS CHAR)) <= 8192
    ),
    CONSTRAINT chk_retention_attempt_metrics_size CHECK (
        metrics_json IS NULL OR OCTET_LENGTH(CAST(metrics_json AS CHAR)) <= 8192
    ),
    CONSTRAINT chk_retention_attempt_error_size CHECK (
        last_error IS NULL OR OCTET_LENGTH(last_error) <= 2048
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='365-day mutation attempt detail for retention operations';

CREATE TABLE IF NOT EXISTS data_retention_batch_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    run_group_id BIGINT UNSIGNED NOT NULL,
    first_attempt_id BIGINT UNSIGNED NOT NULL,
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    domain VARCHAR(64) NOT NULL,
    business_key BIGINT UNSIGNED NOT NULL,
    sequence_no SMALLINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_retention_batch_item_key (operation_id, business_key),
    UNIQUE KEY uk_retention_batch_item_sequence (operation_id, sequence_no),
    KEY idx_retention_batch_item_run (run_group_id, created_at),
    KEY idx_retention_batch_item_attempt (operation_id, run_group_id, domain, first_attempt_id),
    KEY idx_retention_batch_item_business (domain, business_key),
    CONSTRAINT fk_retention_batch_item_operation_identity
        FOREIGN KEY (operation_id, run_group_id, domain)
        REFERENCES data_retention_operation_run (operation_id, run_group_id, domain) ON DELETE RESTRICT,
    CONSTRAINT fk_retention_batch_item_attempt
        FOREIGN KEY (operation_id, run_group_id, domain, first_attempt_id)
        REFERENCES data_retention_batch_attempt (operation_id, run_group_id, domain, id) ON DELETE RESTRICT,
    CONSTRAINT chk_retention_batch_item_sequence CHECK (sequence_no BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='canonical website business keys for one retention operation';

CREATE TABLE IF NOT EXISTS data_retention_batch_commit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    run_group_id BIGINT UNSIGNED NOT NULL,
    domain VARCHAR(64) NOT NULL,
    batch_key_hash BINARY(32) NOT NULL,
    hash_schema_version SMALLINT UNSIGNED NOT NULL,
    item_count INT UNSIGNED NOT NULL,
    cutoff_at DATETIME(3) NOT NULL,
    upper_bound_business_key BIGINT UNSIGNED NOT NULL,
    config_fingerprint_schema_version SMALLINT UNSIGNED NOT NULL,
    config_fingerprint BINARY(32) NOT NULL,
    policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    committed_application_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    committed_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_retention_batch_commit_operation (operation_id),
    UNIQUE KEY uk_retention_batch_commit_scope (domain, batch_key_hash),
    KEY idx_retention_batch_commit_run (run_group_id, committed_at),
    KEY idx_retention_batch_commit_operation_run (operation_id, run_group_id, domain),
    KEY idx_retention_batch_commit_contract
        (operation_id, domain, batch_key_hash, hash_schema_version, item_count, cutoff_at,
         upper_bound_business_key, config_fingerprint_schema_version, config_fingerprint, policy_version),
    CONSTRAINT fk_retention_batch_commit_operation_run
        FOREIGN KEY (operation_id, run_group_id, domain)
        REFERENCES data_retention_operation_run (operation_id, run_group_id, domain) ON DELETE RESTRICT,
    CONSTRAINT fk_retention_batch_commit_contract
        FOREIGN KEY (operation_id, domain, batch_key_hash, hash_schema_version, item_count, cutoff_at,
                     upper_bound_business_key, config_fingerprint_schema_version, config_fingerprint, policy_version)
        REFERENCES data_retention_operation
            (operation_id, domain, batch_key_hash, hash_schema_version, item_count, cutoff_at,
             upper_bound_business_key, config_fingerprint_schema_version, config_fingerprint, policy_version)
        ON DELETE RESTRICT,
    CONSTRAINT chk_retention_batch_commit_item_count CHECK (item_count BETWEEN 1 AND 20),
    CONSTRAINT chk_retention_batch_commit_hash_version CHECK (hash_schema_version >= 1),
    CONSTRAINT chk_retention_batch_commit_fingerprint_version CHECK (config_fingerprint_schema_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='durable commit marker for website retention batches';

SET @v354_detail_tables_ready := (
    SELECT COUNT(*) = 3
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN (
           'data_retention_batch_attempt', 'data_retention_batch_item', 'data_retention_batch_commit'
       )
       AND engine = 'InnoDB'
);
SET @v354_sql := IF(
    @v354_detail_tables_ready,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 mutation detail table definition mismatch'''
);
PREPARE v354_detail_tables_validate_stmt FROM @v354_sql;
EXECUTE v354_detail_tables_validate_stmt;
DEALLOCATE PREPARE v354_detail_tables_validate_stmt;

-- -----------------------------------------------------------------------------
-- 4. Durable failure-group governance.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS data_retention_failure_group (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    domain VARCHAR(64) NOT NULL,
    source_run_group_id BIGINT UNSIGNED NOT NULL,
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_count INT UNSIGNED NOT NULL,
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    last_error VARCHAR(2000) NULL,
    opened_at DATETIME(3) NOT NULL,
    closed_at DATETIME(3) NULL,
    closed_by BIGINT NULL,
    close_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_retention_failure_group_id_domain (id, domain),
    KEY idx_retention_failure_group_status (domain, status, opened_at),
    KEY idx_retention_failure_group_run (source_run_group_id, opened_at),
    KEY idx_retention_failure_group_operation (operation_id, source_run_group_id, domain),
    CONSTRAINT fk_retention_failure_group_operation
        FOREIGN KEY (operation_id, source_run_group_id, domain)
        REFERENCES data_retention_operation_run (operation_id, run_group_id, domain) ON DELETE RESTRICT,
    CONSTRAINT chk_retention_failure_group_item_count CHECK (item_count BETWEEN 1 AND 5),
    CONSTRAINT chk_retention_failure_group_error_size CHECK (
        last_error IS NULL OR OCTET_LENGTH(last_error) <= 2048
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='durable failure groups for website retention operations';

CREATE TABLE IF NOT EXISTS data_retention_failure_group_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    group_id BIGINT UNSIGNED NOT NULL,
    domain VARCHAR(64) NOT NULL,
    business_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sequence_no SMALLINT UNSIGNED NOT NULL,
    item_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    resolved_at DATETIME(3) NULL,
    resolved_by BIGINT NULL,
    resolution_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_retention_failure_item_key (group_id, domain, business_key),
    UNIQUE KEY uk_retention_failure_item_sequence (group_id, sequence_no),
    UNIQUE KEY uk_retention_failure_item_active_ref (group_id, domain, business_key, id),
    KEY idx_retention_failure_item_business (domain, business_key),
    KEY idx_retention_failure_item_group_domain (group_id, domain),
    CONSTRAINT fk_retention_failure_item_group_domain
        FOREIGN KEY (group_id, domain)
        REFERENCES data_retention_failure_group (id, domain) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='durable members of website retention failure groups';

CREATE TABLE IF NOT EXISTS data_retention_failure_active_key (
    domain VARCHAR(64) NOT NULL,
    business_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    group_id BIGINT UNSIGNED NOT NULL,
    group_item_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (domain, business_key),
    UNIQUE KEY uk_retention_failure_active_item (group_item_id),
    KEY idx_retention_failure_active_ref (group_id, domain, business_key, group_item_id),
    CONSTRAINT fk_retention_failure_active_ref
        FOREIGN KEY (group_id, domain, business_key, group_item_id)
        REFERENCES data_retention_failure_group_item (group_id, domain, business_key, id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='business keys currently held by active retention failure groups';

CREATE TABLE IF NOT EXISTS data_retention_failure_regroup_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    regroup_operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    domain VARCHAR(64) NOT NULL,
    business_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    old_group_id BIGINT UNSIGNED NOT NULL,
    new_group_id BIGINT UNSIGNED NOT NULL,
    operator_user_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    regrouped_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_retention_regroup_operation_key (regroup_operation_id, business_key),
    KEY idx_retention_regroup_old_group (old_group_id, domain, regrouped_at),
    KEY idx_retention_regroup_new_group (new_group_id, domain, regrouped_at),
    CONSTRAINT fk_retention_regroup_old_group
        FOREIGN KEY (old_group_id, domain)
        REFERENCES data_retention_failure_group (id, domain) ON DELETE RESTRICT,
    CONSTRAINT fk_retention_regroup_new_group
        FOREIGN KEY (new_group_id, domain)
        REFERENCES data_retention_failure_group (id, domain) ON DELETE RESTRICT,
    CONSTRAINT chk_retention_regroup_different_group CHECK (old_group_id <> new_group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='durable per-business-key retention failure regroup audit';

SET @v354_failure_tables_ready := (
    SELECT COUNT(*) = 4
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN (
           'data_retention_failure_group', 'data_retention_failure_group_item',
           'data_retention_failure_active_key', 'data_retention_failure_regroup_history'
       )
       AND engine = 'InnoDB'
);
SET @v354_sql := IF(
    @v354_failure_tables_ready,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 failure governance table definition mismatch'''
);
PREPARE v354_failure_tables_validate_stmt FROM @v354_sql;
EXECUTE v354_failure_tables_validate_stmt;
DEALLOCATE PREPARE v354_failure_tables_validate_stmt;

-- -----------------------------------------------------------------------------
-- 5. Dynamic-switch audit and unknown-outcome reconciliation.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS data_retention_dynamic_switch_change (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    change_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    switch_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    enabled TINYINT(1) NOT NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    changed_at DATETIME(3) NOT NULL,
    requested_by BIGINT NOT NULL,
    request_source VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(500) NOT NULL,
    approval_ref VARCHAR(128) NULL,
    approved_by BIGINT NULL,
    approved_at DATETIME(3) NULL,
    ticket_ref VARCHAR(128) NULL,
    redis_observed_version BIGINT UNSIGNED NULL,
    redis_observed_enabled TINYINT(1) NULL,
    last_error VARCHAR(2000) NULL,
    applied_at DATETIME(3) NULL,
    failed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_retention_switch_change_id (change_id),
    UNIQUE KEY uk_retention_switch_version (switch_key, version),
    KEY idx_retention_switch_status (status, created_at),
    KEY idx_retention_switch_key_changed (switch_key, changed_at),
    CONSTRAINT chk_retention_switch_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_retention_switch_observed_enabled CHECK (
        redis_observed_enabled IS NULL OR redis_observed_enabled IN (0, 1)
    ),
    CONSTRAINT chk_retention_switch_error_size CHECK (
        last_error IS NULL OR OCTET_LENGTH(last_error) <= 2048
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='durable audit chain for requested Redis retention switch changes';

CREATE TABLE IF NOT EXISTS data_retention_unknown_outcome_reconciliation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    reconciliation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    run_group_id BIGINT UNSIGNED NOT NULL,
    domain VARCHAR(64) NOT NULL,
    marker_state VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    business_state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    decision VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evidence_hash BINARY(32) NOT NULL,
    evidence_json JSON NULL,
    requested_by BIGINT NOT NULL,
    resolution_reason VARCHAR(500) NULL,
    resolved_by BIGINT NULL,
    resolved_at DATETIME(3) NULL,
    last_error VARCHAR(2000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_retention_reconciliation_id (reconciliation_id),
    KEY idx_retention_reconciliation_operation (operation_id, created_at),
    KEY idx_retention_reconciliation_status (domain, status, created_at),
    KEY idx_retention_reconciliation_identity (operation_id, run_group_id, domain),
    CONSTRAINT fk_retention_reconciliation_identity
        FOREIGN KEY (operation_id, run_group_id, domain)
        REFERENCES data_retention_operation_run (operation_id, run_group_id, domain) ON DELETE RESTRICT,
    CONSTRAINT chk_retention_reconciliation_evidence_size CHECK (
        evidence_json IS NULL OR OCTET_LENGTH(CAST(evidence_json AS CHAR)) <= 8192
    ),
    CONSTRAINT chk_retention_reconciliation_error_size CHECK (
        last_error IS NULL OR OCTET_LENGTH(last_error) <= 2048
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='durable unknown-outcome reconciliation evidence';

SET @v354_audit_tables_ready := (
    SELECT COUNT(*) = 2
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN (
           'data_retention_dynamic_switch_change',
           'data_retention_unknown_outcome_reconciliation'
       )
       AND engine = 'InnoDB'
);
SET @v354_sql := IF(
    @v354_audit_tables_ready,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 audit governance table definition mismatch'''
);
PREPARE v354_audit_tables_validate_stmt FROM @v354_sql;
EXECUTE v354_audit_tables_validate_stmt;
DEALLOCATE PREPARE v354_audit_tables_validate_stmt;

-- -----------------------------------------------------------------------------
-- 6. Link the existing poll marker and purge audit to durable operation identity.
-- -----------------------------------------------------------------------------

SET @v354_purged_slice_operation_column_count := (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'data_retention_purged_slice'
       AND column_name = 'operation_id'
);
SET @v354_sql := CASE
    WHEN @v354_purged_slice_operation_column_count = 0 THEN
        'ALTER TABLE data_retention_purged_slice
           ADD COLUMN operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL'
    WHEN @v354_purged_slice_operation_column_count = 1 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 purged-slice operation column is ambiguous'''
END;
PREPARE v354_purged_slice_column_stmt FROM @v354_sql;
EXECUTE v354_purged_slice_column_stmt;
DEALLOCATE PREPARE v354_purged_slice_column_stmt;

SET @v354_purged_slice_column_ready := (
    SELECT COUNT(*) = 1
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'data_retention_purged_slice'
       AND column_name = 'operation_id'
       AND column_type = 'char(36)'
       AND character_set_name = 'ascii'
       AND collation_name = 'ascii_bin'
       AND is_nullable = 'YES'
);
SET @v354_sql := IF(
    @v354_purged_slice_column_ready,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 purged-slice operation column definition mismatch'''
);
PREPARE v354_purged_slice_column_validate_stmt FROM @v354_sql;
EXECUTE v354_purged_slice_column_validate_stmt;
DEALLOCATE PREPARE v354_purged_slice_column_validate_stmt;

SET @v354_purged_slice_index_count := (
    SELECT COUNT(DISTINCT index_name)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'data_retention_purged_slice'
       AND index_name IN (
           'uk_retention_purged_slice_operation',
           'idx_retention_purged_slice_operation_run',
           'idx_retention_purged_slice_operation_scope'
       )
);
SET @v354_sql := CASE
    WHEN @v354_purged_slice_index_count = 0 THEN
        'ALTER TABLE data_retention_purged_slice
           ADD UNIQUE KEY uk_retention_purged_slice_operation (operation_id),
           ADD KEY idx_retention_purged_slice_operation_run (operation_id, retention_run_id, domain),
           ADD KEY idx_retention_purged_slice_operation_scope (operation_id, domain, project_id, batch_date, question_tier)'
    WHEN @v354_purged_slice_index_count = 3 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 purged-slice indexes are partially defined'''
END;
PREPARE v354_purged_slice_indexes_stmt FROM @v354_sql;
EXECUTE v354_purged_slice_indexes_stmt;
DEALLOCATE PREPARE v354_purged_slice_indexes_stmt;

SET @v354_purged_slice_fk_count := (
    SELECT COUNT(*)
      FROM information_schema.referential_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'data_retention_purged_slice'
       AND constraint_name IN (
           'fk_retention_purged_slice_operation_run',
           'fk_retention_purged_slice_operation_scope'
       )
);
SET @v354_sql := CASE
    WHEN @v354_purged_slice_fk_count = 0 THEN
        'ALTER TABLE data_retention_purged_slice
           ADD CONSTRAINT fk_retention_purged_slice_operation_run
             FOREIGN KEY (operation_id, retention_run_id, domain)
             REFERENCES data_retention_operation_run (operation_id, run_group_id, domain)
             ON DELETE RESTRICT,
           ADD CONSTRAINT fk_retention_purged_slice_operation_scope
             FOREIGN KEY (operation_id, domain, project_id, batch_date, question_tier)
             REFERENCES data_retention_operation
               (operation_id, domain, poll_project_id, poll_batch_date, poll_question_tier)
             ON DELETE RESTRICT'
    WHEN @v354_purged_slice_fk_count = 2 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 purged-slice foreign keys are partially defined'''
END;
PREPARE v354_purged_slice_fks_stmt FROM @v354_sql;
EXECUTE v354_purged_slice_fks_stmt;
DEALLOCATE PREPARE v354_purged_slice_fks_stmt;

SET @v354_purged_slice_check_count := (
    SELECT COUNT(*)
      FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'data_retention_purged_slice'
       AND constraint_type = 'CHECK'
       AND constraint_name = 'chk_retention_purged_slice_operation_run'
);
SET @v354_sql := CASE
    WHEN @v354_purged_slice_check_count = 0 THEN
        'ALTER TABLE data_retention_purged_slice
           ADD CONSTRAINT chk_retention_purged_slice_operation_run
             CHECK (operation_id IS NULL OR retention_run_id IS NOT NULL)'
    WHEN @v354_purged_slice_check_count = 1 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 purged-slice check is ambiguous'''
END;
PREPARE v354_purged_slice_check_stmt FROM @v354_sql;
EXECUTE v354_purged_slice_check_stmt;
DEALLOCATE PREPARE v354_purged_slice_check_stmt;

SET @v354_poll_audit_column_count := (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'poll_audit_purge_runs'
       AND column_name IN ('retention_run_id', 'operation_id', 'retention_domain')
);
SET @v354_sql := CASE
    WHEN @v354_poll_audit_column_count = 0 THEN
        'ALTER TABLE poll_audit_purge_runs
           ADD COLUMN retention_run_id BIGINT UNSIGNED NULL,
           ADD COLUMN operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
           ADD COLUMN retention_domain VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL'
    WHEN @v354_poll_audit_column_count = 3 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 poll audit columns are partially defined'''
END;
PREPARE v354_poll_audit_columns_stmt FROM @v354_sql;
EXECUTE v354_poll_audit_columns_stmt;
DEALLOCATE PREPARE v354_poll_audit_columns_stmt;

SET @v354_poll_audit_columns_ready := (
    SELECT COUNT(*) = 3
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'poll_audit_purge_runs'
       AND (
           (column_name = 'retention_run_id' AND column_type = 'bigint unsigned' AND is_nullable = 'YES') OR
           (column_name = 'operation_id' AND column_type = 'char(36)' AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' AND is_nullable = 'YES') OR
           (column_name = 'retention_domain' AND column_type = 'varchar(64)' AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci' AND is_nullable = 'YES')
       )
);
SET @v354_sql := IF(
    @v354_poll_audit_columns_ready,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 poll audit column definition mismatch'''
);
PREPARE v354_poll_audit_columns_validate_stmt FROM @v354_sql;
EXECUTE v354_poll_audit_columns_validate_stmt;
DEALLOCATE PREPARE v354_poll_audit_columns_validate_stmt;

SET @v354_poll_audit_index_count := (
    SELECT COUNT(DISTINCT index_name)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'poll_audit_purge_runs'
       AND index_name IN ('idx_poll_audit_purge_retention_run', 'idx_poll_audit_purge_operation')
);
SET @v354_sql := CASE
    WHEN @v354_poll_audit_index_count = 0 THEN
        'ALTER TABLE poll_audit_purge_runs
           ADD KEY idx_poll_audit_purge_retention_run (retention_run_id, created_at),
           ADD KEY idx_poll_audit_purge_operation (operation_id, retention_run_id, retention_domain, created_at)'
    WHEN @v354_poll_audit_index_count = 2 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 poll audit indexes are partially defined'''
END;
PREPARE v354_poll_audit_indexes_stmt FROM @v354_sql;
EXECUTE v354_poll_audit_indexes_stmt;
DEALLOCATE PREPARE v354_poll_audit_indexes_stmt;

SET @v354_poll_audit_fk_count := (
    SELECT COUNT(*)
      FROM information_schema.referential_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'poll_audit_purge_runs'
       AND constraint_name = 'fk_poll_audit_purge_operation'
);
SET @v354_sql := CASE
    WHEN @v354_poll_audit_fk_count = 0 THEN
        'ALTER TABLE poll_audit_purge_runs
           ADD CONSTRAINT fk_poll_audit_purge_operation
             FOREIGN KEY (operation_id, retention_run_id, retention_domain)
             REFERENCES data_retention_operation_run (operation_id, run_group_id, domain)
             ON DELETE RESTRICT'
    WHEN @v354_poll_audit_fk_count = 1 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 poll audit foreign key is ambiguous'''
END;
PREPARE v354_poll_audit_fk_stmt FROM @v354_sql;
EXECUTE v354_poll_audit_fk_stmt;
DEALLOCATE PREPARE v354_poll_audit_fk_stmt;

SET @v354_poll_audit_check_count := (
    SELECT COUNT(*)
      FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'poll_audit_purge_runs'
       AND constraint_type = 'CHECK'
       AND constraint_name = 'chk_poll_audit_purge_retention_identity'
);
SET @v354_sql := CASE
    WHEN @v354_poll_audit_check_count = 0 THEN
        'ALTER TABLE poll_audit_purge_runs
           ADD CONSTRAINT chk_poll_audit_purge_retention_identity CHECK (
             (retention_run_id IS NULL AND operation_id IS NULL AND retention_domain IS NULL)
             OR
             (retention_run_id IS NOT NULL AND operation_id IS NOT NULL AND retention_domain IS NOT NULL)
           )'
    WHEN @v354_poll_audit_check_count = 1 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 poll audit check is ambiguous'''
END;
PREPARE v354_poll_audit_check_stmt FROM @v354_sql;
EXECUTE v354_poll_audit_check_stmt;
DEALLOCATE PREPARE v354_poll_audit_check_stmt;

-- Final migration assertions. These catch incompatible pre-existing target objects
-- instead of accepting CREATE TABLE IF NOT EXISTS warnings silently.
SET @v354_new_table_count := (
    SELECT COUNT(*)
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name IN (
           'data_retention_operation', 'data_retention_operation_run',
           'data_retention_batch_attempt', 'data_retention_batch_item', 'data_retention_batch_commit',
           'data_retention_failure_group', 'data_retention_failure_group_item',
           'data_retention_failure_active_key', 'data_retention_failure_regroup_history',
           'data_retention_dynamic_switch_change',
           'data_retention_unknown_outcome_reconciliation'
       )
       AND engine = 'InnoDB'
);
SET @v354_required_fk_count := (
    SELECT COUNT(*)
      FROM information_schema.referential_constraints
     WHERE constraint_schema = DATABASE()
       AND constraint_name IN (
           'fk_retention_operation_origin_run_domain',
           'fk_retention_operation_run_operation_contract',
           'fk_retention_operation_run_run_contract',
           'fk_retention_attempt_run_domain',
           'fk_retention_attempt_operation_identity',
           'fk_retention_batch_item_operation_identity',
           'fk_retention_batch_item_attempt',
           'fk_retention_batch_commit_operation_run',
           'fk_retention_batch_commit_contract',
           'fk_retention_failure_group_operation',
           'fk_retention_failure_item_group_domain',
           'fk_retention_failure_active_ref',
           'fk_retention_regroup_old_group',
           'fk_retention_regroup_new_group',
           'fk_retention_reconciliation_identity',
           'fk_retention_purged_slice_operation_run',
           'fk_retention_purged_slice_operation_scope',
           'fk_poll_audit_purge_operation'
       )
       AND delete_rule = 'RESTRICT'
);
SET @v354_sql := IF(
    @v354_new_table_count = 11 AND @v354_required_fk_count = 18,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V354 final governance schema validation failed'''
);
PREPARE v354_final_validate_stmt FROM @v354_sql;
EXECUTE v354_final_validate_stmt;
DEALLOCATE PREPARE v354_final_validate_stmt;

-- Exact recovery guards. The earlier count checks make partial repair failures easy to
-- diagnose; these canonical SHA-256 signatures additionally reject same-shape objects
-- whose types/defaults, index order, FK targets/rules, or CHECK expressions differ.
SET @v354_previous_group_concat_max_len := @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 1048576;
SET @v354_new_tables := CONCAT(
    'data_retention_operation,data_retention_operation_run,',
    'data_retention_batch_attempt,data_retention_batch_item,data_retention_batch_commit,',
    'data_retention_failure_group,data_retention_failure_group_item,',
    'data_retention_failure_active_key,data_retention_failure_regroup_history,',
    'data_retention_dynamic_switch_change,data_retention_unknown_outcome_reconciliation'
);

SET @v354_table_signature := (
    SELECT SHA2(GROUP_CONCAT(
               CONCAT_WS(CHAR(31), table_name, engine, table_collation,
                         create_options, table_comment)
               ORDER BY table_name SEPARATOR '\n'
           ), 256)
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND FIND_IN_SET(table_name, @v354_new_tables)
);

SET @v354_column_signature := (
    SELECT SHA2(GROUP_CONCAT(
               CONCAT_WS(
                   CHAR(31), table_name, ordinal_position, column_name,
                   LOWER(column_type), is_nullable,
                   COALESCE(LOWER(CAST(column_default AS CHAR)), '<null>'),
                   LOWER(extra), COALESCE(character_set_name, '<null>'),
                   COALESCE(collation_name, '<null>'),
                   LOWER(REGEXP_REPLACE(
                       REPLACE(COALESCE(generation_expression, '<null>'), '`', ''),
                       '[[:space:]]+', ''))
               )
               ORDER BY table_name, ordinal_position SEPARATOR '\n'
           ), 256)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND (
           FIND_IN_SET(table_name, @v354_new_tables)
           OR (table_name = 'data_retention_run' AND FIND_IN_SET(column_name, CONCAT(
               'trigger_id,trigger_source,requested_by,request_reason,approval_ref,ticket_ref,',
               'scope_json,config_fingerprint,config_fingerprint_schema_version,policy_version,',
               'application_version,attempt_count,succeeded_attempt_count,failed_attempt_count,',
               'skipped_attempt_count,unknown_attempt_count,attempt_rollup_completed_at,',
               'attempt_detail_purged_at')))
           OR (table_name = 'data_retention_purged_slice' AND column_name = 'operation_id')
           OR (table_name = 'poll_audit_purge_runs'
               AND FIND_IN_SET(column_name,
                   'retention_run_id,operation_id,retention_domain'))
       )
);

SET @v354_index_signature := (
    SELECT SHA2(GROUP_CONCAT(
               CONCAT_WS(
                   CHAR(31), table_name, index_name, non_unique, index_type,
                   seq_in_index, COALESCE(column_name, '<null>'),
                   COALESCE(collation, '<null>'), COALESCE(sub_part, '<null>'),
                   nullable, COALESCE(expression, '<null>'), is_visible, index_comment
               )
               ORDER BY table_name, index_name, seq_in_index SEPARATOR '\n'
           ), 256)
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND (
           FIND_IN_SET(table_name, @v354_new_tables)
           OR (table_name = 'data_retention_run' AND FIND_IN_SET(index_name, CONCAT(
               'uk_retention_run_trigger_domain,uk_retention_run_id_domain,',
               'uk_retention_run_execution_contract,idx_retention_run_source_started')))
           OR (table_name = 'data_retention_purged_slice' AND FIND_IN_SET(index_name, CONCAT(
               'uk_retention_purged_slice_operation,idx_retention_purged_slice_operation_run,',
               'idx_retention_purged_slice_operation_scope')))
           OR (table_name = 'poll_audit_purge_runs' AND FIND_IN_SET(index_name,
               'idx_poll_audit_purge_retention_run,idx_poll_audit_purge_operation'))
       )
);

SET @v354_fk_signature := (
    SELECT SHA2(GROUP_CONCAT(
               CONCAT_WS(
                   CHAR(31), k.table_name, k.constraint_name, k.ordinal_position,
                   k.column_name, k.referenced_table_name, k.referenced_column_name,
                   r.unique_constraint_name, r.match_option, r.update_rule, r.delete_rule
               )
               ORDER BY k.table_name, k.constraint_name, k.ordinal_position SEPARATOR '\n'
           ), 256)
      FROM information_schema.key_column_usage k
      JOIN information_schema.referential_constraints r
        ON r.constraint_schema = k.constraint_schema
       AND r.table_name = k.table_name
       AND r.constraint_name = k.constraint_name
     WHERE k.constraint_schema = DATABASE()
       AND (
           FIND_IN_SET(k.table_name, @v354_new_tables)
           OR (k.table_name = 'data_retention_purged_slice' AND FIND_IN_SET(
               k.constraint_name, CONCAT(
                   'fk_retention_purged_slice_operation_run,',
                   'fk_retention_purged_slice_operation_scope')))
           OR (k.table_name = 'poll_audit_purge_runs'
               AND k.constraint_name = 'fk_poll_audit_purge_operation')
       )
);

SET @v354_check_signature := (
    SELECT SHA2(GROUP_CONCAT(
               CONCAT_WS(
                   CHAR(31), tc.table_name, tc.constraint_name, tc.enforced,
                   cc.check_clause
               )
               ORDER BY tc.table_name, tc.constraint_name SEPARATOR '\n'
           ), 256)
      FROM information_schema.table_constraints tc
      JOIN information_schema.check_constraints cc
        ON cc.constraint_schema = tc.constraint_schema
       AND cc.constraint_name = tc.constraint_name
     WHERE tc.constraint_schema = DATABASE()
       AND tc.constraint_type = 'CHECK'
       AND (
           FIND_IN_SET(tc.table_name, @v354_new_tables)
           OR (tc.table_name = 'data_retention_run' AND FIND_IN_SET(
               tc.constraint_name,
               'chk_retention_run_scope_size,chk_retention_run_fingerprint_pair'))
           OR (tc.table_name = 'data_retention_purged_slice'
               AND tc.constraint_name = 'chk_retention_purged_slice_operation_run')
           OR (tc.table_name = 'poll_audit_purge_runs'
               AND tc.constraint_name = 'chk_poll_audit_purge_retention_identity')
       )
);

SET @v354_sql := IF(
    @v354_table_signature = '620635fcbf3b38e0eab257dfc218e7ff76b04790096f77ee72421ab868e47d3a'
    AND @v354_column_signature = 'a24bbe48cbc4f73e0b39cdb2cd511ba8d212080afc5542c02ddf728ac9b923e4'
    AND @v354_index_signature = 'cd4e2ebb1d72e5954a55435248252893ffe8db202e8a5d0461860d48e080eacf'
    AND @v354_fk_signature = '4422b9669b8eb34bc5d596d6b933bca51d83e84fa965873fc7c01ad568c60d3c'
    AND @v354_check_signature = '12eb71af38a45506be1cb1a4a2b60ef76aead565c0860a921c8b575c236064a6',
    'SELECT 1',
    'SELECT 1 FROM information_schema.V354_exact_governance_schema_signature_mismatch'
);
PREPARE v354_exact_schema_validate_stmt FROM @v354_sql;
EXECUTE v354_exact_schema_validate_stmt;
DEALLOCATE PREPARE v354_exact_schema_validate_stmt;
SET SESSION group_concat_max_len = @v354_previous_group_concat_max_len;
