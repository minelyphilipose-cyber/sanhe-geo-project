-- ============================================================
-- V147: simplify project status values
-- ============================================================

DROP TRIGGER IF EXISTS trg_project_before_insert;
DROP TRIGGER IF EXISTS trg_project_before_update;

UPDATE project
SET status = 'pending_start'
WHERE status = 'draft';

UPDATE sys_dict_item
SET dict_value = '已启动', sort_order = 20
WHERE dict_type = 'project_status' AND dict_key = 'active';

UPDATE sys_dict_item
SET dict_value = '已暂停', sort_order = 30
WHERE dict_type = 'project_status' AND dict_key = 'paused';

UPDATE sys_dict_item
SET dict_value = '已过期', sort_order = 40
WHERE dict_type = 'project_status' AND dict_key = 'expired';

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'project_status', 'pending_start', '待启动', 10
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_status' AND dict_key = 'pending_start'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'project_status', 'expired', '已过期', 40
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_status' AND dict_key = 'expired'
);

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

    IF NEW.status NOT IN ('pending_start', 'active', 'paused', 'expired') THEN
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

    IF NEW.status NOT IN ('pending_start', 'active', 'paused', 'expired') THEN
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
