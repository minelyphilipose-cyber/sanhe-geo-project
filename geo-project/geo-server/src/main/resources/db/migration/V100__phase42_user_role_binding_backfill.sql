-- Backfill role bindings for legacy users after DB grants became authoritative.
-- Some historical accounts only have sys_user.role populated. PermissionService
-- now resolves permissions through sys_user_role, so those users would otherwise
-- log in with an empty permission set.

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_key = u.role AND r.status = 'active'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_user_role ur
    WHERE ur.user_id = u.id
      AND ur.role_id = r.id
);

DROP PROCEDURE IF EXISTS v100_assert_user_role_bindings;

DELIMITER $$
CREATE PROCEDURE v100_assert_user_role_bindings()
BEGIN
    DECLARE missing_count INT DEFAULT 0;

    SELECT COUNT(*) INTO missing_count
    FROM sys_user u
    JOIN sys_role r ON r.role_key = u.role AND r.status = 'active'
    LEFT JOIN sys_user_role ur ON ur.user_id = u.id AND ur.role_id = r.id
    WHERE u.is_active = 1
      AND ur.user_id IS NULL;

    IF missing_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V100 USER ROLE BINDING FAILED: active users missing sys_user_role binding';
    END IF;
END$$
DELIMITER ;

CALL v100_assert_user_role_bindings();

DROP PROCEDURE IF EXISTS v100_assert_user_role_bindings;
