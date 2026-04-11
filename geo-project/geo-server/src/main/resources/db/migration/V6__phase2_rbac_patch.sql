-- ============================================================
-- V6: phase2 RBAC patch for environments where V5 was applied
-- ============================================================

-- Normalize role names (safe update)
UPDATE sys_role SET role_name = 'Super Admin' WHERE role_key = 'super_admin';
UPDATE sys_role SET role_name = 'Manager' WHERE role_key = 'manager';
UPDATE sys_role SET role_name = 'Delivery Manager' WHERE role_key = 'delivery_manager';
UPDATE sys_role SET role_name = 'Operator' WHERE role_key = 'operator';
UPDATE sys_role SET role_name = 'Sales' WHERE role_key = 'sales';
UPDATE sys_role SET role_name = 'Partner Owner' WHERE role_key = 'partner';
UPDATE sys_role SET role_name = 'Partner Staff' WHERE role_key = 'partner_staff';
UPDATE sys_role SET role_name = 'Partner Viewer' WHERE role_key = 'partner_viewer';

-- Normalize permission names (safe update)
UPDATE sys_permission SET perm_name = 'User Manage' WHERE perm_key = 'user.manage';
UPDATE sys_permission SET perm_name = 'Partner Read' WHERE perm_key = 'partner.read';
UPDATE sys_permission SET perm_name = 'Partner Write' WHERE perm_key = 'partner.write';
UPDATE sys_permission SET perm_name = 'Company Read' WHERE perm_key = 'company.read';
UPDATE sys_permission SET perm_name = 'Company Write' WHERE perm_key = 'company.write';
UPDATE sys_permission SET perm_name = 'Project Read' WHERE perm_key = 'project.read';
UPDATE sys_permission SET perm_name = 'Project Write' WHERE perm_key = 'project.write';

-- Ensure role-permission relations exist (idempotent)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key = 'user.manage'
WHERE r.role_key IN ('super_admin', 'manager')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('partner.read', 'partner.write', 'company.read', 'company.write', 'project.read', 'project.write')
WHERE r.role_key = 'super_admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('partner.read', 'partner.write', 'company.read', 'company.write', 'project.read', 'project.write')
WHERE r.role_key = 'manager'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('partner.read', 'company.read', 'company.write', 'project.read', 'project.write')
WHERE r.role_key IN ('delivery_manager', 'operator')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('company.read', 'company.write', 'project.read')
WHERE r.role_key = 'sales'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('partner.read', 'company.read', 'project.read')
WHERE r.role_key IN ('partner', 'partner_staff', 'partner_viewer')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Ensure user-role backfill exists for historical accounts
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_key = u.role
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user_role ur
    WHERE ur.user_id = u.id AND ur.role_id = r.id
);
