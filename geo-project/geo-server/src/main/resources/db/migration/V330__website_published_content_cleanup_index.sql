-- Website-owned publications can release hot article data after durable public delivery.
-- This index keeps the scheduled candidate scan bounded by target/status/time.

SET @idx_exists := (
  SELECT COUNT(1)
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'article_publish_record'
     AND index_name = 'idx_article_publish_website_cleanup'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE article_publish_record ADD KEY idx_article_publish_website_cleanup (target_kind, publish_status, published_at, article_id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
