-- ============================================================
-- V243: link project self-media schedule batches with article generation batches
-- ============================================================

ALTER TABLE project_self_media_schedule_batch
  ADD COLUMN generation_batch_ids JSON NULL AFTER rejected_count;

ALTER TABLE project_self_media_schedule_batch
  MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'processing'
    COMMENT 'processing/created/partial_failed/failed/cancelled';
