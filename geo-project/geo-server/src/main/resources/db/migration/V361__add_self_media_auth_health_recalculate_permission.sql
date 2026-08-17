INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'self-media.auth-health.recalculate',
       'Self-media auth health recalculate',
       'self_media_auth_health',
       'recalculate',
       'active'
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission WHERE perm_key = 'self-media.auth-health.recalculate'
);

-- Preserve current operating scope while allowing this action to be managed independently later.
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT DISTINCT existing.role_id, target.id
FROM sys_role_permission existing
JOIN sys_permission legacy
  ON legacy.id = existing.permission_id
 AND legacy.perm_key IN ('self-media.auth-health.policy-manage', 'company.update')
JOIN sys_permission target
  ON target.perm_key = 'self-media.auth-health.recalculate'
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_role_permission granted
  WHERE granted.role_id = existing.role_id
    AND granted.permission_id = target.id
);
