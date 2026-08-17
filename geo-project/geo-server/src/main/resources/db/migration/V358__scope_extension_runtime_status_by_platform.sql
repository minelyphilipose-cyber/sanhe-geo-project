-- Keep an independent latest runtime fact for every platform opened in the same
-- fingerprint-browser profile.  The previous key allowed a Zhihu heartbeat to
-- overwrite Douyin/Xiaohongshu login state from the same extension installation.
ALTER TABLE extension_runtime_status
  DROP INDEX uk_extension_runtime_provider_install,
  ADD COLUMN runtime_platform_key VARCHAR(32)
    GENERATED ALWAYS AS (
      COALESCE(
        NULLIF(LOWER(TRIM(detected_platform)), ''),
        NULLIF(LOWER(TRIM(platform)), ''),
        '__unknown__'
      )
    ) STORED AFTER detected_platform,
  ADD UNIQUE KEY uk_extension_runtime_provider_install_platform (
    provider_profile_id,
    install_id,
    runtime_platform_key
  );
