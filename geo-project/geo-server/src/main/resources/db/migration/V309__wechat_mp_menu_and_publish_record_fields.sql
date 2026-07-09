-- ============================================================
-- V309: WeChat MP history menu config and publish record fields
-- ============================================================

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'article_publish_record'
     AND column_name = 'self_media_account_id'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_publish_record ADD COLUMN self_media_account_id BIGINT UNSIGNED NULL COMMENT ''self-media account id snapshot'' AFTER project_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'article_publish_record'
     AND column_name = 'brand_id'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_publish_record ADD COLUMN brand_id BIGINT NULL COMMENT ''brand id snapshot'' AFTER self_media_account_id',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'article_publish_record'
     AND column_name = 'title'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_publish_record ADD COLUMN title VARCHAR(255) NULL COMMENT ''display title snapshot'' AFTER publish_status',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'article_publish_record'
     AND column_name = 'cover_url'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_publish_record ADD COLUMN cover_url VARCHAR(1000) NULL COMMENT ''display cover url snapshot'' AFTER title',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'article_publish_record'
     AND column_name = 'digest'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_publish_record ADD COLUMN digest VARCHAR(255) NULL COMMENT ''display digest snapshot'' AFTER cover_url',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'article_publish_record'
     AND column_name = 'raw_response'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_publish_record ADD COLUMN raw_response JSON NULL COMMENT ''platform raw response snapshot'' AFTER digest',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
  SELECT COUNT(1)
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'article_publish_record'
     AND index_name = 'idx_article_publish_record_wechat_mp'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE article_publish_record ADD KEY idx_article_publish_record_wechat_mp (target_channel, self_media_account_id, publish_status, published_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS wechat_menu_config (
  id                    BIGINT PRIMARY KEY AUTO_INCREMENT,
  self_media_account_id BIGINT NOT NULL,
  brand_id              BIGINT NOT NULL,
  authorizer_appid      VARCHAR(64) NOT NULL,
  public_slug           VARCHAR(64) NOT NULL COMMENT 'public random slug',
  menu_name             VARCHAR(32) NOT NULL DEFAULT '往期文章',
  menu_status           VARCHAR(32) NOT NULL DEFAULT 'pending'
    COMMENT 'pending/configured/permission_missing/menu_full/config_failed/manual_required/disabled',
  list_page_url         VARCHAR(1000) NOT NULL,
  backup_menu_json      JSON NULL,
  backup_menu_at        DATETIME NULL,
  last_sync_at          DATETIME NULL,
  last_sync_error       VARCHAR(500) NULL,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_account (self_media_account_id),
  UNIQUE KEY uk_public_slug (public_slug),
  KEY idx_brand (brand_id),
  KEY idx_appid (authorizer_appid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='WeChat MP history article menu config';
