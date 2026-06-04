-- Stage 0 capability records for self-media native scheduling.
-- This table stores platform-level verification results, not brand-specific settings.

CREATE TABLE IF NOT EXISTS self_media_schedule_capability (
  id BIGINT NOT NULL AUTO_INCREMENT,
  platform VARCHAR(32) NOT NULL,
  verification_status VARCHAR(32) NOT NULL DEFAULT 'unverified',
  supports_schedule TINYINT(1) NOT NULL DEFAULT 0,
  min_delay_minutes INT NULL,
  max_delay_minutes INT NULL,
  save_creates_schedule TINYINT(1) NULL,
  supports_cancel TINYINT(1) NULL,
  supports_modify TINYINT(1) NULL,
  supports_publish_check TINYINT(1) NULL,
  v1_strategy VARCHAR(32) NOT NULL DEFAULT 'pending',
  selector_status VARCHAR(32) NULL,
  evidence_json TEXT NULL,
  notes TEXT NULL,
  verified_at DATETIME NULL,
  verified_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_self_media_schedule_capability_platform (platform),
  KEY idx_self_media_schedule_capability_status (verification_status, v1_strategy),
  CONSTRAINT fk_self_media_schedule_capability_verified_by FOREIGN KEY (verified_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='self-media platform schedule capability verification';
