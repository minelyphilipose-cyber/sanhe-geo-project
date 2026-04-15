-- ============================================================
-- V46: postsale report pipeline (biweekly/monthly/quarterly)
-- ============================================================

-- Keep stage_advice fields in reports for compatibility, mark deprecated by comments.
ALTER TABLE reports
  MODIFY COLUMN stage_advice TEXT NULL COMMENT '@Deprecated: use postsale_report_snapshots.stage_advice',
  MODIFY COLUMN stage_advice_input JSON NULL COMMENT '@Deprecated: use postsale_report_snapshots.stage_advice_input';

SET @col_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports' AND COLUMN_NAME = 'pair_report_id'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE reports ADD COLUMN pair_report_id BIGINT UNSIGNED NULL COMMENT ''paired report id (client/internal)''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports' AND COLUMN_NAME = 'is_latest'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE reports ADD COLUMN is_latest TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''whether latest version''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports' AND COLUMN_NAME = 'pdf_generated_at'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE reports ADD COLUMN pdf_generated_at DATETIME NULL COMMENT ''latest pdf generation time''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports' AND INDEX_NAME = 'idx_reports_pair_report'
);
SET @sql := IF(@idx_exists = 0,
  'CREATE INDEX idx_reports_pair_report ON reports(pair_report_id)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports' AND INDEX_NAME = 'idx_reports_project_type_visibility_latest'
);
SET @sql := IF(@idx_exists = 0,
  'CREATE INDEX idx_reports_project_type_visibility_latest ON reports(project_id, report_type, visibility, is_latest, status)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Replace old unique key (project_id, report_type, version_no) with visibility-aware one.
SET @idx_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports' AND INDEX_NAME = 'uk_reports_project_type_version'
);
SET @sql := IF(@idx_exists > 0,
  'DROP INDEX uk_reports_project_type_version ON reports',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports' AND INDEX_NAME = 'uk_reports_project_type_visibility_version'
);
SET @sql := IF(@idx_exists = 0,
  'CREATE UNIQUE INDEX uk_reports_project_type_visibility_version ON reports(project_id, report_type, visibility, version_no)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS postsale_report_snapshots (
  id                        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  report_id                 BIGINT UNSIGNED NOT NULL,
  report_subtype            VARCHAR(16) NOT NULL COMMENT 'biweekly/monthly/quarterly',
  summary_data              JSON NOT NULL,
  trend_data                JSON NULL,
  detail_data               JSON NULL,
  platform_breakdown        JSON NULL,
  comparison_data           JSON NULL,
  target_evaluation         JSON NULL,
  stage_advice              TEXT NULL,
  stage_advice_input        JSON NULL,
  internal_notes            TEXT NULL,
  risk_flags                JSON NULL,
  content_execution_summary JSON NULL,
  methodology_note          TEXT NULL,
  created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_postsale_snapshot_report (report_id),
  KEY idx_postsale_snapshot_subtype (report_subtype),
  CONSTRAINT fk_postsale_snapshot_report FOREIGN KEY (report_id) REFERENCES reports(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='postsale report snapshots for biweekly/monthly/quarterly';

CREATE TABLE IF NOT EXISTS report_generation_config (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  config_key  VARCHAR(64) NOT NULL,
  config_value VARCHAR(1024) NOT NULL,
  description VARCHAR(255) NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_report_generation_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='postsale report generation config';

INSERT INTO report_generation_config(config_key, config_value, description)
SELECT 'risk.hit_rate_drop_threshold', '20', 'hit rate drop threshold percentage'
WHERE NOT EXISTS (SELECT 1 FROM report_generation_config WHERE config_key = 'risk.hit_rate_drop_threshold');

INSERT INTO report_generation_config(config_key, config_value, description)
SELECT 'risk.platform_success_rate_min', '80', 'p0 platform success rate minimum percentage'
WHERE NOT EXISTS (SELECT 1 FROM report_generation_config WHERE config_key = 'risk.platform_success_rate_min');

INSERT INTO report_generation_config(config_key, config_value, description)
SELECT 'risk.data_missing_tolerance_days', '1', 'allowed missing trigger days in period'
WHERE NOT EXISTS (SELECT 1 FROM report_generation_config WHERE config_key = 'risk.data_missing_tolerance_days');

INSERT INTO report_generation_config(config_key, config_value, description)
SELECT 'methodology.default_note', '本报告基于系统监测任务及结构化结果解析生成。双周报与月报的统计窗口独立计算，覆盖时段可能存在重叠。', 'default methodology note'
WHERE NOT EXISTS (SELECT 1 FROM report_generation_config WHERE config_key = 'methodology.default_note');
