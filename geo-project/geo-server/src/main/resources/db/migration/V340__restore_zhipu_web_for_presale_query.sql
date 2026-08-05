-- Restore the same-channel Zhipu Web companion for Presale QUERY only.
-- The STANDARD_CHAT zhipu row remains the source of the capability switch and credential.

UPDATE ai_platform_config companion
JOIN ai_platform_config base
  ON base.platform_code = 'zhipu'
 AND base.usage_scene = 'STANDARD_CHAT'
SET companion.channel_code = base.channel_code,
    companion.integration_type = 'ZHIPU_CHAT_WEB',
    companion.provider_config_json = JSON_OBJECT(
        'provider', 'zhipu',
        'stream', false,
        'searchEndpointUrl', 'https://open.bigmodel.cn/api/paas/v4/web_search',
        'searchEngine', 'search_pro',
        'count', 10,
        'searchRecencyFilter', 'noLimit',
        'contentSize', 'medium',
        'questionTiers', JSON_ARRAY('A')
    ),
    companion.config_version = companion.config_version + 1,
    companion.primary_key_ref = CONCAT('db://ai-platform-config/', base.id),
    companion.api_key = NULL,
    companion.api_url = 'https://open.bigmodel.cn/api/paas/v4/chat/completions',
    companion.model_id = 'glm-4.7-flashx',
    companion.model_name = 'GLM-4.7-FlashX 联网模型',
    companion.enabled = 1,
    companion.enabled_for_presale = 0,
    companion.enabled_for_question_poll = 0,
    companion.enabled_for_mobile_dashboard = 0,
    companion.degraded = 0,
    companion.degraded_reason = NULL,
    companion.current_health_status = 'normal',
    companion.remark = 'Enabled as the Zhipu same-channel Web companion for Presale QUERY; question polling remains disabled',
    companion.updated_at = CURRENT_TIMESTAMP
WHERE companion.platform_code = 'zhipu_web'
  AND companion.usage_scene = 'QUESTION_POLL_WEB';

INSERT IGNORE INTO ai_platform_config (
    platform_code, channel_code, usage_scene, integration_type, provider_config_json, config_version,
    platform_name, priority_level, api_key, primary_key_ref, api_url, model_id, model_name,
    concurrency_limit, enabled, enabled_for_presale, presale_evaluate_enabled, enabled_for_article,
    enabled_for_geo_question, enabled_for_question_poll, enabled_for_mobile_dashboard,
    max_retry, timeout_ms, rate_limit_qps, degraded, current_health_status, remark
)
SELECT
    'zhipu_web', base.channel_code, 'QUESTION_POLL_WEB', 'ZHIPU_CHAT_WEB',
    JSON_OBJECT(
        'provider', 'zhipu',
        'stream', false,
        'searchEndpointUrl', 'https://open.bigmodel.cn/api/paas/v4/web_search',
        'searchEngine', 'search_pro',
        'count', 10,
        'searchRecencyFilter', 'noLimit',
        'contentSize', 'medium',
        'questionTiers', JSON_ARRAY('A')
    ),
    1,
    '智谱联网模型', 'P0', NULL, CONCAT('db://ai-platform-config/', base.id),
    'https://open.bigmodel.cn/api/paas/v4/chat/completions',
    'glm-4.7-flashx', 'GLM-4.7-FlashX 联网模型',
    2, 1, 0, 0, 0, 0, 0, 0,
    1, 120000, 1, 0, 'normal',
    'Enabled as the Zhipu same-channel Web companion for Presale QUERY; question polling remains disabled'
FROM ai_platform_config base
WHERE base.platform_code = 'zhipu'
  AND base.usage_scene = 'STANDARD_CHAT'
LIMIT 1;
