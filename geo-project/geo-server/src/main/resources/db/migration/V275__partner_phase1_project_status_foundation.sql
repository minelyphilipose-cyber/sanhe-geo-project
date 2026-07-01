-- ============================================================
-- V275: partner collaboration phase 1 project status foundation
-- ============================================================

DROP PROCEDURE IF EXISTS v275_assert_no_partner_viewer_users;

DELIMITER $$
CREATE PROCEDURE v275_assert_no_partner_viewer_users()
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
            SET MESSAGE_TEXT = 'V275 BLOCKED: partner_viewer users exist; migrate or disable them before partner phase 1';
    END IF;
END$$
DELIMITER ;

CALL v275_assert_no_partner_viewer_users();

DROP PROCEDURE IF EXISTS v275_assert_no_partner_viewer_users;

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT seed.dict_type, seed.dict_key, seed.dict_value, seed.sort_order
FROM (
    SELECT 'project_status' AS dict_type, 'draft' AS dict_key, '草稿' AS dict_value, 10 AS sort_order
    UNION ALL SELECT 'project_status', 'pending_start', '待启动', 15
    UNION ALL SELECT 'project_status', 'submitted', '已提交', 20
    UNION ALL SELECT 'project_status', 'rejected', '已驳回', 30
    UNION ALL SELECT 'project_status', 'approved_pending_setup', '已审批待配置', 40
    UNION ALL SELECT 'project_status', 'setup_ready', '配置完成待启动', 50
    UNION ALL SELECT 'project_status', 'active', '已启动', 60
    UNION ALL SELECT 'project_status', 'paused', '已暂停', 70
    UNION ALL SELECT 'project_status', 'completed', '已完成', 80
    UNION ALL SELECT 'project_status', 'archived', '已归档', 90
    UNION ALL SELECT 'project_status', 'cancelled', '已取消', 100
    UNION ALL SELECT 'project_status', 'expired', '已过期', 110
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_dict_item existing
    WHERE existing.dict_type = seed.dict_type
      AND existing.dict_key = seed.dict_key
);

UPDATE sys_dict_item item
JOIN (
    SELECT 'project_status' AS dict_type, 'draft' AS dict_key, '草稿' AS dict_value, 10 AS sort_order
    UNION ALL SELECT 'project_status', 'pending_start', '待启动', 15
    UNION ALL SELECT 'project_status', 'submitted', '已提交', 20
    UNION ALL SELECT 'project_status', 'rejected', '已驳回', 30
    UNION ALL SELECT 'project_status', 'approved_pending_setup', '已审批待配置', 40
    UNION ALL SELECT 'project_status', 'setup_ready', '配置完成待启动', 50
    UNION ALL SELECT 'project_status', 'active', '已启动', 60
    UNION ALL SELECT 'project_status', 'paused', '已暂停', 70
    UNION ALL SELECT 'project_status', 'completed', '已完成', 80
    UNION ALL SELECT 'project_status', 'archived', '已归档', 90
    UNION ALL SELECT 'project_status', 'cancelled', '已取消', 100
    UNION ALL SELECT 'project_status', 'expired', '已过期', 110
) seed ON seed.dict_type = item.dict_type AND seed.dict_key = item.dict_key
SET item.dict_value = seed.dict_value,
    item.sort_order = seed.sort_order;

DROP TRIGGER IF EXISTS trg_project_before_insert;
DROP TRIGGER IF EXISTS trg_project_before_update;

DELIMITER $$

CREATE TRIGGER trg_project_before_insert
BEFORE INSERT ON project
FOR EACH ROW
BEGIN
    IF NEW.owner_type NOT IN ('direct', 'partner', 'joint') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid owner_type for project';
    END IF;

    IF NEW.owner_type = 'direct' AND NEW.partner_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'direct project must not bind partner_id';
    END IF;

    IF NEW.owner_type IN ('partner', 'joint') AND NEW.partner_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'partner/joint project must bind partner_id';
    END IF;

    IF NEW.status NOT IN (
        'draft',
        'pending_start',
        'submitted',
        'rejected',
        'approved_pending_setup',
        'setup_ready',
        'active',
        'paused',
        'completed',
        'archived',
        'cancelled',
        'expired'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid status for project';
    END IF;

    IF NEW.stage NOT IN (
        'pending_start',
        'collecting_materials',
        'baseline_diagnosis',
        'building_questions',
        'executing',
        'biweekly_feedback',
        'monthly_report',
        'quarterly_report',
        'needs_renewal',
        'high_risk',
        'dispute_handling',
        'completed'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid stage for project';
    END IF;
END $$

CREATE TRIGGER trg_project_before_update
BEFORE UPDATE ON project
FOR EACH ROW
BEGIN
    IF NEW.owner_type NOT IN ('direct', 'partner', 'joint') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid owner_type for project';
    END IF;

    IF NEW.owner_type = 'direct' AND NEW.partner_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'direct project must not bind partner_id';
    END IF;

    IF NEW.owner_type IN ('partner', 'joint') AND NEW.partner_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'partner/joint project must bind partner_id';
    END IF;

    IF NEW.status NOT IN (
        'draft',
        'pending_start',
        'submitted',
        'rejected',
        'approved_pending_setup',
        'setup_ready',
        'active',
        'paused',
        'completed',
        'archived',
        'cancelled',
        'expired'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid status for project';
    END IF;

    IF NEW.stage NOT IN (
        'pending_start',
        'collecting_materials',
        'baseline_diagnosis',
        'building_questions',
        'executing',
        'biweekly_feedback',
        'monthly_report',
        'quarterly_report',
        'needs_renewal',
        'high_risk',
        'dispute_handling',
        'completed'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid stage for project';
    END IF;
END $$

DELIMITER ;
