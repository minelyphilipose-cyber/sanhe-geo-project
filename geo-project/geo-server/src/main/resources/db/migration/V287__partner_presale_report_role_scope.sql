-- Partner owns AI visibility diagnostic reports; partner staff must not access report capabilities.

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
  'presale.report.list',
  'presale.report.view',
  'presale.report.create',
  'presale.report.download'
)
WHERE r.role_key = 'partner'
  AND r.status = 'active'
  AND p.status = 'active'
  AND NOT EXISTS (
    SELECT 1
    FROM sys_role_permission rp
    WHERE rp.role_id = r.id
      AND rp.permission_id = p.id
  );

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key = 'partner_staff'
  AND p.perm_key LIKE 'presale.report.%';
