-- =========================================================================
-- V69: create presale_ai_call
-- Rollback: DROP TABLE presale_ai_call
-- =========================================================================

CREATE TABLE IF NOT EXISTS presale_ai_call (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  version_id         BIGINT       NOT NULL,
  batch_no           TINYINT      NOT NULL,
  platform_code      VARCHAR(40)  NOT NULL,
  prompt_template_id BIGINT       NOT NULL,
  competitor_name    VARCHAR(100) DEFAULT NULL,
  stage              VARCHAR(10)  NOT NULL,
  parent_call_id     BIGINT       DEFAULT NULL,
  call_status        VARCHAR(20)  NOT NULL,
  retry_count        TINYINT      NOT NULL DEFAULT 0,
  raw_response       LONGTEXT     DEFAULT NULL,
  failure_reason     VARCHAR(500) DEFAULT NULL,
  prompt_tokens      INT          DEFAULT NULL,
  completion_tokens  INT          DEFAULT NULL,
  duration_ms        INT          DEFAULT NULL,
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_version_stage (version_id, stage),
  KEY idx_version_batch (version_id, batch_no),
  KEY idx_parent (parent_call_id),
  KEY idx_version_platform (version_id, platform_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='presale llm call records';

