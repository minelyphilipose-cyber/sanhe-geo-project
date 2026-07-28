-- Priority retention safety for poll details and archived article bodies.

ALTER TABLE article_draft_version
    MODIFY COLUMN content_markdown LONGTEXT NULL;

SET @idx_exists := (
  SELECT COUNT(1)
    FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'article_draft_version'
     AND index_name = 'idx_article_version_retention'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE article_draft_version ADD KEY idx_article_version_retention (content_purged_at, content_archived_at, created_at, id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(1)
    FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'report_period_freeze'
     AND column_name = 'snapshot_schema_version'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE report_period_freeze ADD COLUMN snapshot_schema_version INT NOT NULL DEFAULT 1 COMMENT ''COS freeze object schema version'' AFTER version_no',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists := (
  SELECT COUNT(1)
    FROM information_schema.referential_constraints
   WHERE constraint_schema = DATABASE()
     AND table_name = 'poll_batches'
     AND constraint_name = 'fk_poll_batch_dispatch_task'
);
SET @sql := IF(
  @fk_exists > 0,
  'ALTER TABLE poll_batches DROP FOREIGN KEY fk_poll_batch_dispatch_task',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE poll_batches
    MODIFY COLUMN dispatch_task_id BIGINT NULL;

ALTER TABLE poll_batches
    ADD CONSTRAINT fk_poll_batch_dispatch_task
        FOREIGN KEY (dispatch_task_id) REFERENCES dispatch_task(id) ON DELETE SET NULL;

SET @fk_exists := (
  SELECT COUNT(1)
    FROM information_schema.referential_constraints
   WHERE constraint_schema = DATABASE()
     AND table_name = 'poll_results'
     AND constraint_name = 'fk_poll_result_dispatch_task'
);
SET @sql := IF(
  @fk_exists > 0,
  'ALTER TABLE poll_results DROP FOREIGN KEY fk_poll_result_dispatch_task',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE poll_results
    MODIFY COLUMN dispatch_task_id BIGINT NULL;

ALTER TABLE poll_results
    ADD CONSTRAINT fk_poll_result_dispatch_task
        FOREIGN KEY (dispatch_task_id) REFERENCES dispatch_task(id) ON DELETE SET NULL;

SET @fk_exists := (
  SELECT COUNT(1)
    FROM information_schema.referential_constraints
   WHERE constraint_schema = DATABASE()
     AND table_name = 'poll_daily_stats'
     AND constraint_name = 'fk_poll_stats_dispatch_task'
);
SET @sql := IF(
  @fk_exists > 0,
  'ALTER TABLE poll_daily_stats DROP FOREIGN KEY fk_poll_stats_dispatch_task',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE poll_daily_stats
    MODIFY COLUMN dispatch_task_id BIGINT NULL;

ALTER TABLE poll_daily_stats
    ADD CONSTRAINT fk_poll_stats_dispatch_task
        FOREIGN KEY (dispatch_task_id) REFERENCES dispatch_task(id) ON DELETE SET NULL;
