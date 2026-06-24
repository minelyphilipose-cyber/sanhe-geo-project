CREATE TABLE IF NOT EXISTS mobile_entity_judge_budget_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scope_type VARCHAR(20) NOT NULL COMMENT 'global/project',
  project_id BIGINT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  daily_call_limit INT NULL,
  monthly_call_limit INT NULL,
  daily_estimated_cost_limit DECIMAL(18,6) NULL,
  monthly_estimated_cost_limit DECIMAL(18,6) NULL,
  updated_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mobile_entity_judge_budget_scope (scope_type, project_id),
  KEY idx_mobile_entity_judge_budget_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE llm_call_observation
  ADD INDEX idx_llm_call_obs_project_feature_time (project_id, feature, occurred_at);

ALTER TABLE llm_call_observation
  ADD INDEX idx_llm_call_obs_run_project_time (run_id, project_id, occurred_at);
