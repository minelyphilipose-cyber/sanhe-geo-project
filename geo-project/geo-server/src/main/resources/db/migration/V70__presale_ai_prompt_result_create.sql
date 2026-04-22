-- =========================================================================
-- V70: create presale_ai_prompt_result
-- Rollback: DROP TABLE presale_ai_prompt_result
-- =========================================================================

CREATE TABLE IF NOT EXISTS presale_ai_prompt_result (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  version_id            BIGINT       NOT NULL,
  batch_no              TINYINT      NOT NULL,
  platform_code         VARCHAR(40)  NOT NULL,
  prompt_template_id    BIGINT       NOT NULL,
  competitor_name       VARCHAR(100) NOT NULL DEFAULT '',
  query_call_id         BIGINT       NOT NULL,
  analyze_call_id       BIGINT       DEFAULT NULL,
  is_mentioned          TINYINT(1)   DEFAULT NULL,
  ranking               INT          DEFAULT NULL,
  sentiment             VARCHAR(10)  DEFAULT NULL,
  mentioned_competitors JSON         DEFAULT NULL,
  scene_advantages      JSON         DEFAULT NULL,
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_version_combo (version_id, batch_no, platform_code, prompt_template_id, competitor_name),
  KEY idx_version_platform_mentioned (version_id, platform_code, is_mentioned)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='presale prompt-level aggregate results';

