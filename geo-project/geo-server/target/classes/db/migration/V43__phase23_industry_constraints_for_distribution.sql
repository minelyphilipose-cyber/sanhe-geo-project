-- ============================================================
-- V43: industry constraints for site distribution
-- ============================================================

-- MySQL low-version compatible: conditional add column
SET @brand_industry_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'brand'
      AND COLUMN_NAME = 'industry'
);
SET @brand_industry_sql := IF(
    @brand_industry_exists = 0,
    'ALTER TABLE brand ADD COLUMN industry VARCHAR(64) NOT NULL DEFAULT ''general'' COMMENT ''brand industry tag from dict industry_tag'' AFTER company_id',
    'SELECT 1'
);
PREPARE stmt_brand_industry FROM @brand_industry_sql;
EXECUTE stmt_brand_industry;
DEALLOCATE PREPARE stmt_brand_industry;

SET @company_industry_tags_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'company'
      AND COLUMN_NAME = 'industry_tags'
);
SET @company_industry_tags_sql := IF(
    @company_industry_tags_exists = 0,
    'ALTER TABLE company ADD COLUMN industry_tags JSON NULL COMMENT ''company industry tags from dict industry_tag'' AFTER industry',
    'SELECT 1'
);
PREPARE stmt_company_industry_tags FROM @company_industry_tags_sql;
EXECUTE stmt_company_industry_tags;
DEALLOCATE PREPARE stmt_company_industry_tags;

UPDATE company
SET industry_tags = JSON_ARRAY('general')
WHERE industry_tags IS NULL OR JSON_VALID(industry_tags) = 0 OR JSON_LENGTH(industry_tags) = 0;

UPDATE publish_sites
SET industry_tags = JSON_ARRAY('general')
WHERE industry_tags IS NULL OR JSON_VALID(industry_tags) = 0 OR JSON_LENGTH(industry_tags) = 0;

ALTER TABLE publish_sites
    MODIFY COLUMN industry_tags JSON NOT NULL COMMENT 'site industry tags from dict industry_tag';

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'medical_health', '医疗健康', 10, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'medical_health');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'education_training', '教育培训', 20, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'education_training');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'tech_internet', '科技互联网', 30, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'tech_internet');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'finance_business', '金融财经', 40, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'finance_business');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'legal_services', '法律服务', 50, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'legal_services');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'home_realestate', '房产家居', 60, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'home_realestate');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'food_beverage', '餐饮美食', 70, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'food_beverage');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'beauty_cosmetic', '美容美业', 80, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'beauty_cosmetic');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'auto_transport', '汽车出行', 90, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'auto_transport');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'travel_hotel', '文旅酒店', 100, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'travel_hotel');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'manufacture_industry', '制造工业', 110, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'manufacture_industry');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'agriculture', '农业农资', 120, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'agriculture');
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'industry_tag', 'general', '综合', 130, 1, 'industry tag'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'industry_tag' AND dict_key = 'general');
