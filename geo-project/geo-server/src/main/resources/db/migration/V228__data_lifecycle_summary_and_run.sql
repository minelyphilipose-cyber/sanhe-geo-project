-- ============================================================
-- V228: data lifecycle summary tables, retention run audit, and purged slice markers
-- ============================================================

SET @idx_exists := (
  SELECT COUNT(1)
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'poll_results'
     AND index_name = 'idx_poll_result_date_project_tier'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE poll_results ADD KEY idx_poll_result_date_project_tier (batch_date, project_id, question_tier)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS poll_keyword_daily_summary (
  id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id                  BIGINT       NOT NULL,
  batch_date                  DATE         NOT NULL,
  question_tier               VARCHAR(16)  NOT NULL COMMENT 'poll question tier, currently A/B/C',
  keyword_identity_type       VARCHAR(8)   NOT NULL COMMENT 'ID or TEXT',
  keyword_identity_value      VARCHAR(1000) NOT NULL COMMENT 'ID:<keyword_result_id> or TEXT:<normalized text>',
  dim_hash                    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 canonical hash of project_id,batch_date,question_tier,keyword_identity_value',
  keyword_result_id           BIGINT       NULL,
  keyword_text_snapshot       VARCHAR(1000) NULL,
  keyword_text_normalized     VARCHAR(1000) NULL,
  source_row_count            INT          NOT NULL DEFAULT 0 COMMENT 'denominator: live poll_results rows in this keyword slice',
  platform_count              INT          NOT NULL DEFAULT 0 COMMENT 'COUNT(DISTINCT platform_id) among live poll_results rows',
  completed_count             INT          NOT NULL DEFAULT 0 COMMENT 'rows whose status=completed',
  failed_count                INT          NOT NULL DEFAULT 0 COMMENT 'rows whose status=failed or record_type=error',
  hit_count                   INT          NOT NULL DEFAULT 0 COMMENT 'rows whose is_hit=1, natural hit signal',
  effective_hit_count         INT          NOT NULL DEFAULT 0 COMMENT 'rows whose effective_hit=1, judged effective hit signal',
  site_mention_count          INT          NOT NULL DEFAULT 0 COMMENT 'rows whose site_mentioned=1',
  contact_mention_count       INT          NOT NULL DEFAULT 0 COMMENT 'rows whose contact_mentioned=1',
  contact_mention_total       BIGINT       NOT NULL DEFAULT 0 COMMENT 'SUM(contact_mention_count) from poll_results, null as 0',
  request_count_total         BIGINT       NOT NULL DEFAULT 0 COMMENT 'SUM(request_count) from poll_results, null as 0; not row count',
  response_time_ms_total      BIGINT       NOT NULL DEFAULT 0 COMMENT 'SUM(response_time_ms) from poll_results, null as 0',
  last_source_created_at      DATETIME     NULL,
  last_source_updated_at      DATETIME     NULL,
  source_checksum             CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 checksum of ordered source poll_results rows used by recompute',
  recomputed_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_poll_keyword_daily_dim_hash (dim_hash),
  KEY idx_poll_keyword_daily_slice (project_id, batch_date, question_tier),
  KEY idx_poll_keyword_daily_keyword (project_id, keyword_result_id, batch_date),
  KEY idx_poll_keyword_daily_date (batch_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='daily recomputed poll summary by project, date, tier, and keyword identity';

CREATE TABLE IF NOT EXISTS poll_platform_daily_summary (
  id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id                  BIGINT       NOT NULL,
  batch_date                  DATE         NOT NULL,
  question_tier               VARCHAR(16)  NOT NULL COMMENT 'poll question tier, currently A/B/C',
  platform_id                 BIGINT       NOT NULL,
  dim_hash                    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 canonical hash of project_id,batch_date,question_tier,platform_id',
  platform_code               VARCHAR(64)  NOT NULL,
  platform_name_snapshot      VARCHAR(128) NULL,
  source_row_count            INT          NOT NULL DEFAULT 0 COMMENT 'denominator: live poll_results rows in this platform slice',
  completed_count             INT          NOT NULL DEFAULT 0 COMMENT 'rows whose status=completed',
  failed_count                INT          NOT NULL DEFAULT 0 COMMENT 'rows whose status=failed or record_type=error',
  hit_count                   INT          NOT NULL DEFAULT 0 COMMENT 'rows whose is_hit=1, natural hit signal',
  effective_hit_count         INT          NOT NULL DEFAULT 0 COMMENT 'rows whose effective_hit=1, judged effective hit signal',
  site_mention_count          INT          NOT NULL DEFAULT 0 COMMENT 'rows whose site_mentioned=1',
  contact_mention_count       INT          NOT NULL DEFAULT 0 COMMENT 'rows whose contact_mentioned=1',
  contact_mention_total       BIGINT       NOT NULL DEFAULT 0 COMMENT 'SUM(contact_mention_count) from poll_results, null as 0',
  request_count_total         BIGINT       NOT NULL DEFAULT 0 COMMENT 'SUM(request_count) from poll_results, null as 0; not row count',
  response_time_ms_total      BIGINT       NOT NULL DEFAULT 0 COMMENT 'SUM(response_time_ms) from poll_results, null as 0',
  last_source_created_at      DATETIME     NULL,
  last_source_updated_at      DATETIME     NULL,
  source_checksum             CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 checksum of ordered source poll_results rows used by recompute',
  recomputed_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_poll_platform_daily_dim_hash (dim_hash),
  KEY idx_poll_platform_daily_slice (project_id, batch_date, question_tier),
  KEY idx_poll_platform_daily_platform (project_id, platform_id, batch_date),
  KEY idx_poll_platform_daily_date (batch_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='daily recomputed poll summary by project, date, tier, and platform';

CREATE TABLE IF NOT EXISTS llm_usage_daily_summary (
  id                              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  usage_date                      DATE         NOT NULL,
  report_id                       BIGINT       NOT NULL,
  stage                           VARCHAR(40)  NOT NULL,
  model_id_snapshot               VARCHAR(128) NULL,
  call_status                     VARCHAR(20)  NOT NULL,
  dim_hash                        CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 canonical hash of usage_date,report_id,stage,model_id_snapshot,call_status',
  brand_name_snapshot             VARCHAR(200) NOT NULL DEFAULT '' COMMENT 'display snapshot from presale_report.brand_name, empty when unavailable',
  industry_snapshot               VARCHAR(50)  NOT NULL DEFAULT '' COMMENT 'display snapshot from presale_report.industry, empty when unavailable',
  region_snapshot                 VARCHAR(100) NOT NULL DEFAULT '' COMMENT 'display snapshot from presale_report.region, empty when unavailable',
  model_name_snapshot             VARCHAR(128) NULL,
  source_row_count                INT          NOT NULL DEFAULT 0 COMMENT 'denominator: presale_ai_call rows in this usage dimension',
  retry_count_total               INT          NOT NULL DEFAULT 0 COMMENT 'SUM(retry_count) from presale_ai_call, null as 0',
  prompt_tokens_total             BIGINT       NOT NULL DEFAULT 0 COMMENT 'SUM(prompt_tokens) from presale_ai_call, null as 0',
  completion_tokens_total         BIGINT       NOT NULL DEFAULT 0 COMMENT 'SUM(completion_tokens) from presale_ai_call, null as 0',
  total_tokens                    BIGINT       NOT NULL DEFAULT 0 COMMENT 'prompt_tokens_total + completion_tokens_total',
  duration_ms_total               BIGINT       NOT NULL DEFAULT 0 COMMENT 'SUM(duration_ms) from presale_ai_call, null as 0',
  raw_response_non_null_count     INT          NOT NULL DEFAULT 0 COMMENT 'rows whose raw_response is not null and not empty',
  request_prompt_non_null_count   INT          NOT NULL DEFAULT 0 COMMENT 'rows whose request_prompt_content is not null and not empty',
  last_call_created_at            DATETIME     NULL,
  source_checksum                 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 checksum of ordered presale_ai_call rows used by recompute',
  recomputed_at                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_llm_usage_daily_dim_hash (dim_hash),
  KEY idx_llm_usage_daily_report (report_id, usage_date),
  KEY idx_llm_usage_daily_date (usage_date),
  KEY idx_llm_usage_daily_model (model_id_snapshot, usage_date),
  KEY idx_llm_usage_daily_status (call_status, usage_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='presale-only daily LLM usage summary by report, stage, model, and status';

CREATE TABLE IF NOT EXISTS article_generation_daily_summary (
  id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  generation_date             DATE         NOT NULL,
  project_id                  BIGINT       NOT NULL,
  article_type                VARCHAR(32)  NOT NULL,
  target_channel              VARCHAR(64)  NULL,
  status                      VARCHAR(32)  NOT NULL,
  dim_hash                    CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 canonical hash of generation_date,project_id,article_type,target_channel,status',
  source_row_count            INT          NOT NULL DEFAULT 0 COMMENT 'denominator: article_draft rows in this generation dimension',
  version_count               INT          NOT NULL DEFAULT 0 COMMENT 'COUNT(article_draft_version rows) for source articles',
  published_count             INT          NOT NULL DEFAULT 0 COMMENT 'article_draft rows whose status=published',
  content_markdown_non_null_count INT      NOT NULL DEFAULT 0 COMMENT 'article_draft_version rows whose content_markdown is not null and not empty',
  last_source_created_at      DATETIME     NULL,
  last_source_updated_at      DATETIME     NULL,
  source_checksum             CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 checksum of ordered article rows used by recompute',
  recomputed_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_article_generation_daily_dim_hash (dim_hash),
  KEY idx_article_generation_daily_project (project_id, generation_date),
  KEY idx_article_generation_daily_status (status, generation_date),
  KEY idx_article_generation_daily_channel (target_channel, generation_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='daily article generation summary by project, type, channel, and status';

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
