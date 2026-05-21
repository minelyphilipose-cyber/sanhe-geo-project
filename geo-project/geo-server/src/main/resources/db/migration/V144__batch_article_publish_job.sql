-- ============================================================
-- V144: batch article publish scheduling
-- ============================================================

CREATE TABLE IF NOT EXISTS content_batch_publish_job (
  id BIGINT NOT NULL AUTO_INCREMENT,
  publish_mode VARCHAR(16) NOT NULL COMMENT 'now/scheduled',
  status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/completed/partial_failed/failed',
  scheduled_at DATETIME NULL,
  interval_minutes INT NOT NULL DEFAULT 30,
  platform_concurrency INT NOT NULL DEFAULT 1,
  total_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_batch_publish_job_status (status, scheduled_at),
  KEY idx_batch_publish_job_created_by (created_by, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='batch article publish job';

CREATE TABLE IF NOT EXISTS content_batch_publish_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  article_id BIGINT UNSIGNED NOT NULL,
  project_id BIGINT NOT NULL,
  platform_key VARCHAR(32) NOT NULL COMMENT 'agent_site/industry_site',
  content_style VARCHAR(64) NULL,
  target_site_id BIGINT UNSIGNED NULL,
  target_brand_id BIGINT NULL,
  planned_at DATETIME NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed',
  distribution_task_id BIGINT UNSIGNED NULL,
  error_message VARCHAR(1000) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_publish_item_article (job_id, article_id),
  KEY idx_batch_publish_item_due (status, planned_at),
  KEY idx_batch_publish_item_job_status (job_id, status),
  KEY idx_batch_publish_item_article (article_id),
  CONSTRAINT fk_batch_publish_item_job FOREIGN KEY (job_id) REFERENCES content_batch_publish_job(id),
  CONSTRAINT fk_batch_publish_item_article FOREIGN KEY (article_id) REFERENCES article_draft(id),
  CONSTRAINT fk_batch_publish_item_distribution FOREIGN KEY (distribution_task_id) REFERENCES distribution_tasks(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='batch article publish item';
