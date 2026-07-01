-- Enforce the phase-one rule: one active partner_staff account per partner at database level.
-- If historical duplicates exist, keep the oldest row and mark later rows inactive before adding the unique constraint.
UPDATE sys_user u
JOIN (
    SELECT id
    FROM (
        SELECT u1.id
        FROM sys_user u1
        JOIN sys_user u2
          ON u2.role = 'partner_staff'
         AND u2.partner_id = u1.partner_id
         AND u2.id < u1.id
        WHERE u1.role = 'partner_staff'
          AND u1.partner_id IS NOT NULL
    ) duplicate_staff
) d ON d.id = u.id
SET u.is_active = 0,
    u.token_version = COALESCE(u.token_version, 0) + 1;

SET @add_partner_staff_unique_col := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE sys_user ADD COLUMN active_partner_staff_partner_id BIGINT GENERATED ALWAYS AS (CASE WHEN role = ''partner_staff'' AND is_active = 1 THEN partner_id ELSE NULL END) STORED',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'active_partner_staff_partner_id'
);
PREPARE add_partner_staff_unique_col_stmt FROM @add_partner_staff_unique_col;
EXECUTE add_partner_staff_unique_col_stmt;
DEALLOCATE PREPARE add_partner_staff_unique_col_stmt;

SET @add_partner_staff_unique_idx := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_partner_staff_single (active_partner_staff_partner_id)',
        'SELECT 1'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND index_name = 'uk_sys_user_partner_staff_single'
);
PREPARE add_partner_staff_unique_idx_stmt FROM @add_partner_staff_unique_idx;
EXECUTE add_partner_staff_unique_idx_stmt;
DEALLOCATE PREPARE add_partner_staff_unique_idx_stmt;
