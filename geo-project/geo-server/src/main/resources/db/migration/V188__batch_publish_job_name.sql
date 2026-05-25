-- ============================================================
-- V188: add display name for batch publish jobs
-- ============================================================

ALTER TABLE content_batch_publish_job
  ADD COLUMN job_name VARCHAR(160) NULL COMMENT 'batch publish job display name' AFTER id;

UPDATE content_batch_publish_job j
LEFT JOIN (
  SELECT job_id, MIN(project_id) AS project_id
  FROM content_batch_publish_item
  GROUP BY job_id
) i ON i.job_id = j.id
LEFT JOIN project p ON p.id = i.project_id
LEFT JOIN brand b ON b.id = p.brand_id
SET j.job_name = CONCAT(
  '批量_',
  COALESCE(
    NULLIF(TRIM(b.brand_short_name), ''),
    NULLIF(TRIM(b.brand_name), ''),
    NULLIF(TRIM(p.brand_name), ''),
    NULLIF(TRIM(p.project_name), ''),
    '任务'
  ),
  '_',
  DATE_FORMAT(COALESCE(j.scheduled_at, j.created_at), '%Y-%m-%d')
)
WHERE j.job_name IS NULL OR j.job_name = '';

ALTER TABLE content_batch_publish_job
  MODIFY COLUMN job_name VARCHAR(160) NOT NULL COMMENT 'batch publish job display name';
