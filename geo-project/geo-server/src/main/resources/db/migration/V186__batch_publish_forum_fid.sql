-- ============================================================
-- V183: batch publish forum board target
-- ============================================================

ALTER TABLE content_batch_publish_item
  ADD COLUMN target_forum_fid INT NULL COMMENT 'Target forum board fid for discuz_http publish'
    AFTER target_site_id;
