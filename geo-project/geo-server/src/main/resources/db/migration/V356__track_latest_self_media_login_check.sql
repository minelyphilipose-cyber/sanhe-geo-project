ALTER TABLE self_media_account
  ADD COLUMN last_login_verification_checked_at DATETIME NULL
    COMMENT 'latest browser login check time, regardless of success or failure'
    AFTER last_login_verified_at;

-- Existing successful facts are also the latest known checks until a new browser report arrives.
UPDATE self_media_account
SET last_login_verification_checked_at = last_login_verified_at
WHERE last_login_verified_at IS NOT NULL
  AND last_login_verification_checked_at IS NULL;
