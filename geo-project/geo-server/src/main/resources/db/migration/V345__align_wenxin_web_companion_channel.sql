-- Keep the Wenxin Web companion attached to the existing STANDARD_CHAT logical platform.
-- This is a no-op in environments where the channel was already corrected manually.
UPDATE ai_platform_config companion
JOIN ai_platform_config base
  ON base.platform_code = 'ernie'
 AND base.usage_scene = 'STANDARD_CHAT'
SET companion.channel_code = base.channel_code,
    companion.config_version = companion.config_version + 1,
    companion.updated_at = CURRENT_TIMESTAMP
WHERE companion.platform_code = 'wenxin_web'
  AND companion.usage_scene = 'QUESTION_POLL_WEB'
  AND companion.channel_code <> base.channel_code;
