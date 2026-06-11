-- ============================================================
-- V232: article publish records and draft version archive columns
-- ============================================================

CREATE TABLE IF NOT EXISTS article_publish_record (
  id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  article_id            BIGINT       NOT NULL,
  distribution_task_id  BIGINT       NULL,
  project_id            BIGINT       NULL,
  source_type           VARCHAR(32)  NOT NULL COMMENT 'distribution_task/self_media_schedule/manual',
  source_id             BIGINT       NOT NULL COMMENT 'non-null source primary key for idempotency',
  target_kind           VARCHAR(32)  NULL,
  target_channel        VARCHAR(64)  NULL,
  published_url         VARCHAR(1000) NULL,
  platform_article_id   VARCHAR(128) NULL,
  platform_publish_id   VARCHAR(128) NULL,
  publish_status        VARCHAR(32)  NOT NULL DEFAULT 'published',
  published_at          DATETIME     NULL,
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_article_publish_source (source_type, source_id),
  KEY idx_article_publish_record_task (distribution_task_id),
  KEY idx_article_publish_record_article (article_id, published_at),
  KEY idx_article_publish_record_project (project_id, published_at),
  KEY idx_article_publish_record_url (published_url(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='durable published article delivery records used before archiving draft body';

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'article_draft_version'
     AND column_name = 'content_object_key'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_draft_version ADD COLUMN content_object_key VARCHAR(512) NULL COMMENT ''logical object key of archived markdown body'' AFTER content_markdown',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'article_draft_version'
     AND column_name = 'content_checksum'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_draft_version ADD COLUMN content_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''SHA-256 checksum of archived markdown body'' AFTER content_object_key',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'article_draft_version'
     AND column_name = 'content_archived_at'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_draft_version ADD COLUMN content_archived_at DATETIME NULL COMMENT ''time when content_markdown was verified into object storage'' AFTER content_checksum',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'article_draft_version'
     AND column_name = 'content_purged_at'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_draft_version ADD COLUMN content_purged_at DATETIME NULL COMMENT ''time when hot DB content_markdown was nulled after archive verification'' AFTER content_archived_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'article_draft_version'
     AND index_name = 'idx_article_version_archive_state'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE article_draft_version ADD KEY idx_article_version_archive_state (content_archived_at, content_purged_at, article_id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
