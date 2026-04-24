-- =========================================================================
-- V80: create presale_ai_prompt_judge_result
-- Rollback: DROP TABLE presale_ai_prompt_judge_result
-- =========================================================================

CREATE TABLE IF NOT EXISTS presale_ai_prompt_judge_result (
  id BIGINT NOT NULL AUTO_INCREMENT,
  prompt_result_id BIGINT NOT NULL,
  version_id BIGINT NOT NULL,
  batch_no TINYINT NOT NULL,
  platform_code VARCHAR(40) NOT NULL,
  prompt_template_id BIGINT NOT NULL,
  category VARCHAR(20) NOT NULL COMMENT '意图枚举: COGNITIVE|COMPARISON',
  competitor_name VARCHAR(100) NOT NULL DEFAULT '',

  judge_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  judge_attempt_count TINYINT NOT NULL DEFAULT 0,
  judge_model_id VARCHAR(128) DEFAULT NULL,
  judge_temperature DECIMAL(4,2) DEFAULT NULL,
  judge_error VARCHAR(500) DEFAULT NULL,

  sentiment VARCHAR(10) DEFAULT NULL,
  sentiment_score DECIMAL(6,4) DEFAULT NULL,
  attribute_hit_rate DECIMAL(6,4) DEFAULT NULL,
  tone VARCHAR(20) DEFAULT NULL,

  preferred_brand VARCHAR(20) DEFAULT NULL COMMENT '对比裁判枚举: target|competitor|tie|unclear',
  target_sentiment VARCHAR(10) DEFAULT NULL,
  reasoning_quality VARCHAR(10) DEFAULT NULL,

  attributes_hit JSON DEFAULT NULL,
  factual_errors JSON DEFAULT NULL,
  target_advantages JSON DEFAULT NULL,
  target_disadvantages JSON DEFAULT NULL,
  competitor_advantages JSON DEFAULT NULL,

  judge_payload_json JSON DEFAULT NULL,
  raw_judge_response LONGTEXT DEFAULT NULL,

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (id),
  UNIQUE KEY uk_prompt_result_id (prompt_result_id),
  KEY idx_version_category_platform (version_id, category, platform_code),
  KEY idx_version_batch_category (version_id, batch_no, category),
  KEY idx_version_status (version_id, judge_status),
  CONSTRAINT fk_judge_prompt_result
    FOREIGN KEY (prompt_result_id) REFERENCES presale_ai_prompt_result(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='presale prompt judge results';
