-- ============================================================
-- V37: RBAC refine
-- 1) sales remove company.write
-- 2) add dispatch.presale.enqueue permission (for sales trigger)
-- 3) partner_staff add company.write (can create customer)
-- ============================================================

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'dispatch.presale.enqueue', 'Dispatch Presale Enqueue', 'dispatch', 'presale_enqueue', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'dispatch.presale.enqueue');

-- sales: remove company.write
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key = 'sales'
  AND p.perm_key = 'company.write';

-- sales: keep only company.read/project.read + dispatch.presale.enqueue
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key = 'dispatch.presale.enqueue'
WHERE r.role_key = 'sales'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission x
      WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- partner_staff: allow company.write (can create customer), still no project.write
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key = 'company.write'
WHERE r.role_key = 'partner_staff'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission x
      WHERE x.role_id = r.id AND x.permission_id = p.id
  );
