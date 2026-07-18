-- V319 已发布的候选版本只保留模板任务方向，不携带旧描述中可能存在的固定结构语句。
-- current_version_id 仍不切换，实际启用继续使用人工灰度脚本。

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.system_prompt = '你是一名资深中文内容创作者和 GEO 内容编辑。根据模板任务、真实材料及渠道边界完成文章。',
    v.user_prompt_template = CONCAT(
        '围绕“{{topic}}”完成“', t.name,
        '”所对应的文章任务。请根据本次主题、读者、材料和平台自主组织内容。'
    ),
    v.variables_json = JSON_ARRAY('topic')
WHERE JSON_UNQUOTE(JSON_EXTRACT(v.quality_rules_json, '$.promptContract')) = 'v2'
  AND v.status = 'published';
