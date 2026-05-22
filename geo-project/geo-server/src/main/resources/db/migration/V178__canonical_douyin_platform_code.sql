UPDATE article_prompt_template
SET channel_sub_code = 'douyin'
WHERE channel_group_code = 'self_media'
  AND channel_sub_code = 'douyin_image_text';

UPDATE article_prompt_template_version
SET user_prompt_template = REPLACE(user_prompt_template, 'douyin_image_text', 'douyin'),
    system_prompt = REPLACE(system_prompt, 'douyin_image_text', 'douyin'),
    variables_json = REPLACE(variables_json, 'douyin_image_text', 'douyin'),
    quality_rules_json = REPLACE(quality_rules_json, 'douyin_image_text', 'douyin')
WHERE user_prompt_template LIKE '%douyin_image_text%'
   OR system_prompt LIKE '%douyin_image_text%'
   OR variables_json LIKE '%douyin_image_text%'
   OR quality_rules_json LIKE '%douyin_image_text%';

UPDATE article_draft
SET content_style = 'douyin',
    channel_sub_code = CASE WHEN channel_sub_code = 'douyin_image_text' THEN 'douyin' ELSE channel_sub_code END
WHERE content_style = 'douyin_image_text'
   OR channel_sub_code = 'douyin_image_text';

UPDATE batch_article_generation_task
SET content_style = 'douyin',
    channel_sub_code = CASE WHEN channel_sub_code = 'douyin_image_text' THEN 'douyin' ELSE channel_sub_code END
WHERE content_style = 'douyin_image_text'
   OR channel_sub_code = 'douyin_image_text';

UPDATE content_batch_publish_item
SET content_style = 'douyin'
WHERE content_style = 'douyin_image_text';
