-- Download is split from export for audit/permission granularity.
-- Existing roles with presale.report.export are granted presale.report.download by default,
-- because users who can export should be able to download their own generated PDF.
INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.report.download', '售前报表-下载 PDF', 'presale', 'download', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.report.download'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT DISTINCT rp.role_id, p_download.id
FROM sys_role_permission rp
JOIN sys_permission p_export ON p_export.id = rp.permission_id
JOIN sys_permission p_download ON p_download.perm_key = 'presale.report.download'
WHERE p_export.perm_key = 'presale.report.export'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission existing
      WHERE existing.role_id = rp.role_id
        AND existing.permission_id = p_download.id
  );
