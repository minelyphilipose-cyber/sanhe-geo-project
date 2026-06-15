SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand' AND column_name = 'coverable_industries') = 0,
  'ALTER TABLE brand ADD COLUMN coverable_industries JSON NULL COMMENT ''third-party source covered industries; ["__ALL__"] covers all'' AFTER compliance_industry_code',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'brand' AND column_name = 'allow_third_party_promotion') = 0,
  'ALTER TABLE brand ADD COLUMN allow_third_party_promotion TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''whether this brand can be used as third-party article subject'' AFTER coverable_industries',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND column_name = 'source_brand_id') = 0,
  'ALTER TABLE batch_article_generation_task ADD COLUMN source_brand_id BIGINT NULL COMMENT ''publishing/source brand frozen at task creation'' AFTER project_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND column_name = 'subject_brand_id') = 0,
  'ALTER TABLE batch_article_generation_task ADD COLUMN subject_brand_id BIGINT NULL COMMENT ''content subject brand frozen at task creation'' AFTER source_brand_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND column_name = 'subject_project_id') = 0,
  'ALTER TABLE batch_article_generation_task ADD COLUMN subject_project_id BIGINT NULL COMMENT ''stable content subject project frozen at task creation'' AFTER subject_brand_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE batch_article_generation_task t
JOIN project p ON p.id = t.project_id
SET t.source_brand_id = COALESCE(t.source_brand_id, p.brand_id),
    t.subject_brand_id = COALESCE(t.subject_brand_id, p.brand_id),
    t.subject_project_id = COALESCE(t.subject_project_id, t.project_id)
WHERE t.source_brand_id IS NULL
   OR t.subject_brand_id IS NULL
   OR t.subject_project_id IS NULL;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND column_name = 'source_brand_id') = 0,
  'ALTER TABLE article_draft ADD COLUMN source_brand_id BIGINT NULL COMMENT ''publishing/source brand frozen at generation'' AFTER project_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND column_name = 'subject_brand_id') = 0,
  'ALTER TABLE article_draft ADD COLUMN subject_brand_id BIGINT NULL COMMENT ''content subject brand frozen at generation'' AFTER source_brand_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND column_name = 'subject_project_id') = 0,
  'ALTER TABLE article_draft ADD COLUMN subject_project_id BIGINT NULL COMMENT ''content subject project frozen at generation'' AFTER subject_brand_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE article_draft a
JOIN project p ON p.id = a.project_id
SET a.source_brand_id = COALESCE(a.source_brand_id, p.brand_id),
    a.subject_brand_id = COALESCE(a.subject_brand_id, p.brand_id),
    a.subject_project_id = COALESCE(a.subject_project_id, a.project_id)
WHERE a.source_brand_id IS NULL
   OR a.subject_brand_id IS NULL
   OR a.subject_project_id IS NULL;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND index_name = 'idx_batch_task_source_subject_time') = 0,
  'ALTER TABLE batch_article_generation_task ADD KEY idx_batch_task_source_subject_time (source_brand_id, subject_brand_id, created_at)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'batch_article_generation_task' AND index_name = 'idx_batch_task_source_subject_type_time') = 0,
  'ALTER TABLE batch_article_generation_task ADD KEY idx_batch_task_source_subject_type_time (source_brand_id, subject_brand_id, article_type, created_at)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'article_draft' AND index_name = 'idx_article_draft_source_subject_type_time') = 0,
  'ALTER TABLE article_draft ADD KEY idx_article_draft_source_subject_type_time (source_brand_id, subject_brand_id, article_type, created_at)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
