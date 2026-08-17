-- WP7 poll retention execute contract and permanent preserved-result cleanup ledger.
-- All runtime execute switches remain disabled; this migration only expands schema contracts.

-- Forward-expand the V354 operation checks without modifying the published migration.
SET @v367_check_count := (
    SELECT COUNT(*) FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'data_retention_operation'
       AND constraint_type = 'CHECK'
       AND constraint_name = 'chk_retention_operation_item_count'
);
SET @v367_sql := CASE
    WHEN @v367_check_count = 1 THEN
        'ALTER TABLE data_retention_operation DROP CHECK chk_retention_operation_item_count'
    WHEN @v367_check_count = 0 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V367 item-count check is ambiguous'''
END;
PREPARE v367_drop_item_count_stmt FROM @v367_sql;
EXECUTE v367_drop_item_count_stmt;
DEALLOCATE PREPARE v367_drop_item_count_stmt;

SET @v367_check_count := (
    SELECT COUNT(*) FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'data_retention_operation'
       AND constraint_type = 'CHECK'
       AND constraint_name = 'chk_retention_operation_domain_type'
);
SET @v367_sql := CASE
    WHEN @v367_check_count = 1 THEN
        'ALTER TABLE data_retention_operation DROP CHECK chk_retention_operation_domain_type'
    WHEN @v367_check_count = 0 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V367 domain-type check is ambiguous'''
END;
PREPARE v367_drop_domain_type_stmt FROM @v367_sql;
EXECUTE v367_drop_domain_type_stmt;
DEALLOCATE PREPARE v367_drop_domain_type_stmt;

SET @v367_check_count := (
    SELECT COUNT(*) FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'data_retention_operation'
       AND constraint_type = 'CHECK'
       AND constraint_name = 'chk_retention_operation_scope_shape'
);
SET @v367_sql := CASE
    WHEN @v367_check_count = 1 THEN
        'ALTER TABLE data_retention_operation DROP CHECK chk_retention_operation_scope_shape'
    WHEN @v367_check_count = 0 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V367 scope-shape check is ambiguous'''
END;
PREPARE v367_drop_scope_shape_stmt FROM @v367_sql;
EXECUTE v367_drop_scope_shape_stmt;
DEALLOCATE PREPARE v367_drop_scope_shape_stmt;

SET @v367_check_count := (
    SELECT COUNT(*) FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'data_retention_operation'
       AND constraint_type = 'CHECK'
       AND constraint_name = 'chk_retention_operation_finalization_source'
);
SET @v367_sql := CASE
    WHEN @v367_check_count = 1 THEN
        'ALTER TABLE data_retention_operation DROP CHECK chk_retention_operation_finalization_source'
    WHEN @v367_check_count = 0 THEN 'SELECT 1'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V367 finalization-source check is ambiguous'''
END;
PREPARE v367_drop_finalization_source_stmt FROM @v367_sql;
EXECUTE v367_drop_finalization_source_stmt;
DEALLOCATE PREPARE v367_drop_finalization_source_stmt;

ALTER TABLE data_retention_operation
    ADD CONSTRAINT chk_retention_operation_item_count CHECK (
        (operation_type = 'website_batch' AND item_count BETWEEN 1 AND 20)
        OR (operation_type = 'poll_slice' AND item_count = 1)
        OR (operation_type = 'poll_preserved_cleanup' AND item_count BETWEEN 1 AND 20)
    ),
    ADD CONSTRAINT chk_retention_operation_domain_type CHECK (
        (operation_type = 'website_batch' AND domain = 'website_published_content')
        OR (operation_type IN ('poll_slice','poll_preserved_cleanup') AND domain = 'poll_results')
    ),
    ADD CONSTRAINT chk_retention_operation_scope_shape CHECK (
        (operation_type = 'website_batch'
         AND cutoff_at IS NOT NULL
         AND upper_bound_business_key IS NOT NULL
         AND poll_project_id IS NULL
         AND poll_batch_date IS NULL
         AND poll_question_tier IS NULL
         AND poll_retention_cutoff_date IS NULL)
        OR
        (operation_type IN ('poll_slice','poll_preserved_cleanup')
         AND cutoff_at IS NULL
         AND upper_bound_business_key IS NULL
         AND poll_project_id IS NOT NULL
         AND poll_batch_date IS NOT NULL
         AND poll_question_tier IS NOT NULL
         AND poll_retention_cutoff_date IS NOT NULL)
    ),
    ADD CONSTRAINT chk_retention_operation_finalization_source CHECK (
        outcome_finalization_source IS NULL
        OR outcome_finalization_source IN (
            'website_commit','poll_marker','poll_cleanup','reconciliation','terminal_attempt'
        )
    );

