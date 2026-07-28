-- Forward compensation for environments where V327 has already been applied.
-- V327 must remain immutable because Flyway records its checksum.

UPDATE ai_platform_config
   SET enabled = 0,
       enabled_for_question_poll = 0,
       enabled_for_mobile_dashboard = 0,
       api_key = NULL,
       primary_key_ref = NULL,
       degraded = 1,
       degraded_reason = 'Retired: replaced by wenxin_web after manual source-quality review',
       current_health_status = 'maintenance',
       config_version = config_version + 1,
       remark = 'Retired configuration retained for historical audit; do not re-enable'
 WHERE platform_code = 'zhipu_web'
   AND usage_scene = 'QUESTION_POLL_WEB';

INSERT IGNORE INTO ai_platform_config (
    platform_code, channel_code, usage_scene, integration_type, provider_config_json, config_version,
    platform_name, priority_level, api_key, primary_key_ref, api_url, model_id, model_name,
    concurrency_limit, enabled, enabled_for_presale, presale_evaluate_enabled, enabled_for_article,
    enabled_for_geo_question, enabled_for_question_poll, enabled_for_mobile_dashboard,
    max_retry, timeout_ms, rate_limit_qps, degraded, remark
) VALUES (
    'wenxin_web', 'wenxin', 'QUESTION_POLL_WEB', 'QIANFAN_ERNIE_CHAT_WEB',
    JSON_OBJECT(
        'provider', 'qianfan',
        'stream', false,
        'searchMode', 'auto',
        'searchNumber', 10,
        'referenceNumber', 5,
        'questionTiers', JSON_ARRAY('A')
    ),
    1,
    '文心一言联网回答', 'P0', NULL, 'env://QIANFAN_API_KEY',
    'https://qianfan.baidubce.com/v2/chat/completions',
    'ernie-4.5-turbo-32k', 'ERNIE-4.5-Turbo 联网模型',
    2, 0, 0, 0, 0, 0, 0, 0,
    1, 120000, 1, 0,
    'Disabled until Qianfan diagnostic, manual poll verification, and content review succeed'
);
