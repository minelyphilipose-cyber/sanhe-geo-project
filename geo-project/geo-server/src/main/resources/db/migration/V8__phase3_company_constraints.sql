-- ============================================================
-- V8: phase3 company owner/status constraints
-- ============================================================

DROP TRIGGER IF EXISTS trg_company_before_insert;
DROP TRIGGER IF EXISTS trg_company_before_update;

DELIMITER $$

CREATE TRIGGER trg_company_before_insert
BEFORE INSERT ON company
FOR EACH ROW
BEGIN
    IF NEW.owner_type NOT IN ('direct', 'partner', 'joint') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid owner_type for company';
    END IF;

    IF NEW.owner_type = 'direct' AND NEW.partner_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'direct company must not bind partner_id';
    END IF;

    IF NEW.owner_type IN ('partner', 'joint') AND NEW.partner_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'partner/joint company must bind partner_id';
    END IF;

    IF NEW.status NOT IN ('potential', 'signed', 'inactive') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid status for company';
    END IF;
END $$

CREATE TRIGGER trg_company_before_update
BEFORE UPDATE ON company
FOR EACH ROW
BEGIN
    IF NEW.owner_type NOT IN ('direct', 'partner', 'joint') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid owner_type for company';
    END IF;

    IF NEW.owner_type = 'direct' AND NEW.partner_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'direct company must not bind partner_id';
    END IF;

    IF NEW.owner_type IN ('partner', 'joint') AND NEW.partner_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'partner/joint company must bind partner_id';
    END IF;

    IF NEW.status NOT IN ('potential', 'signed', 'inactive') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid status for company';
    END IF;
END $$

DELIMITER ;
