-- Partner owner reviews collaboration state; partner staff performs phase-one data entry.
-- Keep project.start on partner owner because start requests remain owner-only.

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
  'company.read',
  'company.create',
  'company.update',
  'brand.read',
  'brand.create',
  'brand.update',
  'brand.material.upload',
  'brand.material.delete',
  'project.read',
  'project.create',
  'project.update',
  'project.report.read',
  'keyword_group.read',
  'keyword_group.write',
  'package.read'
)
WHERE r.role_key = 'partner_staff'
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
WHERE r.role_key = 'partner'
  AND p.perm_key IN (
    'company.create',
    'company.update',
    'company.delete',
    'brand.create',
    'brand.update',
    'brand.delete',
    'brand.material.upload',
    'brand.material.delete',
    'project.create',
    'project.update',
    'keyword_group.write',
    'company.write',
    'project.write'
  );

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key = 'partner_staff'
  AND p.perm_key IN (
    'project.start',
    'partner.account.recharge.apply',
    'partner.account.adjust',
    'partner.staff.manage'
  );
