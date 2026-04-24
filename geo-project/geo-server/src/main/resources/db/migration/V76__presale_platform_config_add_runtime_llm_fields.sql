-- =========================================================================
-- V76: presale_platform_config add dedicated runtime llm fields
-- 目标:
--   1) 售前报表调用时仅依赖 presale 白名单配置,与 ai_platform_config.model_id 解耦
--   2) 保留一次性回填,将已存在白名单平台的配置迁移到 presale_platform_config
-- =========================================================================

SET @col := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'presale_platform_config'
      AND COLUMN_NAME = 'api_url'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE presale_platform_config ADD COLUMN api_url VARCHAR(500) NULL COMMENT ''售前调用专用 API 地址'' AFTER platform_code',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'presale_platform_config'
      AND COLUMN_NAME = 'model_id'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE presale_platform_config ADD COLUMN model_id VARCHAR(100) NULL COMMENT ''售前调用专用模型 ID'' AFTER api_url',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'presale_platform_config'
      AND COLUMN_NAME = 'api_key'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE presale_platform_config ADD COLUMN api_key VARCHAR(500) NULL COMMENT ''售前调用专用 API Key(可选明文,优先 key_ref)'' AFTER model_id',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'presale_platform_config'
      AND COLUMN_NAME = 'primary_key_ref'
);
SET @sql := IF(@col = 0,
    'ALTER TABLE presale_platform_config ADD COLUMN primary_key_ref VARCHAR(100) NULL COMMENT ''售前调用专用密钥引用'' AFTER api_key',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 一次性回填: 仅回填 presale 白名单中的行,避免污染非售前平台。
UPDATE presale_platform_config p
JOIN ai_platform_config a ON a.platform_code = p.platform_code
SET p.api_url = CASE WHEN p.api_url IS NULL OR TRIM(p.api_url) = '' THEN a.api_url ELSE p.api_url END,
    p.model_id = CASE WHEN p.model_id IS NULL OR TRIM(p.model_id) = '' THEN a.model_id ELSE p.model_id END,
    p.api_key = CASE WHEN p.api_key IS NULL OR TRIM(p.api_key) = '' THEN a.api_key ELSE p.api_key END,
    p.primary_key_ref = CASE
        WHEN p.primary_key_ref IS NULL OR TRIM(p.primary_key_ref) = '' THEN a.primary_key_ref
        ELSE p.primary_key_ref
    END
WHERE p.in_whitelist = 1;
