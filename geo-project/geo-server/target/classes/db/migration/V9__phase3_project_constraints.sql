-- ============================================================
-- V9: phase3 project owner/status/stage constraints
-- ============================================================

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

    IF NEW.status NOT IN ('draft', 'active', 'paused', 'dispute', 'completed', 'archived') THEN
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

    IF NEW.status NOT IN ('draft', 'active', 'paused', 'dispute', 'completed', 'archived') THEN
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
