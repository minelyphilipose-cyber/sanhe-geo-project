-- Douyin OAuth is retired. An active fingerprint-browser binding is authoritative even when
-- a historical account still carries V112's OAUTH default.
UPDATE self_media_account account
JOIN browser_environment_account binding
  ON binding.self_media_account_id = account.id
 AND binding.deleted_at IS NULL
JOIN browser_environment environment
  ON environment.id = binding.browser_environment_id
 AND environment.deleted_at IS NULL
 AND environment.status = 'active'
SET account.auth_mode = 'COOKIE',
    account.last_auth_error = NULL,
    account.updated_at = CURRENT_TIMESTAMP
WHERE LOWER(account.platform) IN ('douyin', 'douyin_image_text')
  AND account.deleted_at IS NULL;

-- Keep historical OAuth rows for audit, but prevent unbound legacy OAuth accounts from being selected.
UPDATE self_media_account account
SET account.status = 'disabled',
    last_auth_error = 'Douyin OAuth retired; use fingerprint-browser account',
    account.updated_at = CURRENT_TIMESTAMP
WHERE LOWER(account.platform) IN ('douyin', 'douyin_image_text')
  AND UPPER(COALESCE(account.auth_mode, 'OAUTH')) = 'OAUTH'
  AND account.status = 'active'
  AND account.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1
    FROM browser_environment_account binding
    JOIN browser_environment environment
      ON environment.id = binding.browser_environment_id
     AND environment.deleted_at IS NULL
     AND environment.status = 'active'
    WHERE binding.self_media_account_id = account.id
      AND binding.deleted_at IS NULL
  );

-- Browser login validity is not guaranteed by the platforms. These conservative values are
-- reference windows for re-verification: Douyin/Xiaohongshu about 30 days, others at least 90.
UPDATE self_media_auth_health_policy
SET reverify_interval_days = CASE
        WHEN platform_code IN ('douyin', 'xiaohongshu') THEN 30
        ELSE 90
    END,
    warning_days = CASE
        WHEN platform_code IN ('douyin', 'xiaohongshu') THEN 7
        ELSE 14
    END,
    credential_reference_days = CASE
        WHEN platform_code IN ('douyin', 'xiaohongshu') THEN 30
        ELSE 90
    END,
    credential_expiry_mode = 'declared_then_reference',
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE platform_code IN ('douyin', 'xiaohongshu', 'toutiao', 'baijiahao', 'zhihu');

-- Align active estimated credential metadata with the same reference windows. This does not
-- rewrite Cookie-declared or manually maintained expiry facts.
UPDATE self_media_cookie_credential credential
JOIN self_media_account account ON account.id = credential.self_media_account_id
SET credential.expires_at = TIMESTAMPADD(
        DAY,
        CASE WHEN LOWER(account.platform) IN ('douyin', 'douyin_image_text', 'xiaohongshu') THEN 30 ELSE 90 END,
        credential.captured_at
    ),
    credential.expiry_source = 'platform_policy'
WHERE LOWER(account.platform) IN ('douyin', 'douyin_image_text', 'xiaohongshu', 'toutiao', 'baijiahao', 'zhihu')
  AND credential.expiry_source = 'platform_policy'
  AND credential.valid_until IS NULL
  AND credential.destroyed_at IS NULL
  AND credential.captured_at IS NOT NULL;
