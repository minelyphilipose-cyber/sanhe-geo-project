-- ============================================================
-- V214: publish result check snapshot for self-media schedules
-- ============================================================

ALTER TABLE self_media_publish_schedule
  ADD COLUMN publish_check_title VARCHAR(255) NULL COMMENT 'Title snapshot used to match platform publish result' AFTER platform_published_url,
  ADD COLUMN publish_check_cover_url VARCHAR(1000) NULL COMMENT 'Cover URL snapshot used to match platform publish result' AFTER publish_check_title,
  ADD COLUMN publish_check_location_name VARCHAR(128) NULL COMMENT 'Location snapshot used to match platform publish result' AFTER publish_check_cover_url,
  ADD COLUMN publish_check_fingerprint VARCHAR(64) NULL COMMENT 'Stable SHA-256 fingerprint for publish result matching' AFTER publish_check_location_name,
  ADD KEY idx_self_media_schedule_publish_check (platform, self_media_account_id, platform_scheduled_at, publish_check_fingerprint);
