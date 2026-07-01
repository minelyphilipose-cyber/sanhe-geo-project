-- ============================================================
-- V278: remove partner_viewer from the new partner role model
-- ============================================================

DROP PROCEDURE IF EXISTS v278_assert_no_partner_viewer_users;

DELIMITER $$
CREATE PROCEDURE v278_assert_no_partner_viewer_users()
BEGIN
    DECLARE partner_viewer_user_count INT DEFAULT 0;

    SELECT COUNT(DISTINCT u.id) INTO partner_viewer_user_count
    FROM sys_user u
    LEFT JOIN sys_user_role ur ON ur.user_id = u.id
    LEFT JOIN sys_role r ON r.id = ur.role_id
    WHERE u.role = 'partner_viewer'
       OR r.role_key = 'partner_viewer';

    IF partner_viewer_user_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V278 BLOCKED: partner_viewer users exist; migrate or disable them before removing role entry';
    END IF;
END$$
DELIMITER ;

CALL v278_assert_no_partner_viewer_users();

DROP PROCEDURE IF EXISTS v278_assert_no_partner_viewer_users;

UPDATE sys_role
SET status = 'inactive',
    updated_at = CURRENT_TIMESTAMP
WHERE role_key = 'partner_viewer';

UPDATE sys_dict_item
SET dict_value = '合伙人只读(已移除)',
    sort_order = 999,
    enabled = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE dict_type = 'role'
  AND dict_key = 'partner_viewer';

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
WHERE r.role_key = 'partner_viewer';
