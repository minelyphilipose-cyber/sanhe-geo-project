-- ============================================================
-- V191: company owner scope foundation
-- ------------------------------------------------------------
-- Adds the structural owner root for internal operator scope.
-- Historical data is intentionally not backfilled here; production
-- owners are assigned manually before owner-based scope logic is enabled.
-- ============================================================

SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'company'
      AND column_name = 'owner_id'
);

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE company ADD COLUMN owner_id BIGINT NULL COMMENT ''current internal operator owner; NULL is visible only to global roles'' AFTER created_by',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'company'
      AND index_name = 'idx_company_owner_id'
);

SET @ddl := IF(
    @index_exists = 0,
    'ALTER TABLE company ADD KEY idx_company_owner_id (owner_id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists := (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'company'
      AND constraint_name = 'fk_company_owner'
      AND constraint_type = 'FOREIGN KEY'
);

SET @ddl := IF(
    @fk_exists = 0,
    'ALTER TABLE company ADD CONSTRAINT fk_company_owner FOREIGN KEY (owner_id) REFERENCES sys_user(id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
