-- ============================================================
-- V215: self-media publish schedule monitor alerts
-- ============================================================

CREATE TABLE IF NOT EXISTS self_media_publish_schedule_alert (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  schedule_id BIGINT UNSIGNED NOT NULL COMMENT 'Self-media publish schedule id',
  brand_id BIGINT UNSIGNED NULL COMMENT 'Brand id snapshot',
  article_id BIGINT UNSIGNED NULL COMMENT 'Article id snapshot',
  self_media_account_id BIGINT UNSIGNED NULL COMMENT 'Self-media account id snapshot',
  browser_environment_id BIGINT UNSIGNED NULL COMMENT 'Browser environment id snapshot',
  platform VARCHAR(32) NULL COMMENT 'Platform code',
  alert_type VARCHAR(64) NOT NULL COMMENT 'Alert type',
  severity VARCHAR(16) NOT NULL COMMENT 'critical/warning/info',
  status VARCHAR(16) NOT NULL COMMENT 'open/resolved',
  message VARCHAR(512) NOT NULL COMMENT 'Human readable alert message',
  evidence_json TEXT NULL COMMENT 'Snapshot evidence for diagnosis',
  active_key VARCHAR(160) NULL COMMENT 'Unique key while alert is open',
  first_seen_at DATETIME NOT NULL COMMENT 'First time detected',
  last_seen_at DATETIME NOT NULL COMMENT 'Last time detected',
  resolved_at DATETIME NULL COMMENT 'Resolved time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_self_media_schedule_alert_active (active_key),
  KEY idx_self_media_schedule_alert_schedule_status (schedule_id, status, severity),
  KEY idx_self_media_schedule_alert_brand_status (brand_id, status, severity, last_seen_at),
  KEY idx_self_media_schedule_alert_type_status (alert_type, status, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Self-media publish schedule monitor alerts';
