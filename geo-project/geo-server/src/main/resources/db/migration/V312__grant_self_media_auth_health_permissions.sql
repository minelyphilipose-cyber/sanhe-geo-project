-- Preserve the current internal operating scope while making the new permissions
-- independently manageable after rollout.
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT DISTINCT existing.role_id, target.id
FROM sys_role_permission existing
JOIN sys_permission legacy ON legacy.id = existing.permission_id
JOIN sys_permission target ON (
       legacy.perm_key = 'company.read'
       AND target.perm_key = 'self-media.auth-health.read'
     ) OR (
       legacy.perm_key = 'company.update'
       AND target.perm_key IN (
         'self-media.auth-health.verify',
         'self-media.auth-health.policy-manage',
         'self-media.auth-health.audit'
       )
     )
WHERE NOT EXISTS (
  SELECT 1
  FROM sys_role_permission granted
  WHERE granted.role_id = existing.role_id
    AND granted.permission_id = target.id
);
