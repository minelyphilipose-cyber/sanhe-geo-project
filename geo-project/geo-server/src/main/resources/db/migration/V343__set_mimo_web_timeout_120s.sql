-- MiMo web search P95 is already close to 90 seconds in production-like
-- measurements. Use the shared web-query default to leave enough jitter margin.

UPDATE ai_platform_config
SET timeout_ms = 120000,
    config_version = config_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE platform_code = 'mimo_web'
  AND usage_scene = 'QUESTION_POLL_WEB'
  AND integration_type = 'MIMO_CHAT_WEB'
  AND (timeout_ms IS NULL OR timeout_ms <> 120000);
