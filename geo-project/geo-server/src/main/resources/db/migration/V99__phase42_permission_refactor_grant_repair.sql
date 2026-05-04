-- ============================================================
-- V99: phase42 permission grant repair
-- Backfills granular permissions and grants for environments that
-- already applied an earlier V96 before the permission seed was completed.
-- ============================================================

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT perm_key, perm_name, module, action, 'active'
FROM (
    SELECT 'brand.create' perm_key, 'Brand Create' perm_name, 'brand' module, 'create' action
    UNION ALL SELECT 'brand.update', 'Brand Update', 'brand', 'update'
    UNION ALL SELECT 'brand.delete', 'Brand Delete', 'brand', 'delete'
    UNION ALL SELECT 'brand.material.upload', 'Brand Material Upload', 'brand_material', 'upload'
    UNION ALL SELECT 'brand.material.delete', 'Brand Material Delete', 'brand_material', 'delete'
    UNION ALL SELECT 'company.create', 'Company Create', 'company', 'create'
    UNION ALL SELECT 'company.update', 'Company Update', 'company', 'update'
    UNION ALL SELECT 'company.delete', 'Company Delete', 'company', 'delete'
    UNION ALL SELECT 'company.account.adjust', 'Company Account Adjust', 'company_account', 'adjust'
    UNION ALL SELECT 'project.create', 'Project Create', 'project', 'create'
    UNION ALL SELECT 'project.update', 'Project Update', 'project', 'update'
    UNION ALL SELECT 'project.delete', 'Project Delete', 'project', 'delete'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.perm_key = seed.perm_key
);

DROP PROCEDURE IF EXISTS v99_assert_grants;

DELIMITER $$
CREATE PROCEDURE v99_assert_grants(
    IN role_key_value VARCHAR(64),
    IN actual_count INT,
    IN expected_min INT
)
BEGIN
    DECLARE error_message VARCHAR(255);

    IF actual_count < expected_min THEN
        SET error_message = CONCAT(
            'V99 GRANTS FAILED: ',
            role_key_value,
            ' has ',
            actual_count,
            ' active grants, expected at least ',
            expected_min
        );
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;
END$$
DELIMITER ;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'user.manage', 'role.manage', 'permission.manage',
    'partner.read', 'partner.create', 'partner.update', 'partner.status.update', 'partner.discount.update',
    'partner.account.read', 'partner.account.recharge.audit', 'partner.account.adjust', 'partner.account.txn.read',
    'company.read', 'company.create', 'company.update', 'company.delete', 'company.account.adjust',
    'brand.read', 'brand.create', 'brand.update', 'brand.delete', 'brand.material.upload', 'brand.material.delete', 'brand.statement.lock',
    'project.read', 'project.create', 'project.update', 'project.start', 'project.pause', 'project.terminate', 'project.delete',
    'project.report.read', 'project.report.export',
    'package.read', 'package.manage',
    'question_pool.core.confirm', 'question_pool.core.delete', 'report.review',
    'dispatch.alert.resolve', 'dispatch.task.replay.dead_letter',
    'keyword_group.read', 'keyword_group.write', 'keyword_affix.manage',
    'activity_log.read', 'activity_log.finance.read'
)
WHERE r.role_key IN ('super_admin', 'manager')
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'partner.read',
    'company.read', 'company.create', 'company.update',
    'brand.read', 'brand.create', 'brand.update', 'brand.material.upload', 'brand.material.delete',
    'project.read', 'project.create', 'project.update', 'project.start', 'project.pause', 'project.terminate',
    'project.report.read', 'project.report.export',
    'keyword_group.read', 'keyword_group.write',
    'package.read',
    'activity_log.read'
)
WHERE r.role_key IN ('delivery_manager', 'operator')
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'brand.statement.lock',
    'question_pool.core.confirm', 'question_pool.core.delete', 'report.review',
    'dispatch.alert.resolve'
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
    'company.read', 'brand.read', 'project.read', 'project.report.read', 'keyword_group.read'
)
WHERE r.role_key = 'sales'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'partner.read', 'partner.account.read', 'partner.account.recharge.apply', 'partner.account.txn.read', 'partner.staff.manage',
    'company.read', 'company.create', 'company.update', 'company.delete',
    'brand.read', 'brand.create', 'brand.update', 'brand.delete', 'brand.material.upload', 'brand.material.delete',
    'project.read', 'project.create', 'project.update', 'project.start',
    'project.report.read', 'project.report.export',
    'package.read'
)
WHERE r.role_key = 'partner'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'partner.read',
    'company.read', 'company.create', 'company.update',
    'brand.read', 'brand.create', 'brand.update', 'brand.material.upload', 'brand.material.delete',
    'project.read', 'project.create', 'project.update',
    'project.report.read',
    'package.read'
)
WHERE r.role_key = 'partner_staff'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'partner.read', 'company.read', 'brand.read', 'project.read', 'project.report.read', 'package.read'
)
WHERE r.role_key = 'partner_viewer'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key = 'partner_staff'
  AND (
      p.perm_key = 'project.start'
      OR p.perm_key LIKE 'partner.account.%'
      OR p.perm_key = 'partner.staff.manage'
  );

