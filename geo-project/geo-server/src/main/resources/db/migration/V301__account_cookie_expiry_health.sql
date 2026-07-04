ALTER TABLE self_media_cookie_credential
  ADD COLUMN expires_at DATETIME NULL COMMENT 'expected cookie login expiry time' AFTER valid_until,
  ADD COLUMN expiry_source VARCHAR(32) NULL COMMENT 'cookie_expires/platform_policy/manual/default' AFTER expires_at,
  ADD KEY idx_cookie_credential_expires_at (expires_at);
