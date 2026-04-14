-- ============================================================
-- V28: project biweekly anchor + expired status fields
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'biweekly_anchor_date'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN biweekly_anchor_date DATE NULL COMMENT ''first biweekly report monday'' AFTER activated_at',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project'
      AND column_name = 'expired_at'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE project ADD COLUMN expired_at DATETIME NULL COMMENT ''actual expiration datetime'' AFTER end_date',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

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

    IF NEW.status NOT IN ('draft', 'active', 'paused', 'dispute', 'completed', 'archived', 'expired') THEN
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

    IF NEW.status NOT IN ('draft', 'active', 'paused', 'dispute', 'completed', 'archived', 'expired') THEN
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

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'project_status', 'expired', '已失效', 70
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'project_status' AND dict_key = 'expired'
);
