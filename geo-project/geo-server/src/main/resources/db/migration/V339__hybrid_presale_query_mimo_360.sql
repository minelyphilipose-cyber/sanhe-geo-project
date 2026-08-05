-- Hybrid Presale QUERY routing and the first two additional native Web companions.
-- Companion profiles remain disabled until their provider entitlement and real response fixture pass diagnostics.

ALTER TABLE presale_report_version
    ADD COLUMN planned_web_query_count INT NOT NULL DEFAULT 0
        COMMENT 'planned QUERY samples that actually use an enabled Web companion'
        AFTER planned_query_count;

-- Hunyuan is the base chat identity for the Yuanbao Web channel.
UPDATE ai_platform_config
   SET channel_code = 'yuanbao',
       config_version = config_version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE platform_code = 'hunyuan'
   AND usage_scene = 'STANDARD_CHAT'
   AND channel_code <> 'yuanbao';

INSERT IGNORE INTO ai_platform_config (
    platform_code, channel_code, usage_scene, integration_type, provider_config_json, config_version,
    platform_name, priority_level, api_key, primary_key_ref, api_url, model_id, model_name,
    concurrency_limit, enabled, enabled_for_presale, presale_evaluate_enabled, enabled_for_article,
    enabled_for_geo_question, enabled_for_question_poll, enabled_for_mobile_dashboard,
    max_retry, timeout_ms, rate_limit_qps, degraded, current_health_status, remark
)
SELECT
    'mimo_web', base.channel_code, 'QUESTION_POLL_WEB', 'MIMO_CHAT_WEB',
    JSON_OBJECT(
        'provider', 'xiaomi-mimo',
        'stream', false,
        'forceSearch', true,
        'maxKeyword', 3,
        'resultLimit', 10,
        'maxCompletionTokens', 2048,
        'thinkingType', 'disabled',
        'questionTiers', JSON_ARRAY('A', 'B', 'C')
    ),
    1,
    '小米 MiMo 联网回答', 'P1', NULL, CONCAT('db://ai-platform-config/', base.id),
    'https://api.xiaomimimo.com/v1/chat/completions', 'mimo-v2.5-pro', 'MiMo V2.5 Pro 联网模型',
    2, 0, 0, 0, 0, 0, 0, 0,
    1, 120000, 1, 0, 'normal',
    'Disabled until the MiMo Web Search plugin, diagnostic and real response fixture pass'
FROM ai_platform_config base
WHERE base.platform_code = 'mimo'
  AND base.usage_scene = 'STANDARD_CHAT'
LIMIT 1;

INSERT IGNORE INTO ai_platform_config (
    platform_code, channel_code, usage_scene, integration_type, provider_config_json, config_version,
    platform_name, priority_level, api_key, primary_key_ref, api_url, model_id, model_name,
    concurrency_limit, enabled, enabled_for_presale, presale_evaluate_enabled, enabled_for_article,
    enabled_for_geo_question, enabled_for_question_poll, enabled_for_mobile_dashboard,
    max_retry, timeout_ms, rate_limit_qps, degraded, current_health_status, remark
)
SELECT
    'zhinao_web', base.channel_code, 'QUESTION_POLL_WEB', 'QIHOO_360_AI_SEARCH_WEB',
    JSON_OBJECT(
        'provider', 'qihoo-360',
        'stream', false,
        'maxReferSearchItems', 20,
        'enableCornerMarkers', true,
        'enableWebPageSafety', true,
        'questionTiers', JSON_ARRAY('A', 'B', 'C')
    ),
    1,
    '360 智脑联网回答', 'P1', NULL, CONCAT('db://ai-platform-config/', base.id),
    'https://api.360.cn/v1/search/aisearch', '360gpt-pro', '360 智搜 AI 搜索',
    2, 0, 0, 0, 0, 0, 0, 0,
    1, 120000, 1, 0, 'normal',
    'Disabled until the 360 AI Search entitlement, diagnostic and real response fixture pass'
FROM ai_platform_config base
WHERE base.platform_code = 'zhinao'
  AND base.usage_scene = 'STANDARD_CHAT'
LIMIT 1;

-- Native question-poll candidates: they use the existing OPENAI_CHAT invocation path and
-- participate in answer/hit aggregation, but never increase Web-search coverage counters.
UPDATE ai_platform_config
   SET enabled_for_question_poll = 1,
       config_version = config_version + 1,
       updated_at = CURRENT_TIMESTAMP
 WHERE platform_code IN ('kimi', 'hailuo')
   AND usage_scene = 'STANDARD_CHAT'
   AND enabled_for_question_poll = 0;
