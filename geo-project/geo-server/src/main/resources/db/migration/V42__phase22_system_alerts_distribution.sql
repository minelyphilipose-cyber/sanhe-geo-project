-- ============================================================
-- V42: system alerts for business events
-- ============================================================

CREATE TABLE IF NOT EXISTS system_alerts (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  alert_type    VARCHAR(64)     NOT NULL,
  severity      VARCHAR(16)     NOT NULL DEFAULT 'warn' COMMENT 'info/warn/error/critical',
  source        VARCHAR(64)     NOT NULL,
  message       VARCHAR(500)    NOT NULL,
  context_json  JSON            NULL,
  is_resolved   TINYINT(1)      NOT NULL DEFAULT 0,
  resolved_by   BIGINT          NULL,
  resolved_at   DATETIME        NULL,
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_system_alerts_type_created (alert_type, created_at),
  KEY idx_system_alerts_resolved (is_resolved, severity, created_at),
  CONSTRAINT fk_system_alerts_resolved_by FOREIGN KEY (resolved_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='system business alerts';
