-- WP6: a purge marker may only describe a row whose payload columns are all SQL NULL.
-- Repair legacy half-state markers without changing or copying payload data.

SET @v366_required_columns := (
    SELECT COUNT(*)
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'distribution_tasks'
       AND column_name IN (
           'request_payload', 'fill_payload', 'response_payload', 'payload_purged_at'
       )
);
SET @v366_sql := IF(
    @v366_required_columns = 4,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V366 distribution payload columns missing'''
);
PREPARE v366_columns_stmt FROM @v366_sql;
EXECUTE v366_columns_stmt;
DEALLOCATE PREPARE v366_columns_stmt;

UPDATE distribution_tasks
   SET payload_purged_at = NULL
 WHERE payload_purged_at IS NOT NULL
   AND (
       request_payload IS NOT NULL
       OR fill_payload IS NOT NULL
       OR response_payload IS NOT NULL
   );

SET @v366_check_name := 'chk_distribution_tasks_payload_purge_consistency';
SET @v366_check_exists := (
    SELECT COUNT(*)
      FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'distribution_tasks'
       AND constraint_name = @v366_check_name
       AND constraint_type = 'CHECK'
);
SET @v366_sql := IF(
    @v366_check_exists = 0,
    'ALTER TABLE distribution_tasks ADD CONSTRAINT chk_distribution_tasks_payload_purge_consistency CHECK (payload_purged_at IS NULL OR (request_payload IS NULL AND fill_payload IS NULL AND response_payload IS NULL))',
    'SELECT 1'
);
PREPARE v366_add_check_stmt FROM @v366_sql;
EXECUTE v366_add_check_stmt;
DEALLOCATE PREPARE v366_add_check_stmt;

SET @v366_check_signature := (
    SELECT LOWER(
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(check_clause, '`', ''), ' ', ''), '(', ''), ')', ''), '\n', '')
    )
      FROM information_schema.check_constraints
     WHERE constraint_schema = DATABASE()
       AND constraint_name = @v366_check_name
);
SET @v366_check_valid := (
    @v366_check_exists <= 1
    AND @v366_check_signature IS NOT NULL
    AND @v366_check_signature =
        'payload_purged_atisnullorrequest_payloadisnullandfill_payloadisnullandresponse_payloadisnull'
);
SET @v366_sql := IF(
    @v366_check_valid,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V366 payload purge CHECK signature mismatch'''
);
PREPARE v366_validate_check_stmt FROM @v366_sql;
EXECUTE v366_validate_check_stmt;
DEALLOCATE PREPARE v366_validate_check_stmt;
