-- V77_2 · ai_platform_config 统一 presale 开关 + 运行时字段 + low_model_id 一次性兜底回填
-- 前置: V77_1 已通过

-- 1) enabled_for_presale / enabled_for_article
SET @col := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'enabled_for_presale'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE ai_platform_config ADD COLUMN enabled_for_presale TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否启用售前(需配合 low_model_id 非空才会被 presale 实际使用)'' AFTER enabled',
  'SELECT 1'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'enabled_for_article'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE ai_platform_config ADD COLUMN enabled_for_article TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否启用文章生成(预留字段,前端暂不暴露按钮)'' AFTER enabled_for_presale',
  'SELECT 1'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) presale runtime 字段
SET @col := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'max_retry'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE ai_platform_config ADD COLUMN max_retry INT NOT NULL DEFAULT 2 COMMENT ''最大重试次数'' AFTER primary_key_ref',
  'SELECT 1'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'timeout_ms'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE ai_platform_config ADD COLUMN timeout_ms INT NOT NULL DEFAULT 60000 COMMENT ''调用超时(毫秒)'' AFTER max_retry',
  'SELECT 1'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'rate_limit_qps'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE ai_platform_config ADD COLUMN rate_limit_qps INT NOT NULL DEFAULT 3 COMMENT ''QPS 限流'' AFTER timeout_ms',
  'SELECT 1'
);
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3) low_model_id 空位一次性回填
UPDATE ai_platform_config a
JOIN presale_platform_config p ON a.platform_code = p.platform_code
SET a.low_model_id = p.model_id
WHERE (a.low_model_id IS NULL OR TRIM(a.low_model_id) = '')
  AND p.model_id IS NOT NULL
  AND TRIM(p.model_id) <> '';
