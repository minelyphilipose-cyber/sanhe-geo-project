-- V359: WP4 data-retention dynamic switch permissions.
-- This migration deliberately changes RBAC data only. V354 owns the governance tables.

DROP PROCEDURE IF EXISTS v359_assert;
DELIMITER $$
CREATE PROCEDURE v359_assert(IN condition_value BOOLEAN, IN error_message VARCHAR(255))
BEGIN
    IF condition_value IS NULL OR condition_value = FALSE THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;
END$$
DELIMITER ;

CALL v359_assert(
    (SELECT COUNT(*) = 3 FROM sys_role
      WHERE role_key IN ('super_admin', 'manager', 'delivery_manager') AND status = 'active'),
    'V359 requires active super_admin, manager and delivery_manager roles'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT seed.perm_key, seed.perm_name, 'data_retention', seed.action, 'active'
FROM (
    SELECT 'data.retention.switch.read' perm_key, 'Data Retention Switch Read' perm_name, 'switch_read' action
    UNION ALL SELECT 'data.retention.switch.disable', 'Data Retention Switch Disable', 'switch_disable'
    UNION ALL SELECT 'data.retention.switch.emergency_disable', 'Data Retention Switch Emergency Disable', 'switch_emergency_disable'
    UNION ALL SELECT 'data.retention.switch.enable', 'Data Retention Switch Enable', 'switch_enable'
    UNION ALL SELECT 'data.retention.switch.approve', 'Data Retention Switch Approve', 'switch_approve'
    UNION ALL SELECT 'data.retention.switch.reconcile', 'Data Retention Switch Reconcile', 'switch_reconcile'
) seed
WHERE NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.perm_key = seed.perm_key);

CALL v359_assert(
    (SELECT COUNT(*) = 6 FROM sys_permission p
      WHERE (p.perm_key = 'data.retention.switch.read' AND p.perm_name = 'Data Retention Switch Read' AND p.module = 'data_retention' AND p.action = 'switch_read' AND p.status = 'active')
         OR (p.perm_key = 'data.retention.switch.disable' AND p.perm_name = 'Data Retention Switch Disable' AND p.module = 'data_retention' AND p.action = 'switch_disable' AND p.status = 'active')
         OR (p.perm_key = 'data.retention.switch.emergency_disable' AND p.perm_name = 'Data Retention Switch Emergency Disable' AND p.module = 'data_retention' AND p.action = 'switch_emergency_disable' AND p.status = 'active')
         OR (p.perm_key = 'data.retention.switch.enable' AND p.perm_name = 'Data Retention Switch Enable' AND p.module = 'data_retention' AND p.action = 'switch_enable' AND p.status = 'active')
         OR (p.perm_key = 'data.retention.switch.approve' AND p.perm_name = 'Data Retention Switch Approve' AND p.module = 'data_retention' AND p.action = 'switch_approve' AND p.status = 'active')
         OR (p.perm_key = 'data.retention.switch.reconcile' AND p.perm_name = 'Data Retention Switch Reconcile' AND p.module = 'data_retention' AND p.action = 'switch_reconcile' AND p.status = 'active')),
    'V359 data-retention switch permission definition mismatch'
);

CALL v359_assert(
    (SELECT COUNT(*) = 0
       FROM sys_role_permission rp
       JOIN sys_role r ON r.id = rp.role_id
       JOIN sys_permission p ON p.id = rp.permission_id
      WHERE p.perm_key IN (
        'data.retention.switch.read', 'data.retention.switch.disable',
        'data.retention.switch.emergency_disable', 'data.retention.switch.enable',
        'data.retention.switch.approve', 'data.retention.switch.reconcile'
      )
        AND r.role_key NOT IN ('super_admin', 'manager', 'delivery_manager')),
    'V359 found pre-existing switch grants on an unapproved role'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'data.retention.switch.read', 'data.retention.switch.disable',
    'data.retention.switch.emergency_disable', 'data.retention.switch.enable',
    'data.retention.switch.approve', 'data.retention.switch.reconcile'
)
WHERE r.role_key = 'super_admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'data.retention.switch.read', 'data.retention.switch.disable', 'data.retention.switch.reconcile'
)
WHERE r.role_key = 'manager'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'data.retention.switch.read', 'data.retention.switch.emergency_disable'
)
WHERE r.role_key = 'delivery_manager'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

CALL v359_assert(
    (SELECT COUNT(*) = 11
       FROM sys_role_permission rp
       JOIN sys_role r ON r.id = rp.role_id
       JOIN sys_permission p ON p.id = rp.permission_id
      WHERE p.perm_key IN (
        'data.retention.switch.read', 'data.retention.switch.disable',
        'data.retention.switch.emergency_disable', 'data.retention.switch.enable',
        'data.retention.switch.approve', 'data.retention.switch.reconcile'
      )
        AND r.role_key IN ('super_admin', 'manager', 'delivery_manager')),
    'V359 switch role grant mismatch'
);

DROP PROCEDURE v359_assert;
