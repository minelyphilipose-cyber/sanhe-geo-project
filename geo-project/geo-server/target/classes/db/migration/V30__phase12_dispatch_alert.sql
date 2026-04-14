-- ============================================================
-- V30: dispatch alert center
-- ============================================================

CREATE TABLE IF NOT EXISTS dispatch_alert (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_code      VARCHAR(64) NOT NULL,
    task_id         BIGINT NULL,
    project_id      BIGINT NULL,
    severity        VARCHAR(16) NOT NULL COMMENT 'info|warn|error|critical',
    status          VARCHAR(16) NOT NULL DEFAULT 'open' COMMENT 'open|resolved',
    title           VARCHAR(255) NOT NULL,
    content         VARCHAR(2000) NULL,
    retry_count     INT NOT NULL DEFAULT 0,
    context_json    JSON NULL,
    resolved_at     DATETIME NULL,
    resolved_by     BIGINT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_dispatch_alert_status (status, severity, created_at),
    KEY idx_dispatch_alert_task (task_id),
    KEY idx_dispatch_alert_project (project_id),
    CONSTRAINT fk_dispatch_alert_task FOREIGN KEY (task_id) REFERENCES dispatch_task(id),
    CONSTRAINT fk_dispatch_alert_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_dispatch_alert_user FOREIGN KEY (resolved_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='dispatch alert center';

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'alert_severity', 'error', '错误', 30
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'alert_severity' AND dict_key = 'error'
);

UPDATE sys_dict_item
SET dict_key = 'warn'
WHERE dict_type = 'alert_severity'
  AND dict_key = 'warning'
  AND NOT EXISTS (
      SELECT 1
      FROM (
          SELECT dict_key
          FROM sys_dict_item
          WHERE dict_type = 'alert_severity' AND dict_key = 'warn'
      ) tmp
  );

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'alert_severity', 'warn', '警告', 20
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'alert_severity' AND dict_key = 'warn'
);
