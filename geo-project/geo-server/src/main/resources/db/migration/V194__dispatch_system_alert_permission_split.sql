-- ============================================================
-- V194: split delivery dispatch alerts from system alert handling
-- Notes:
--   * dispatch.alert.resolve remains the delivery/dispatch alert handler key
--   * system.alert.resolve is the system alert/todo handler key
--   * dispatch.task.replay.dead_letter keeps its legacy name, but is treated as system dead-letter replay
--   * content.distribution.retry from V193 remains content-distribution retry and is not reused here
-- ============================================================

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'system.alert.resolve', 'System Alert Resolve', 'system_alert', 'resolve', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'system.alert.resolve'
);

-- Ensure legacy dispatch keys exist before granting/asserting the refined boundary.
INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'dispatch.alert.resolve', 'Dispatch Alert Resolve', 'dispatch', 'alert_resolve', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'dispatch.alert.resolve'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'dispatch.task.replay.dead_letter', 'Dispatch Dead Letter Replay', 'dispatch', 'task_replay_dead_letter', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'dispatch.task.replay.dead_letter'
);

-- delivery_manager handles delivery alerts only.
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key = 'dispatch.alert.resolve'
WHERE r.role_key = 'delivery_manager'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- manager handles system alerts and system dead-letter replay, not delivery alerts.
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'system.alert.resolve',
    'dispatch.task.replay.dead_letter'
)
WHERE r.role_key = 'manager'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key IN ('operator', 'delivery_manager')
  AND p.perm_key IN (
      'system.alert.resolve',
      'dispatch.task.replay.dead_letter'
  );

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key = 'manager'
  AND p.perm_key = 'dispatch.alert.resolve';

DROP PROCEDURE IF EXISTS v194_assert_role_permission;

DELIMITER $$
CREATE PROCEDURE v194_assert_role_permission(
    IN role_key_value VARCHAR(64),
    IN perm_key_value VARCHAR(128),
    IN should_have TINYINT
)
BEGIN
    DECLARE actual_count INT DEFAULT 0;
    DECLARE error_message VARCHAR(255);

    SELECT COUNT(*) INTO actual_count
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = role_key_value
      AND p.perm_key = perm_key_value
      AND p.status IN ('active', 'deprecated');

    IF should_have = 1 AND actual_count = 0 THEN
        SET error_message = CONCAT('V194 ASSERT FAILED: ', role_key_value, ' should have ', perm_key_value);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;

    IF should_have = 0 AND actual_count > 0 THEN
        SET error_message = CONCAT('V194 ASSERT FAILED: ', role_key_value, ' should not have ', perm_key_value);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;
END$$
DELIMITER ;

-- delivery_manager: delivery alert fallback, no system alert/replay.
CALL v194_assert_role_permission('delivery_manager', 'dispatch.alert.resolve', 1);
CALL v194_assert_role_permission('delivery_manager', 'system.alert.resolve', 0);
CALL v194_assert_role_permission('delivery_manager', 'dispatch.task.replay.dead_letter', 0);

-- manager: system alert/replay only, no delivery alert handling.
CALL v194_assert_role_permission('manager', 'dispatch.alert.resolve', 0);
CALL v194_assert_role_permission('manager', 'system.alert.resolve', 1);
CALL v194_assert_role_permission('manager', 'dispatch.task.replay.dead_letter', 1);

-- operator: no global/system alert handling or system replay.
CALL v194_assert_role_permission('operator', 'dispatch.alert.resolve', 0);
CALL v194_assert_role_permission('operator', 'system.alert.resolve', 0);
CALL v194_assert_role_permission('operator', 'dispatch.task.replay.dead_letter', 0);

DROP PROCEDURE IF EXISTS v194_assert_role_permission;
