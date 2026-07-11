CREATE TABLE self_media_auth_health_policy (
  id                         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  platform_code              VARCHAR(32)  NOT NULL,
  enabled                    TINYINT(1)   NOT NULL DEFAULT 1,
  reverify_interval_days     INT          NOT NULL,
  warning_days               INT          NOT NULL,
  credential_reference_days INT          NULL,
  credential_expiry_mode     VARCHAR(32)  NOT NULL DEFAULT 'declared_then_reference',
  alert_enabled              TINYINT(1)   NOT NULL DEFAULT 1,
  default_recipient_role     VARCHAR(32)  NULL,
  version                    INT          NOT NULL DEFAULT 1,
  created_by                 BIGINT       NULL,
  updated_by                 BIGINT       NULL,
  created_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_self_media_auth_policy_platform (platform_code),
  CONSTRAINT fk_self_media_auth_policy_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
  CONSTRAINT fk_self_media_auth_policy_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Operator-maintained self-media authorization risk policy';

CREATE TABLE self_media_auth_health_policy_audit (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  policy_id     BIGINT UNSIGNED NOT NULL,
  platform_code VARCHAR(32)     NOT NULL,
  before_json   JSON            NULL,
  after_json    JSON            NOT NULL,
  change_reason VARCHAR(512)    NOT NULL,
  changed_by    BIGINT          NOT NULL,
  changed_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_self_media_auth_policy_audit (policy_id, changed_at),
  CONSTRAINT fk_self_media_auth_policy_audit_policy FOREIGN KEY (policy_id) REFERENCES self_media_auth_health_policy(id),
  CONSTRAINT fk_self_media_auth_policy_audit_user FOREIGN KEY (changed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only authorization health policy audit trail';

CREATE TABLE self_media_login_verification (
  id                             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  brand_id                       BIGINT          NOT NULL,
  self_media_account_id          BIGINT UNSIGNED NOT NULL,
  browser_environment_id         BIGINT UNSIGNED NOT NULL,
  browser_environment_account_id BIGINT UNSIGNED NOT NULL,
  platform                       VARCHAR(32)     NOT NULL,
  expected_account_name          VARCHAR(128)    NOT NULL,
  expected_platform_account_id   VARCHAR(128)    NULL,
  status                         VARCHAR(32)     NOT NULL DEFAULT 'pending',
  result_code                    VARCHAR(64)     NULL,
  result_message                 VARCHAR(512)    NULL,
  actual_account_name            VARCHAR(128)    NULL,
  actual_platform_account_id     VARCHAR(128)    NULL,
  identity_diagnostics           VARCHAR(512)    NULL,
  requested_by                   BIGINT          NOT NULL,
  requested_at                   DATETIME        NOT NULL,
  reported_at                    DATETIME        NULL,
  expires_at                     DATETIME        NOT NULL,
  created_at                     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_login_verification_account_status (self_media_account_id, status, expires_at),
  KEY idx_login_verification_environment (browser_environment_account_id, status, expires_at),
  CONSTRAINT fk_login_verification_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_login_verification_account FOREIGN KEY (self_media_account_id) REFERENCES self_media_account(id),
  CONSTRAINT fk_login_verification_environment FOREIGN KEY (browser_environment_id) REFERENCES browser_environment(id),
  CONSTRAINT fk_login_verification_environment_account FOREIGN KEY (browser_environment_account_id) REFERENCES browser_environment_account(id),
  CONSTRAINT fk_login_verification_requested_by FOREIGN KEY (requested_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='System initiated browser login identity verification';

CREATE TABLE account_auth_risk_scan_batch (
  id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  scan_type       VARCHAR(32)  NOT NULL,
  status          VARCHAR(32)  NOT NULL,
  started_at      DATETIME     NOT NULL,
  finished_at     DATETIME     NULL,
  total_count     INT          NOT NULL DEFAULT 0,
  success_count   INT          NOT NULL DEFAULT 0,
  failure_count   INT          NOT NULL DEFAULT 0,
  skipped_count   INT          NOT NULL DEFAULT 0,
  last_scanned_id BIGINT       NULL,
  error_summary   VARCHAR(1000) NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_auth_risk_scan_type_started (scan_type, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Authorization risk scan coverage and failure summary';

ALTER TABLE self_media_account
  ADD COLUMN last_login_verified_at DATETIME NULL COMMENT 'last successful browser identity verification' AFTER last_auth_error,
  ADD COLUMN last_login_verification_result VARCHAR(64) NULL AFTER last_login_verified_at,
  ADD COLUMN last_login_verification_method VARCHAR(64) NULL AFTER last_login_verification_result,
  ADD COLUMN last_login_verification_warning VARCHAR(512) NULL AFTER last_login_verification_method,
  ADD COLUMN recommended_reverify_at DATETIME NULL AFTER last_login_verification_warning,
  ADD KEY idx_self_media_reverify (recommended_reverify_at, platform, status);

INSERT INTO self_media_auth_health_policy
  (platform_code, enabled, reverify_interval_days, warning_days, credential_reference_days,
   credential_expiry_mode, alert_enabled, default_recipient_role)
VALUES
  ('toutiao', 1, 14, 3, 180, 'declared_then_reference', 1, 'delivery_manager'),
  ('xiaohongshu', 1, 3, 1, 30, 'declared_then_reference', 1, 'delivery_manager'),
  ('zhihu', 1, 14, 3, 180, 'declared_then_reference', 1, 'delivery_manager'),
  ('baijiahao', 1, 7, 2, NULL, 'declared_only', 1, 'delivery_manager'),
  ('douyin', 1, 7, 2, 60, 'declared_then_reference', 1, 'delivery_manager');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT seed.perm_key, seed.perm_name, 'self_media_auth_health', seed.action, 'active'
FROM (
  SELECT 'self-media.auth-health.read' perm_key, 'Self-media auth health read' perm_name, 'read' action
  UNION ALL SELECT 'self-media.auth-health.verify', 'Self-media login verify', 'verify'
  UNION ALL SELECT 'self-media.auth-health.policy-manage', 'Self-media auth policy manage', 'manage'
  UNION ALL SELECT 'self-media.auth-health.audit', 'Self-media auth policy audit', 'audit'
) seed
WHERE NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.perm_key = seed.perm_key);

-- Historical Cookie-time alerts represented an estimate as an authorization fact.
-- Close them during migration; the first unified scan will create non-blocking
-- re-verification reminders with the new alert types when appropriate.
UPDATE system_alerts
SET is_resolved = 1,
    resolved_at = COALESCE(resolved_at, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP
WHERE is_resolved = 0
  AND alert_type IN ('COOKIE_CREDENTIAL_EXPIRED', 'COOKIE_CREDENTIAL_EXPIRING');
