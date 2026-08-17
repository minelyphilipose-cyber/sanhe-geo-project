-- V364: WP5 data-retention execute permissions. All execution gates remain disabled by default.

DROP PROCEDURE IF EXISTS v364_assert;
DELIMITER $$
CREATE PROCEDURE v364_assert(IN condition_value BOOLEAN, IN error_message VARCHAR(255))
BEGIN
    IF condition_value IS NULL OR condition_value = FALSE THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;
END$$
DELIMITER ;

CALL v364_assert(
    (SELECT COUNT(*) = 3 FROM sys_role
      WHERE role_key IN ('super_admin', 'manager', 'delivery_manager')
        AND status = 'active' AND role_type = 'internal'),
    'V364 requires active internal execute roles'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT seed.perm_key, seed.perm_name, 'data_retention', seed.action, 'active'
FROM (
    SELECT 'data.retention.execute.manual' perm_key, 'Data Retention Execute Manual' perm_name, 'execute_manual' action
    UNION ALL SELECT 'data.retention.execute.scheduler', 'Data Retention Execute Scheduler', 'execute_scheduler'
    UNION ALL SELECT 'data.retention.execute.canary', 'Data Retention Execute Canary', 'execute_canary'
    UNION ALL SELECT 'data.retention.execute.retry', 'Data Retention Execute Retry', 'execute_retry'
    UNION ALL SELECT 'data.retention.execute.reconcile', 'Data Retention Execute Reconcile', 'execute_reconcile'
) seed
WHERE NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.perm_key = seed.perm_key);

CALL v364_assert(
    (SELECT COUNT(*) = 5 FROM sys_permission
      WHERE perm_key IN ('data.retention.execute.manual','data.retention.execute.scheduler',
                         'data.retention.execute.canary','data.retention.execute.retry',
                         'data.retention.execute.reconcile')
        AND module = 'data_retention' AND status = 'active'),
    'V364 execute permission definition mismatch'
);

CALL v364_assert(
    (SELECT COUNT(*) = 0
       FROM sys_role_permission rp
       JOIN sys_role r ON r.id = rp.role_id
       JOIN sys_permission p ON p.id = rp.permission_id
      WHERE p.perm_key LIKE 'data.retention.execute.%'
        AND r.role_key NOT IN ('super_admin', 'manager', 'delivery_manager')),
    'V364 found execute grants on an unapproved role'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'data.retention.execute.manual','data.retention.execute.scheduler','data.retention.execute.canary',
    'data.retention.execute.retry','data.retention.execute.reconcile')
WHERE r.role_key = 'super_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'data.retention.execute.manual','data.retention.execute.scheduler',
    'data.retention.execute.retry','data.retention.execute.reconcile')
WHERE r.role_key = 'manager'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r
JOIN sys_permission p ON p.perm_key = 'data.retention.execute.canary'
WHERE r.role_key = 'delivery_manager'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

CALL v364_assert(
    (SELECT COUNT(*) = 10
       FROM sys_role_permission rp
       JOIN sys_role r ON r.id = rp.role_id
       JOIN sys_permission p ON p.id = rp.permission_id
      WHERE p.perm_key LIKE 'data.retention.execute.%'
        AND r.role_key IN ('super_admin','manager','delivery_manager')),
    'V364 execute role grant mismatch'
);

DROP PROCEDURE v364_assert;
