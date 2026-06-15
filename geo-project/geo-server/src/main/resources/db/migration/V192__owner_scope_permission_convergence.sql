-- ============================================================
-- V192: owner-scope permission convergence
-- Aligns role grants with the owner_id data-scope model enabled in V191.
-- Scope:
--   * operator owns the delivery creation loop within owner scope
--   * delivery_manager is global fallback/assignment, but does not create business objects
--   * manager is system/configuration admin; no delivery execution and no project.update until content.* split
--   * super_admin is covered by legacy '*' in Java, no per-key grant needed here
-- ============================================================

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT perm_key, perm_name, module, action, 'active'
FROM (
    SELECT 'delivery.assignment.manage' perm_key, 'Delivery Assignment Manage' perm_name, 'delivery' module, 'assignment_manage' action
    UNION ALL SELECT 'presale.report.create', 'Presale Report Create', 'presale', 'create'
    UNION ALL SELECT 'presale.report.manage', 'Presale Report Manage', 'presale', 'manage'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.perm_key = seed.perm_key
);

-- Grants: operator keeps the owner-scoped delivery loop; delivery_manager gets assignment/fallback only.
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'company.create', 'brand.create', 'project.create',
    'project.start', 'project.pause',
    'project.report.read', 'project.report.export',
    'presale.report.create',
    'keyword_group.read', 'keyword_group.write'
)
WHERE r.role_key = 'operator'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'delivery.assignment.manage',
    'company.read', 'company.update',
    'brand.read', 'brand.update', 'brand.material.upload', 'brand.material.delete', 'brand.statement.lock',
    'project.read', 'project.update', 'project.start', 'project.pause', 'project.terminate',
    'project.report.read', 'project.report.export',
    'report.review', 'dispatch.alert.resolve',
    'presale.report.create', 'presale.report.manage',
    'keyword_group.read', 'keyword_group.write'
)
WHERE r.role_key = 'delivery_manager'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'company.read', 'company.update',
    'brand.read', 'brand.update',
    'project.read',
    'project.report.read', 'project.report.export',
    'presale.report.create', 'presale.report.manage',
    'keyword_group.read', 'keyword_affix.manage',
    'activity_log.read',
    'user.manage', 'role.manage', 'permission.manage',
    'package.read', 'package.manage'
)
WHERE r.role_key = 'manager'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Revocations: DELETE role bindings; deprecated permissions still count as active in this codebase.
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key = 'operator'
  AND p.perm_key IN (
      'company.delete', 'brand.delete', 'project.delete',
      'project.terminate',
      'activity_log.read', 'activity_log.finance.read'
  );

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key = 'delivery_manager'
  AND p.perm_key IN (
      'company.create', 'brand.create', 'project.create',
      'company.delete', 'brand.delete', 'project.delete',
      'activity_log.read', 'activity_log.finance.read'
  );

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key = 'manager'
  AND p.perm_key IN (
      'company.create', 'company.delete', 'company.account.adjust',
      'brand.create', 'brand.delete', 'brand.material.upload', 'brand.material.delete', 'brand.statement.lock',
      'project.create', 'project.update', 'project.start', 'project.pause', 'project.terminate', 'project.delete',
      'report.review', 'dispatch.alert.resolve', 'dispatch.task.replay.dead_letter',
      'keyword_group.write',
      'delivery.assignment.manage'
  );

DROP PROCEDURE IF EXISTS v192_assert_role_permission;

DELIMITER $$
CREATE PROCEDURE v192_assert_role_permission(
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
    WHERE r.role_key = role_key_value
      AND p.perm_key = perm_key_value
      AND p.status IN ('active', 'deprecated');

    IF should_have = 1 AND actual_count = 0 THEN
        SET error_message = CONCAT('V192 ASSERT FAILED: ', role_key_value, ' should have ', perm_key_value);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;

    IF should_have = 0 AND actual_count > 0 THEN
        SET error_message = CONCAT('V192 ASSERT FAILED: ', role_key_value, ' should not have ', perm_key_value);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;
END$$
DELIMITER ;

-- operator: owner-scoped creation/start-pause loop, no terminate/delete/global activity log.
CALL v192_assert_role_permission('operator', 'company.create', 1);
CALL v192_assert_role_permission('operator', 'brand.create', 1);
CALL v192_assert_role_permission('operator', 'project.create', 1);
CALL v192_assert_role_permission('operator', 'project.start', 1);
CALL v192_assert_role_permission('operator', 'project.pause', 1);
CALL v192_assert_role_permission('operator', 'presale.report.create', 1);
CALL v192_assert_role_permission('operator', 'keyword_group.read', 1);
CALL v192_assert_role_permission('operator', 'keyword_group.write', 1);
CALL v192_assert_role_permission('operator', 'project.terminate', 0);
CALL v192_assert_role_permission('operator', 'company.delete', 0);
CALL v192_assert_role_permission('operator', 'brand.delete', 0);
CALL v192_assert_role_permission('operator', 'project.delete', 0);
CALL v192_assert_role_permission('operator', 'activity_log.read', 0);

-- delivery_manager: global fallback/assignment, no business object creation.
CALL v192_assert_role_permission('delivery_manager', 'delivery.assignment.manage', 1);
CALL v192_assert_role_permission('delivery_manager', 'company.create', 0);
CALL v192_assert_role_permission('delivery_manager', 'brand.create', 0);
CALL v192_assert_role_permission('delivery_manager', 'project.create', 0);
CALL v192_assert_role_permission('delivery_manager', 'project.terminate', 1);
CALL v192_assert_role_permission('delivery_manager', 'report.review', 1);
CALL v192_assert_role_permission('delivery_manager', 'dispatch.alert.resolve', 1);
CALL v192_assert_role_permission('delivery_manager', 'presale.report.create', 1);
CALL v192_assert_role_permission('delivery_manager', 'presale.report.manage', 1);
CALL v192_assert_role_permission('delivery_manager', 'keyword_group.read', 1);
CALL v192_assert_role_permission('delivery_manager', 'keyword_group.write', 1);

-- manager: system/configuration admin; no delivery assignment/execution, project.update waits for content.* split.
CALL v192_assert_role_permission('manager', 'company.create', 0);
CALL v192_assert_role_permission('manager', 'brand.create', 0);
CALL v192_assert_role_permission('manager', 'project.create', 0);
CALL v192_assert_role_permission('manager', 'project.update', 0);
CALL v192_assert_role_permission('manager', 'project.start', 0);
CALL v192_assert_role_permission('manager', 'project.pause', 0);
CALL v192_assert_role_permission('manager', 'project.terminate', 0);
CALL v192_assert_role_permission('manager', 'report.review', 0);
CALL v192_assert_role_permission('manager', 'delivery.assignment.manage', 0);
CALL v192_assert_role_permission('manager', 'project.report.export', 1);
CALL v192_assert_role_permission('manager', 'presale.report.create', 1);
CALL v192_assert_role_permission('manager', 'presale.report.manage', 1);
CALL v192_assert_role_permission('manager', 'user.manage', 1);
CALL v192_assert_role_permission('manager', 'keyword_affix.manage', 1);
CALL v192_assert_role_permission('manager', 'activity_log.read', 1);

DROP PROCEDURE IF EXISTS v192_assert_role_permission;
