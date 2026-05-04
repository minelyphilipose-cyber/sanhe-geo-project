-- Grant presale report creation to every active role except partner read-only.
-- Read-only partner accounts keep list/view access only.

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.status = 'active'
  AND r.role_key <> 'partner_viewer'
  AND p.status = 'active'
  AND p.perm_key = 'presale.report.create'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
