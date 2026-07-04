UPDATE company
SET owner_type = 'partner',
    updated_at = NOW()
WHERE owner_type = 'joint'
  AND partner_id IS NOT NULL;

UPDATE company
SET owner_type = 'direct',
    updated_at = NOW()
WHERE owner_type = 'joint'
  AND partner_id IS NULL;

UPDATE project
SET owner_type = 'partner',
    updated_at = NOW()
WHERE owner_type = 'joint'
  AND partner_id IS NOT NULL;

UPDATE project
SET owner_type = 'direct',
    updated_at = NOW()
WHERE owner_type = 'joint'
  AND partner_id IS NULL;

DELETE FROM sys_dict_item
WHERE dict_type = 'owner_type'
  AND dict_key = 'joint';

DROP TRIGGER IF EXISTS trg_company_before_insert;
DROP TRIGGER IF EXISTS trg_company_before_update;
DROP TRIGGER IF EXISTS trg_project_before_insert;
DROP TRIGGER IF EXISTS trg_project_before_update;

DELIMITER $$

CREATE TRIGGER trg_company_before_insert
BEFORE INSERT ON company
FOR EACH ROW
BEGIN
    IF NEW.owner_type NOT IN ('direct', 'partner') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid owner_type for company';
    END IF;

    IF NEW.owner_type = 'direct' AND NEW.partner_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'direct company must not bind partner_id';
    END IF;

    IF NEW.owner_type = 'partner' AND NEW.partner_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'partner company must bind partner_id';
    END IF;

    IF NEW.status NOT IN ('potential', 'signed', 'inactive', 'expired') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid status for company';
    END IF;
END $$

CREATE TRIGGER trg_company_before_update
BEFORE UPDATE ON company
FOR EACH ROW
BEGIN
    IF NEW.owner_type NOT IN ('direct', 'partner') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid owner_type for company';
    END IF;

    IF NEW.owner_type = 'direct' AND NEW.partner_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'direct company must not bind partner_id';
    END IF;

    IF NEW.owner_type = 'partner' AND NEW.partner_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'partner company must bind partner_id';
    END IF;

    IF NEW.status NOT IN ('potential', 'signed', 'inactive', 'expired') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid status for company';
    END IF;
END $$

CREATE TRIGGER trg_project_before_insert
BEFORE INSERT ON project
FOR EACH ROW
BEGIN
    IF NEW.owner_type NOT IN ('direct', 'partner') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid owner_type for project';
    END IF;

    IF NEW.owner_type = 'direct' AND NEW.partner_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'direct project must not bind partner_id';
    END IF;

    IF NEW.owner_type = 'partner' AND NEW.partner_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'partner project must bind partner_id';
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
    IF NEW.owner_type NOT IN ('direct', 'partner') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid owner_type for project';
    END IF;

    IF NEW.owner_type = 'direct' AND NEW.partner_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'direct project must not bind partner_id';
    END IF;

    IF NEW.owner_type = 'partner' AND NEW.partner_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'partner project must bind partner_id';
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
