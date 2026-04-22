-- =========================================================================
-- V71: drop legacy presale_ai_test_result (destructive migration)
-- Rollback: NOT SUPPORTED (data-destructive)
-- Safety guard: abort migration if legacy table has rows
-- Flyway-compatible script (pure SQL)
-- NOTE:
-- 1) Avoid CASE/IF short-circuit assumptions; some MySQL plans may still fail on absent table references.
-- 2) Guard must abort clearly when legacy table contains data.
-- =========================================================================

SET @v71_table_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'presale_ai_test_result'
);

SET @v71_row_count = 0;
SET @v71_count_sql = IF(
    @v71_table_exists > 0,
    'SELECT COUNT(*) INTO @v71_row_count FROM presale_ai_test_result',
    'SELECT 0 INTO @v71_row_count'
);
PREPARE stmt_v71_count FROM @v71_count_sql;
EXECUTE stmt_v71_count;
DEALLOCATE PREPARE stmt_v71_count;

CREATE TEMPORARY TABLE IF NOT EXISTS _v71_drop_guard (
    gate TINYINT NOT NULL,
    CONSTRAINT chk_v71_presale_ai_test_result_must_be_empty CHECK (gate = 1)
) ENGINE=MEMORY;

DELETE FROM _v71_drop_guard;
INSERT INTO _v71_drop_guard (gate) VALUES (IF(@v71_row_count = 0, 1, 0));

DROP TEMPORARY TABLE IF EXISTS _v71_drop_guard;
DROP TABLE IF EXISTS presale_ai_test_result;
