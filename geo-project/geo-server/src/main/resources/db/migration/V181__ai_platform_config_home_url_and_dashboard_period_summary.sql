SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'platform_home_url');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN platform_home_url VARCHAR(512) NULL COMMENT ''AI platform public home URL'' AFTER platform_name', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE ai_platform_config p
JOIN sys_dict_item d
  ON d.dict_type = 'dashboard_platform_jump_url'
 AND d.dict_key = p.platform_code
 AND d.enabled = 1
SET p.platform_home_url = d.dict_value
WHERE (p.platform_home_url IS NULL OR p.platform_home_url = '')
  AND d.dict_value IS NOT NULL
  AND d.dict_value <> '';

SET @idx := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_dashboard_snapshot' AND INDEX_NAME = 'idx_project_dashboard_snapshot_period');
SET @sql := IF(@idx = 0, 'CREATE INDEX idx_project_dashboard_snapshot_period ON project_dashboard_snapshot (project_id, snapshot_type, snapshot_key, refreshed_at)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
