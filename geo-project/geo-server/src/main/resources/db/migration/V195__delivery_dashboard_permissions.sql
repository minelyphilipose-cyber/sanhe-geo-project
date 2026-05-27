-- ============================================================
-- V195: delivery dashboard permissions
-- Notes:
--   * delivery dashboard is for delivery_manager only; super_admin is covered by Java wildcard
--   * manager remains system/configuration admin and does not receive delivery dashboard permissions
-- ============================================================

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT perm_key, perm_name, module, action, 'active'
FROM (
    SELECT 'delivery.overview.read' perm_key, 'Delivery Overview Read' perm_name, 'delivery' module, 'overview_read' action
    UNION ALL SELECT 'delivery.operator_stats.read', 'Delivery Operator Stats Read', 'delivery', 'operator_stats_read'
    UNION ALL SELECT 'delivery.exception.handle', 'Delivery Exception Handle', 'delivery', 'exception_handle'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.perm_key = seed.perm_key
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'delivery.overview.read',
    'delivery.operator_stats.read',
    'delivery.exception.handle'
)
WHERE r.role_key = 'delivery_manager'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key IN ('operator', 'manager')
  AND p.perm_key IN (
      'delivery.overview.read',
      'delivery.operator_stats.read',
      'delivery.exception.handle'
  );

DROP PROCEDURE IF EXISTS v195_assert_role_permission;

DELIMITER $$
CREATE PROCEDURE v195_assert_role_permission(
    IN role_key_value VARCHAR(64),
    IN perm_key_value VARCHAR(128),
    IN should_have TINYINT
)
BEGIN
    DECLARE actual_count INT DEFAULT 0;
    DECLARE error_message VARCHAR(255);

    SELECT COUNT(*) INTO actual_count
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = role_key_value
      AND p.perm_key = perm_key_value
      AND p.status IN ('active', 'deprecated');

    IF should_have = 1 AND actual_count = 0 THEN
        SET error_message = CONCAT('V195 ASSERT FAILED: ', role_key_value, ' should have ', perm_key_value);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;

    IF should_have = 0 AND actual_count > 0 THEN
        SET error_message = CONCAT('V195 ASSERT FAILED: ', role_key_value, ' should not have ', perm_key_value);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;
END$$
DELIMITER ;

CALL v195_assert_role_permission('delivery_manager', 'delivery.overview.read', 1);
CALL v195_assert_role_permission('delivery_manager', 'delivery.operator_stats.read', 1);
CALL v195_assert_role_permission('delivery_manager', 'delivery.exception.handle', 1);

CALL v195_assert_role_permission('operator', 'delivery.overview.read', 0);
CALL v195_assert_role_permission('operator', 'delivery.operator_stats.read', 0);
CALL v195_assert_role_permission('operator', 'delivery.exception.handle', 0);

CALL v195_assert_role_permission('manager', 'delivery.overview.read', 0);
CALL v195_assert_role_permission('manager', 'delivery.operator_stats.read', 0);
CALL v195_assert_role_permission('manager', 'delivery.exception.handle', 0);

DROP PROCEDURE IF EXISTS v195_assert_role_permission;