SET @super_admin_grants = (
    SELECT COUNT(*) FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'super_admin' AND p.status = 'active'
);
SELECT IF(@super_admin_grants >= 45, 'ok', CONCAT('V99 GRANTS FAILED: super_admin has ', @super_admin_grants, ' active grants, expected at least 45')) AS check_super_admin_grants;
CALL v99_assert_grants('super_admin', @super_admin_grants, 45);

SET @manager_grants = (
    SELECT COUNT(*) FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'manager' AND p.status = 'active'
);
SELECT IF(@manager_grants >= 45, 'ok', CONCAT('V99 GRANTS FAILED: manager has ', @manager_grants, ' active grants, expected at least 45')) AS check_manager_grants;
CALL v99_assert_grants('manager', @manager_grants, 45);

SET @delivery_manager_grants = (
    SELECT COUNT(*) FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'delivery_manager' AND p.status = 'active'
);
SELECT IF(@delivery_manager_grants >= 26, 'ok', CONCAT('V99 GRANTS FAILED: delivery_manager has ', @delivery_manager_grants, ' active grants, expected at least 26')) AS check_delivery_manager_grants;
CALL v99_assert_grants('delivery_manager', @delivery_manager_grants, 26);

SET @operator_grants = (
    SELECT COUNT(*) FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'operator' AND p.status = 'active'
);
SELECT IF(@operator_grants >= 21, 'ok', CONCAT('V99 GRANTS FAILED: operator has ', @operator_grants, ' active grants, expected at least 21')) AS check_operator_grants;
CALL v99_assert_grants('operator', @operator_grants, 21);

SET @sales_grants = (
    SELECT COUNT(*) FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'sales' AND p.status = 'active'
);
SELECT IF(@sales_grants >= 5, 'ok', CONCAT('V99 GRANTS FAILED: sales has ', @sales_grants, ' active grants, expected at least 5')) AS check_sales_grants;
CALL v99_assert_grants('sales', @sales_grants, 5);

SET @partner_grants = (
    SELECT COUNT(*) FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'partner' AND p.status = 'active'
);
SELECT IF(@partner_grants >= 22, 'ok', CONCAT('V99 GRANTS FAILED: partner has ', @partner_grants, ' active grants, expected at least 22')) AS check_partner_grants;
CALL v99_assert_grants('partner', @partner_grants, 22);

SET @partner_viewer_grants = (
    SELECT COUNT(*) FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'partner_viewer' AND p.status = 'active'
);
SELECT IF(@partner_viewer_grants >= 6, 'ok', CONCAT('V99 GRANTS FAILED: partner_viewer has ', @partner_viewer_grants, ' active grants, expected at least 6')) AS check_partner_viewer_grants;
CALL v99_assert_grants('partner_viewer', @partner_viewer_grants, 6);

SET @partner_staff_grants = (
    SELECT COUNT(*) FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'partner_staff' AND p.status = 'active'
);
SELECT IF(@partner_staff_grants >= 14, 'ok', CONCAT('V99 GRANTS FAILED: partner_staff has ', @partner_staff_grants, ' active grants, expected at least 14')) AS check_partner_staff_grants;
CALL v99_assert_grants('partner_staff', @partner_staff_grants, 14);

DROP PROCEDURE IF EXISTS v99_assert_grants;
