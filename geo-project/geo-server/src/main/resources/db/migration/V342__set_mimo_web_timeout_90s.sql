-- MiMo web search usually completes close to 60 seconds. Keep enough headroom for
-- slower responses while avoiding the previous 120-second per-call upper bound.

UPDATE ai_platform_config
SET timeout_ms = 90000,
    config_version = config_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE platform_code = 'mimo_web'
  AND usage_scene = 'QUESTION_POLL_WEB'
  AND integration_type = 'MIMO_CHAT_WEB'
  AND (timeout_ms IS NULL OR timeout_ms <> 90000);
