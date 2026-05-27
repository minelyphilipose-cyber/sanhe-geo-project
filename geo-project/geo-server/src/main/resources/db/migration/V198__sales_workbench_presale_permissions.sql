-- Sales workspace and owned presale report operations.
-- Sales can see their assigned customers and fully manage reports they created.

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'workbench.sales.read', 'Sales Workbench Read', 'workbench', 'sales_read', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'workbench.sales.read');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.report.delete', 'Presale Report Delete', 'presale', 'delete', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'presale.report.delete');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'workbench.sales.read',
    'company.read',
    'presale.report.list',
    'presale.report.view',
    'presale.report.create',
    'presale.report.delete'
)
WHERE r.role_key = 'sales'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key IN ('operator', 'delivery_manager', 'manager')
  AND p.perm_key = 'workbench.sales.read';

DELIMITER $$

DROP PROCEDURE IF EXISTS v198_assert_role_permission $$
CREATE PROCEDURE v198_assert_role_permission(
    IN in_role_key VARCHAR(64),
    IN in_perm_key VARCHAR(128),
    IN expected INT
)
BEGIN
    DECLARE actual INT DEFAULT 0;
    SELECT COUNT(1)
      INTO actual
      FROM sys_role r
      JOIN sys_role_permission rp ON rp.role_id = r.id
      JOIN sys_permission p ON p.id = rp.permission_id
     WHERE r.role_key = in_role_key
       AND p.perm_key = in_perm_key
       AND p.status IN ('active', 'deprecated');
    IF actual <> expected THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V198 role permission assertion failed';
    END IF;
END $$

DELIMITER ;

CALL v198_assert_role_permission('sales', 'workbench.sales.read', 1);
CALL v198_assert_role_permission('sales', 'company.read', 1);
CALL v198_assert_role_permission('sales', 'presale.report.list', 1);
CALL v198_assert_role_permission('sales', 'presale.report.view', 1);
CALL v198_assert_role_permission('sales', 'presale.report.create', 1);
CALL v198_assert_role_permission('sales', 'presale.report.delete', 1);
CALL v198_assert_role_permission('sales', 'presale.report.manage', 0);
CALL v198_assert_role_permission('sales', 'content.distribution.retry', 0);
CALL v198_assert_role_permission('sales', 'dispatch.alert.resolve', 0);
CALL v198_assert_role_permission('sales', 'system.alert.resolve', 0);

CALL v198_assert_role_permission('operator', 'workbench.sales.read', 0);
CALL v198_assert_role_permission('delivery_manager', 'workbench.sales.read', 0);
CALL v198_assert_role_permission('manager', 'workbench.sales.read', 0);

DROP PROCEDURE IF EXISTS v198_assert_role_permission;
