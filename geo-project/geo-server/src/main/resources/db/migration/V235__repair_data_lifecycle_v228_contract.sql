-- ============================================================
-- V235: repair V228 lifecycle summary schema for databases that
-- applied an earlier draft before the V228 contract was finalized.
-- ============================================================

CREATE TABLE IF NOT EXISTS data_retention_run (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  domain              VARCHAR(64)  NOT NULL,
  mode                VARCHAR(16)  NOT NULL COMMENT 'dry_run or execute',
  status              VARCHAR(24)  NOT NULL COMMENT 'running/succeeded/failed/skipped',
  retention_window_start DATE      NULL,
  retention_window_end   DATE      NULL,
  candidate_count     BIGINT       NOT NULL DEFAULT 0,
  affected_count      BIGINT       NOT NULL DEFAULT 0,
  skipped_count       BIGINT       NOT NULL DEFAULT 0,
  warning_count       BIGINT       NOT NULL DEFAULT 0,
  metrics_json        JSON         NULL,
  error_message       VARCHAR(2000) NULL,
  approved_by         BIGINT       NULL,
  approved_at         DATETIME     NULL,
  started_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at         DATETIME     NULL,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_retention_run_domain_started (domain, started_at),
  KEY idx_retention_run_status_started (status, started_at),
  KEY idx_retention_run_approval (approved_by, approved_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='data retention dry-run and execute audit records';

CREATE TABLE IF NOT EXISTS data_retention_purged_slice (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  domain              VARCHAR(64)  NOT NULL COMMENT 'first version: poll_results',
  project_id          BIGINT       NOT NULL,
  batch_date          DATE         NOT NULL,
  question_tier       VARCHAR(16)  NOT NULL,
  status              VARCHAR(24)  NOT NULL DEFAULT 'purged',
  purged_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  retention_run_id    BIGINT UNSIGNED NULL,
  metrics_json        JSON         NULL,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_retention_purged_slice (domain, project_id, batch_date, question_tier),
  KEY idx_retention_purged_slice_date (domain, batch_date),
  KEY idx_retention_purged_slice_run (retention_run_id),
  CONSTRAINT fk_retention_purged_slice_run
    FOREIGN KEY (retention_run_id) REFERENCES data_retention_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='markers for slices whose source details were already purged';

CREATE TABLE IF NOT EXISTS data_retention_recompute_slice_lock (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  domain              VARCHAR(64)  NOT NULL COMMENT 'first version: poll_results',
  project_id          BIGINT       NOT NULL,
  batch_date          DATE         NOT NULL,
  question_tier       VARCHAR(16)  NOT NULL,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_retention_recompute_slice_lock (domain, project_id, batch_date, question_tier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='transactional row locks for per-slice retention recompute serialization';

ALTER TABLE poll_keyword_daily_summary
  MODIFY question_tier VARCHAR(16) NOT NULL COMMENT 'poll question tier, currently A/B/C',
  MODIFY dim_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 canonical hash of project_id,batch_date,question_tier,keyword_identity_value',
  MODIFY source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 checksum of ordered source poll_results rows used by recompute';

ALTER TABLE poll_platform_daily_summary
  MODIFY question_tier VARCHAR(16) NOT NULL COMMENT 'poll question tier, currently A/B/C',
  MODIFY dim_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 canonical hash of project_id,batch_date,question_tier,platform_id',
  MODIFY source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 checksum of ordered source poll_results rows used by recompute';

ALTER TABLE llm_usage_daily_summary
  MODIFY dim_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 canonical hash of usage_date,report_id,stage,model_id_snapshot,call_status',
  MODIFY brand_name_snapshot VARCHAR(200) NOT NULL DEFAULT '' COMMENT 'display snapshot from presale_report.brand_name, empty when unavailable',
  MODIFY industry_snapshot VARCHAR(50) NOT NULL DEFAULT '' COMMENT 'display snapshot from presale_report.industry, empty when unavailable',
  MODIFY region_snapshot VARCHAR(100) NOT NULL DEFAULT '' COMMENT 'display snapshot from presale_report.region, empty when unavailable',
  MODIFY source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 checksum of ordered presale_ai_call rows used by recompute';

ALTER TABLE article_generation_daily_summary
  MODIFY dim_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 canonical hash of generation_date,project_id,target_channel,article_status',
  MODIFY source_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 checksum of ordered article generation rows used by recompute';

ALTER TABLE data_retention_purged_slice
  MODIFY question_tier VARCHAR(16) NOT NULL;

ALTER TABLE data_retention_recompute_slice_lock
  MODIFY question_tier VARCHAR(16) NOT NULL;
