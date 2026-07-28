ALTER TABLE ai_platform_config
    ADD COLUMN enabled_for_mobile_dashboard TINYINT(1) NOT NULL DEFAULT 0
        COMMENT 'whether question-poll data is visible in the public mobile dashboard'
        AFTER enabled_for_question_poll;

ALTER TABLE poll_search_sources
    ADD COLUMN media VARCHAR(255) NULL
        COMMENT 'provider-reported source media or site name'
        AFTER domain;

UPDATE ai_platform_config
   SET enabled_for_mobile_dashboard = 1
 WHERE usage_scene = 'QUESTION_POLL_WEB'
   AND (
       platform_code IN ('doubao_web', 'deepseek_ark_web', 'qwen_web')
       OR channel_code IN ('doubao', 'deepseek', 'qwen')
   );

UPDATE ai_platform_config
   SET enabled_for_mobile_dashboard = 0
 WHERE platform_code = 'tencent_search_web'
    OR (usage_scene = 'QUESTION_POLL_WEB' AND channel_code = 'yuanbao');

INSERT IGNORE INTO ai_platform_config (
    platform_code, channel_code, usage_scene, integration_type, provider_config_json, config_version,
    platform_name, priority_level, api_key, primary_key_ref, api_url, model_id, model_name,
    concurrency_limit, enabled, enabled_for_presale, presale_evaluate_enabled, enabled_for_article,
    enabled_for_geo_question, enabled_for_question_poll, enabled_for_mobile_dashboard,
    max_retry, timeout_ms, rate_limit_qps, degraded, remark
) VALUES (
    'zhipu_web', 'zhipu', 'QUESTION_POLL_WEB', 'ZHIPU_CHAT_WEB',
    JSON_OBJECT(
        'provider', 'zhipu',
        'stream', false,
        'searchEngine', 'search_pro',
        'count', 10,
        'searchRecencyFilter', 'noLimit',
        'contentSize', 'medium',
        'questionTiers', JSON_ARRAY('A')
    ),
    1,
    '智谱清言联网回答', 'P0', NULL, 'env://ZHIPU_API_KEY',
    'https://open.bigmodel.cn/api/paas/v4/chat/completions',
    'glm-4-plus', '智谱 GLM 联网模型',
    2, 0, 0, 0, 0, 0, 0, 0,
    1, 120000, 1, 0,
    'Disabled until GLM Web Search diagnostic and manual poll verification succeed'
);
