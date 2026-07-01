-- ============================================================
-- Partner collaboration phase 1 precheck
-- Run manually before applying the phase 1 migrations.
-- This script only creates a temporary table in the current session.
-- ============================================================

SELECT VERSION() AS mysql_version;

DROP TEMPORARY TABLE IF EXISTS tmp_partner_phase1_generated_column_check;

CREATE TEMPORARY TABLE tmp_partner_phase1_generated_column_check (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    active_submitted_project_id BIGINT
        GENERATED ALWAYS AS (IF(status = 'submitted', project_id, NULL)) STORED,
    UNIQUE KEY uk_tmp_active_submitted_project (active_submitted_project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_partner_phase1_generated_column_check (project_id, status)
VALUES
    (1, 'submitted'),
    (1, 'rejected'),
    (1, 'cancelled');

DROP PROCEDURE IF EXISTS partner_phase1_generated_column_assert;

DELIMITER $$
CREATE PROCEDURE partner_phase1_generated_column_assert()
BEGIN
    DECLARE duplicate_key_raised TINYINT(1) DEFAULT 0;

    DECLARE CONTINUE HANDLER FOR 1062
        SET duplicate_key_raised = 1;

    INSERT INTO tmp_partner_phase1_generated_column_check (project_id, status)
    VALUES (1, 'submitted');

    IF duplicate_key_raised <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Partner phase 1 precheck failed: duplicate submitted request was not blocked';
    END IF;
END$$
DELIMITER ;

CALL partner_phase1_generated_column_assert();

DROP PROCEDURE IF EXISTS partner_phase1_generated_column_assert;

SELECT 'ok' AS generated_column_unique_check;

DROP TEMPORARY TABLE IF EXISTS tmp_partner_phase1_generated_column_check;

SELECT COUNT(DISTINCT u.id) AS partner_viewer_user_count
FROM sys_user u
LEFT JOIN sys_user_role ur ON ur.user_id = u.id
LEFT JOIN sys_role r ON r.id = ur.role_id
WHERE u.role = 'partner_viewer'
   OR r.role_key = 'partner_viewer';

SELECT p.status, COUNT(*) AS project_count
FROM project p
GROUP BY p.status
ORDER BY p.status;

SELECT COUNT(*) AS expired_project_count
FROM project
WHERE status = 'expired';

SELECT COUNT(*) AS pending_start_project_count
FROM project
WHERE status = 'pending_start';

DROP PROCEDURE IF EXISTS partner_phase1_precheck_assert;

DELIMITER $$
CREATE PROCEDURE partner_phase1_precheck_assert()
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
            SET MESSAGE_TEXT = 'Partner phase 1 precheck failed: partner_viewer users exist';
    END IF;
END$$
DELIMITER ;

CALL partner_phase1_precheck_assert();

DROP PROCEDURE IF EXISTS partner_phase1_precheck_assert;