CREATE TABLE IF NOT EXISTS data_retention_poll_preserved_result (
    origin_operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    poll_result_id BIGINT NOT NULL,
    domain VARCHAR(64) NOT NULL DEFAULT 'poll_results',
    project_id BIGINT NOT NULL,
    batch_date DATE NOT NULL,
    question_tier VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    preserved_at DATETIME(3) NOT NULL,
    cleanup_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'preserved',
    cleanup_operation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    cleaned_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (origin_operation_id, poll_result_id),
    UNIQUE KEY uk_retention_poll_preserved_result (poll_result_id),
    KEY idx_retention_poll_preserved_select (cleanup_status, origin_operation_id, poll_result_id),
    KEY idx_retention_poll_preserved_cleanup (cleanup_operation_id, cleanup_status, poll_result_id),
    KEY idx_retention_poll_preserved_marker
        (origin_operation_id, domain, project_id, batch_date, question_tier),
    CONSTRAINT fk_retention_poll_preserved_marker
        FOREIGN KEY (origin_operation_id, domain, project_id, batch_date, question_tier)
        REFERENCES data_retention_purged_slice
            (operation_id, domain, project_id, batch_date, question_tier)
        ON DELETE RESTRICT,
    CONSTRAINT fk_retention_poll_preserved_cleanup_operation
        FOREIGN KEY (cleanup_operation_id)
        REFERENCES data_retention_operation (operation_id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_retention_poll_preserved_domain CHECK (domain = 'poll_results'),
    CONSTRAINT chk_retention_poll_preserved_status CHECK (
        (cleanup_status = 'preserved' AND cleanup_operation_id IS NULL AND cleaned_at IS NULL)
        OR
        (cleanup_status = 'cleaned' AND cleanup_operation_id IS NOT NULL AND cleaned_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='permanent marker-owned ledger for preserved poll result projections';

-- Reject a same-name partial or incompatible object instead of accepting IF NOT EXISTS silently.
SET @v367_ledger_column_count := (
    SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'data_retention_poll_preserved_result'
);
SET @v367_ledger_index_count := (
    SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'data_retention_poll_preserved_result'
       AND index_name IN (
           'PRIMARY','uk_retention_poll_preserved_result','idx_retention_poll_preserved_select',
           'idx_retention_poll_preserved_cleanup','idx_retention_poll_preserved_marker'
       )
);
SET @v367_ledger_fk_count := (
    SELECT COUNT(*) FROM information_schema.referential_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'data_retention_poll_preserved_result'
       AND constraint_name IN (
           'fk_retention_poll_preserved_marker',
           'fk_retention_poll_preserved_cleanup_operation'
       )
       AND delete_rule = 'RESTRICT'
);
SET @v367_ledger_check_count := (
    SELECT COUNT(*) FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'data_retention_poll_preserved_result'
       AND constraint_type = 'CHECK'
       AND constraint_name IN (
           'chk_retention_poll_preserved_domain',
           'chk_retention_poll_preserved_status'
       )
);
SET @v367_operation_check_count := (
    SELECT COUNT(*) FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'data_retention_operation'
       AND constraint_type = 'CHECK'
       AND constraint_name IN (
           'chk_retention_operation_item_count',
           'chk_retention_operation_domain_type',
           'chk_retention_operation_scope_shape',
           'chk_retention_operation_finalization_source'
       )
);
SET @v367_sql := IF(
    @v367_ledger_column_count = 12
    AND @v367_ledger_index_count = 5
    AND @v367_ledger_fk_count = 2
    AND @v367_ledger_check_count = 2
    AND @v367_operation_check_count = 4,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V367 poll retention schema validation failed'''
);
PREPARE v367_final_validate_stmt FROM @v367_sql;
EXECUTE v367_final_validate_stmt;
DEALLOCATE PREPARE v367_final_validate_stmt;
