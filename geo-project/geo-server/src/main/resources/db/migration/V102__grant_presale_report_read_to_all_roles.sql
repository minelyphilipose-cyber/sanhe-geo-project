-- Expand presale report read access to every active role.
-- Write/export/manage permissions remain role-specific.

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.status = 'active'
  AND p.status = 'active'
  AND p.perm_key IN (
    'presale.report.list',
    'presale.report.view'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
