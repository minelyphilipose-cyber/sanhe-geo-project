-- Poll result source tables use MySQL 8's utf8mb4_0900_ai_ci collation.
-- Align retention slice identities so cross-table question_tier comparisons
-- do not fail with "Illegal mix of collations".

SET @v335_purged_slice_column_ready := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'data_retention_purged_slice'
      AND column_name = 'question_tier'
      AND data_type = 'varchar'
      AND character_maximum_length = 16
      AND character_set_name = 'utf8mb4'
      AND collation_name = 'utf8mb4_0900_ai_ci'
      AND is_nullable = 'NO'
);

SET @v335_purged_slice_ddl := IF(
    @v335_purged_slice_column_ready = 1,
    'SELECT 1',
    'ALTER TABLE data_retention_purged_slice MODIFY COLUMN question_tier VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL'
);

PREPARE v335_purged_slice_stmt FROM @v335_purged_slice_ddl;
EXECUTE v335_purged_slice_stmt;
DEALLOCATE PREPARE v335_purged_slice_stmt;

SET @v335_recompute_lock_column_ready := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'data_retention_recompute_slice_lock'
      AND column_name = 'question_tier'
      AND data_type = 'varchar'
      AND character_maximum_length = 16
      AND character_set_name = 'utf8mb4'
      AND collation_name = 'utf8mb4_0900_ai_ci'
      AND is_nullable = 'NO'
);

SET @v335_recompute_lock_ddl := IF(
    @v335_recompute_lock_column_ready = 1,
    'SELECT 1',
    'ALTER TABLE data_retention_recompute_slice_lock MODIFY COLUMN question_tier VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL'
);

PREPARE v335_recompute_lock_stmt FROM @v335_recompute_lock_ddl;
EXECUTE v335_recompute_lock_stmt;
DEALLOCATE PREPARE v335_recompute_lock_stmt;
