-- ============================================================
-- V237: add URL quality metadata to article publish records.
-- V236 is reserved by another branch.
-- ============================================================

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'article_publish_record'
     AND column_name = 'url_quality'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_publish_record ADD COLUMN url_quality VARCHAR(32) NOT NULL DEFAULT ''missing'' COMMENT ''public_url/preview_url/manage_url/missing'' AFTER published_url',
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
     AND column_name = 'url_source'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_publish_record ADD COLUMN url_source VARCHAR(64) NULL COMMENT ''source field used for published_url/url_quality'' AFTER url_quality',
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
     AND column_name = 'verified_at'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE article_publish_record ADD COLUMN verified_at DATETIME NULL COMMENT ''time when published delivery evidence was verified'' AFTER published_at',
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
     AND index_name = 'idx_article_publish_record_quality'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE article_publish_record ADD KEY idx_article_publish_record_quality (url_quality, verified_at)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
