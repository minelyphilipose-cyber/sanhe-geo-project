-- ============================================================
-- V196: role-specific workbench permissions
-- Notes:
--   * workbench permissions are role-entry permissions, not reusable business permissions
--   * delivery_manager continues to use delivery.overview.read for the delivery workbench
--   * super_admin is covered by Java wildcard and is not asserted as having individual DB grants
-- ============================================================

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT perm_key, perm_name, module, action, 'active'
FROM (
    SELECT 'workbench.operator.read' perm_key, 'Operator Workbench Read' perm_name, 'workbench' module, 'operator_read' action
    UNION ALL SELECT 'workbench.manager.read', 'Manager Workbench Read', 'workbench', 'manager_read'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.perm_key = seed.perm_key
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key = 'workbench.operator.read'
WHERE r.role_key = 'operator'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key = 'workbench.manager.read'
WHERE r.role_key = 'manager'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE (
      r.role_key = 'operator'
      AND p.perm_key IN ('workbench.manager.read', 'delivery.overview.read')
  )
   OR (
      r.role_key = 'delivery_manager'
      AND p.perm_key IN ('workbench.operator.read', 'workbench.manager.read')
  )
   OR (
      r.role_key = 'manager'
      AND p.perm_key IN ('workbench.operator.read', 'delivery.overview.read')
  );

DROP PROCEDURE IF EXISTS v196_assert_role_permission;

DELIMITER $$
CREATE PROCEDURE v196_assert_role_permission(
    IN role_key_value VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN perm_key_value VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN should_have TINYINT
)
BEGIN
    DECLARE actual_count INT DEFAULT 0;
    DECLARE error_message VARCHAR(255);

    SELECT COUNT(*) INTO actual_count
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE CONVERT(r.role_key USING utf8mb4) COLLATE utf8mb4_unicode_ci = role_key_value
      AND CONVERT(p.perm_key USING utf8mb4) COLLATE utf8mb4_unicode_ci = perm_key_value
      AND CONVERT(p.status USING utf8mb4) COLLATE utf8mb4_unicode_ci IN ('active', 'deprecated');

    IF should_have = 1 AND actual_count = 0 THEN
        SET error_message = CONCAT('V196 ASSERT FAILED: ', role_key_value, ' should have ', perm_key_value);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;

    IF should_have = 0 AND actual_count > 0 THEN
        SET error_message = CONCAT('V196 ASSERT FAILED: ', role_key_value, ' should not have ', perm_key_value);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;
END$$
DELIMITER ;

CALL v196_assert_role_permission('operator', 'workbench.operator.read', 1);
CALL v196_assert_role_permission('operator', 'workbench.manager.read', 0);
CALL v196_assert_role_permission('operator', 'delivery.overview.read', 0);

CALL v196_assert_role_permission('delivery_manager', 'delivery.overview.read', 1);
CALL v196_assert_role_permission('delivery_manager', 'workbench.operator.read', 0);
CALL v196_assert_role_permission('delivery_manager', 'workbench.manager.read', 0);

CALL v196_assert_role_permission('manager', 'workbench.manager.read', 1);
CALL v196_assert_role_permission('manager', 'workbench.operator.read', 0);
CALL v196_assert_role_permission('manager', 'delivery.overview.read', 0);

DROP PROCEDURE IF EXISTS v196_assert_role_permission;
