-- Native MiMo calls regularly exceed the generic 60-second request window.
-- mimo_web remains independently configured at 120 seconds.
UPDATE ai_platform_config
SET timeout_ms = 90000,
    config_version = config_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE platform_code = 'mimo'
  AND usage_scene = 'STANDARD_CHAT'
  AND (timeout_ms IS NULL OR timeout_ms <> 90000);
