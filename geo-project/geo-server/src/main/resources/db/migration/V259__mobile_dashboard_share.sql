CREATE TABLE IF NOT EXISTS mobile_dashboard_share (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  token_hash CHAR(64) NOT NULL,
  token_prefix VARCHAR(16) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  access_password_hash VARCHAR(120) NULL,
  expires_at DATETIME NOT NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  disabled_at DATETIME NULL,
  last_access_at DATETIME NULL,
  access_count BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_mobile_dashboard_share_token_hash (token_hash),
  KEY idx_mobile_dashboard_share_project_status (project_id, status),
  KEY idx_mobile_dashboard_share_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mobile_dashboard_access_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  share_id BIGINT NULL,
  project_id BIGINT NULL,
  event_type VARCHAR(32) NOT NULL,
  success TINYINT(1) NOT NULL DEFAULT 0,
  fail_reason VARCHAR(64) NULL,
  client_ip_masked VARCHAR(64) NULL,
  client_ip_hash CHAR(64) NULL,
  user_agent VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_mobile_dashboard_access_share_created (share_id, created_at),
  KEY idx_mobile_dashboard_access_project_created (project_id, created_at),
  KEY idx_mobile_dashboard_access_ip_created (client_ip_hash, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
