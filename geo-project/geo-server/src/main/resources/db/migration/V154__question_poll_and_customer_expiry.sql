-- ============================================================
-- V154: question poll switch and customer package expiry
-- ============================================================

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'enabled_for_question_poll');
SET @sql := IF(@col = 0,
    'ALTER TABLE ai_platform_config ADD COLUMN enabled_for_question_poll TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''问题池定时跑批启用'' AFTER enabled_for_geo_question',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE ai_platform_config
SET enabled_for_question_poll = enabled_for_geo_question
WHERE enabled_for_question_poll = 0;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_alerts' AND COLUMN_NAME = 'recipient_user_id');
SET @sql := IF(@col = 0,
    'ALTER TABLE system_alerts ADD COLUMN recipient_user_id BIGINT NULL COMMENT ''alert recipient user id'' AFTER context_json',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_alerts' AND COLUMN_NAME = 'recipient_role');
SET @sql := IF(@col = 0,
    'ALTER TABLE system_alerts ADD COLUMN recipient_role VARCHAR(32) NULL COMMENT ''alert recipient role'' AFTER recipient_user_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_alerts' AND COLUMN_NAME = 'dedupe_key');
SET @sql := IF(@col = 0,
    'ALTER TABLE system_alerts ADD COLUMN dedupe_key VARCHAR(191) NULL COMMENT ''business dedupe key'' AFTER recipient_role',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_alerts' AND INDEX_NAME = 'idx_system_alerts_recipient');
SET @sql := IF(@idx = 0,
    'ALTER TABLE system_alerts ADD INDEX idx_system_alerts_recipient (recipient_user_id, is_resolved, created_at)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_alerts' AND INDEX_NAME = 'uk_system_alerts_dedupe');
SET @sql := IF(@idx = 0,
    'ALTER TABLE system_alerts ADD UNIQUE KEY uk_system_alerts_dedupe (dedupe_key)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'system_alerts' AND CONSTRAINT_NAME = 'fk_system_alerts_recipient_user');
SET @sql := IF(@fk = 0,
    'ALTER TABLE system_alerts ADD CONSTRAINT fk_system_alerts_recipient_user FOREIGN KEY (recipient_user_id) REFERENCES sys_user(id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'company_status', 'expired', '已过期', 40
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'company_status' AND dict_key = 'expired'
);

UPDATE sys_dict_item
SET dict_value = '客户套餐到期检查'
WHERE dict_type = 'dispatch_task_type'
  AND dict_key = 'PROJECT_EXPIRE_CHECK';

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

    IF NEW.status NOT IN ('potential', 'signed', 'inactive', 'expired') THEN
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

    IF NEW.status NOT IN ('potential', 'signed', 'inactive', 'expired') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid status for company';
    END IF;
END $$

DELIMITER ;

DROP TABLE IF EXISTS project_platform_binding;
