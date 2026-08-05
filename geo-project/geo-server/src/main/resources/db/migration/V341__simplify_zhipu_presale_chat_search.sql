-- V340 was already applied in local validation environments. Keep it immutable and remove the
-- unused two-stage endpoint so the production baseline is native Chat Search with tool_choice=auto.

UPDATE ai_platform_config
SET provider_config_json = JSON_REMOVE(provider_config_json, '$.searchEndpointUrl'),
    config_version = config_version + 1,
    remark = 'Enabled as native glm-4.7-flashx Chat Search for Presale QUERY; tool_choice=auto; question polling remains disabled',
    updated_at = CURRENT_TIMESTAMP
WHERE platform_code = 'zhipu_web'
  AND usage_scene = 'QUESTION_POLL_WEB'
  AND JSON_CONTAINS_PATH(provider_config_json, 'one', '$.searchEndpointUrl') = 1;
