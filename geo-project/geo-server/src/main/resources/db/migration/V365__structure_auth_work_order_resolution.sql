ALTER TABLE self_media_auth_work_order
  ADD COLUMN resolution_mode VARCHAR(32) NULL COMMENT 'login_restored or special' AFTER resolved_at,
  ADD COLUMN resolution_reason VARCHAR(64) NULL COMMENT 'structured operator resolution reason' AFTER resolution_mode;
