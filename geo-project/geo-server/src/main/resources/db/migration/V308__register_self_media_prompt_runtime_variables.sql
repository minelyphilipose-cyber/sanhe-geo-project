-- 注册 V304-V306 引入的自媒体运行时变量，避免模板编辑页保存时报“未注册变量”或 variables_json 缺项。

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.variables_json = JSON_ARRAY_APPEND(COALESCE(v.variables_json, JSON_ARRAY()), '$', 'titleStrategy')
WHERE t.channel_group_code = 'self_media'
  AND v.status = 'published'
  AND JSON_CONTAINS(COALESCE(v.variables_json, JSON_ARRAY()), JSON_QUOTE('titleStrategy'), '$') = 0;

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.variables_json = JSON_ARRAY_APPEND(COALESCE(v.variables_json, JSON_ARRAY()), '$', 'structureStrategy')
WHERE t.channel_group_code = 'self_media'
  AND v.status = 'published'
  AND JSON_CONTAINS(COALESCE(v.variables_json, JSON_ARRAY()), JSON_QUOTE('structureStrategy'), '$') = 0;

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.variables_json = JSON_ARRAY_APPEND(COALESCE(v.variables_json, JSON_ARRAY()), '$', 'perspectivePolicy')
WHERE t.channel_group_code = 'self_media'
  AND v.status = 'published'
  AND JSON_CONTAINS(COALESCE(v.variables_json, JSON_ARRAY()), JSON_QUOTE('perspectivePolicy'), '$') = 0;
