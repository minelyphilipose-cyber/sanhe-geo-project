CREATE TABLE IF NOT EXISTS project_competitor_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  competitor_name VARCHAR(128) NOT NULL,
  aliases_json JSON NULL,
  display_order INT NOT NULL DEFAULT 1,
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  qa_status VARCHAR(20) NOT NULL DEFAULT 'pending',
  qa_checked_at DATETIME NULL,
  config_version INT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_project_competitor_order (project_id, display_order),
  KEY idx_project_competitor_project_status (project_id, status, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS poll_result_entity_judge (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  poll_result_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  keyword_result_id BIGINT NULL,
  batch_date DATE NOT NULL,
  question_tier VARCHAR(8) NOT NULL,
  platform_id BIGINT NULL,
  platform_code VARCHAR(64) NULL,
  entity_type VARCHAR(20) NOT NULL COMMENT 'focus_brand/competitor',
  entity_ref_id BIGINT NOT NULL DEFAULT 0 COMMENT '0 for focus brand, project_competitor_config.id for competitor',
  entity_config_version INT NOT NULL DEFAULT 1,
  judge_prompt_version VARCHAR(64) NOT NULL,
  judge_model VARCHAR(128) NULL,
  judge_status VARCHAR(20) NOT NULL DEFAULT 'pending',
  recommended TINYINT(1) NULL,
  first_recommend TINYINT(1) NULL,
  rank_position INT NULL,
  evidence VARCHAR(500) NULL,
  matched_alias VARCHAR(128) NULL,
  confidence DECIMAL(5,4) NULL,
  raw_response_json JSON NULL,
  judge_error VARCHAR(500) NULL,
  judged_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_poll_entity_judge_idem (poll_result_id, entity_type, entity_ref_id, entity_config_version, judge_prompt_version),
  KEY idx_poll_entity_judge_project_date (project_id, batch_date, platform_code, entity_type, entity_ref_id),
  KEY idx_poll_entity_judge_status (judge_status, updated_at),
  CONSTRAINT fk_poll_entity_judge_result FOREIGN KEY (poll_result_id) REFERENCES poll_results(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS poll_entity_judge_daily_summary (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  batch_date DATE NOT NULL,
  question_tier VARCHAR(8) NOT NULL,
  platform_code VARCHAR(64) NULL,
  entity_type VARCHAR(20) NOT NULL,
  entity_ref_id BIGINT NOT NULL DEFAULT 0,
  entity_config_version INT NOT NULL DEFAULT 1,
  judge_prompt_version VARCHAR(64) NOT NULL,
  expected_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  recommended_count INT NOT NULL DEFAULT 0,
  first_recommend_count INT NOT NULL DEFAULT 0,
  source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  recomputed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_poll_entity_judge_daily (project_id, batch_date, question_tier, platform_code, entity_type, entity_ref_id, entity_config_version, judge_prompt_version),
  KEY idx_poll_entity_judge_daily_project (project_id, batch_date, entity_type, entity_ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'project.competitor.manage', 'Project Competitor Manage', 'project', 'competitor_manage', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'project.competitor.manage');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r
  JOIN sys_permission p ON p.perm_key = 'project.competitor.manage'
 WHERE r.role_key IN ('super_admin', 'admin', 'manager', 'operator');
