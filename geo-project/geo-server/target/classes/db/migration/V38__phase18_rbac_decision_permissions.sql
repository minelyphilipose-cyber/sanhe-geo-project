-- ============================================================
-- V38: decision-level RBAC permissions
-- 1) project activate/close
-- 2) question pool core confirm/delete
-- 3) report review (review/publish unified)
-- 4) alert resolve and dead_letter replay
-- ============================================================

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'project.status.activate', 'Project Activate', 'project', 'status_activate', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'project.status.activate');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'project.status.close', 'Project Close', 'project', 'status_close', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'project.status.close');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'question_pool.core.confirm', 'Question Pool Core Confirm', 'question_pool', 'core_confirm', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'question_pool.core.confirm');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'question_pool.core.delete', 'Question Pool Core Delete', 'question_pool', 'core_delete', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'question_pool.core.delete');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'report.review', 'Report Review', 'report', 'review', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'report.review');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'dispatch.alert.resolve', 'Dispatch Alert Resolve', 'dispatch', 'alert_resolve', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'dispatch.alert.resolve');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'dispatch.task.replay.dead_letter', 'Dispatch Dead Letter Replay', 'dispatch', 'task_replay_dead_letter', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'dispatch.task.replay.dead_letter');

-- manager grants
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'project.status.activate', 'project.status.close',
    'question_pool.core.confirm', 'question_pool.core.delete',
    'report.review', 'dispatch.alert.resolve', 'dispatch.task.replay.dead_letter'
)
WHERE r.role_key = 'manager'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission x
      WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- delivery_manager grants
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'project.status.activate', 'project.status.close',
    'question_pool.core.confirm', 'question_pool.core.delete',
    'report.review', 'dispatch.alert.resolve'
)
WHERE r.role_key = 'delivery_manager'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission x
      WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- super_admin grants (explicit, even though has full fallback)
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'project.status.activate', 'project.status.close',
    'question_pool.core.confirm', 'question_pool.core.delete',
    'report.review', 'dispatch.alert.resolve', 'dispatch.task.replay.dead_letter'
)
WHERE r.role_key = 'super_admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission x
      WHERE x.role_id = r.id AND x.permission_id = p.id
  );
