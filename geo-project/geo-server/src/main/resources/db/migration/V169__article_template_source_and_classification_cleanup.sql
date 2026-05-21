SET @col := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'batch_article_generation_task'
    AND COLUMN_NAME = 'template_source'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE batch_article_generation_task ADD COLUMN template_source VARCHAR(32) NULL COMMENT ''smart/weighted/custom/fallback_default_prompt'' AFTER allocation_mode',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'article_draft'
    AND COLUMN_NAME = 'template_source'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE article_draft ADD COLUMN template_source VARCHAR(32) NULL COMMENT ''smart/weighted/custom/fallback_default_prompt'' AFTER allocation_mode',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE batch_article_generation_task
SET article_type_code = article_type
WHERE article_type_code IS NULL
  AND article_type IN (
    'faq', 'scenario_content', 'industry_article', 'stage_advice',
    'buying_guide', 'comparison', 'cost_analysis', 'pitfall_guide',
    'social_note', 'news_brief', 'forum_discussion'
  );

UPDATE article_draft
SET article_type_code = article_type
WHERE article_type_code IS NULL
  AND article_type IN (
    'faq', 'scenario_content', 'industry_article', 'stage_advice',
    'buying_guide', 'comparison', 'cost_analysis', 'pitfall_guide',
    'social_note', 'news_brief', 'forum_discussion'
  );

UPDATE batch_article_generation_task
SET
  channel_group_code = CASE
    WHEN content_style IN ('wechat', 'toutiao', 'douyin_image_text', 'zhihu', 'xiaohongshu', 'baijiahao', 'netease') THEN 'self_media'
    WHEN content_style IN ('agent_site_article', 'linkedin') THEN 'agent_site'
    WHEN content_style = 'industry_site' THEN 'industry_site'
    WHEN content_style = 'authority_media' THEN 'authority_media'
    WHEN content_style = 'forum' THEN 'forum'
    ELSE channel_group_code
  END,
  channel_sub_code = CASE
    WHEN content_style IN ('wechat', 'toutiao', 'douyin_image_text', 'zhihu', 'xiaohongshu', 'baijiahao', 'netease') THEN content_style
    WHEN content_style = 'authority_media' THEN 'industry_media'
    ELSE channel_sub_code
  END
WHERE channel_group_code IS NULL
  AND content_style IS NOT NULL;

UPDATE article_draft
SET
  channel_group_code = CASE
    WHEN content_style IN ('wechat', 'toutiao', 'douyin_image_text', 'zhihu', 'xiaohongshu', 'baijiahao', 'netease') THEN 'self_media'
    WHEN content_style IN ('agent_site_article', 'linkedin') THEN 'agent_site'
    WHEN content_style = 'industry_site' THEN 'industry_site'
    WHEN content_style = 'authority_media' THEN 'authority_media'
    WHEN content_style = 'forum' THEN 'forum'
    ELSE channel_group_code
  END,
  channel_sub_code = CASE
    WHEN content_style IN ('wechat', 'toutiao', 'douyin_image_text', 'zhihu', 'xiaohongshu', 'baijiahao', 'netease') THEN content_style
    WHEN content_style = 'authority_media' THEN 'industry_media'
    ELSE channel_sub_code
  END
WHERE channel_group_code IS NULL
  AND content_style IS NOT NULL;

UPDATE batch_article_generation_task
SET template_source = CASE
  WHEN prompt_template_id IS NULL OR prompt_template_version_id IS NULL THEN 'fallback_default_prompt'
  WHEN allocation_mode = 'custom' THEN 'custom'
  ELSE 'weighted'
END
WHERE template_source IS NULL;

UPDATE article_draft
SET template_source = CASE
  WHEN prompt_template_id IS NULL OR prompt_template_version_id IS NULL THEN 'fallback_default_prompt'
  WHEN allocation_mode = 'custom' THEN 'custom'
  ELSE 'weighted'
END
WHERE template_source IS NULL;

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'article_type', 'buying_guide', '选择指南', 50, 1, '选择指南类文章'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'article_type' AND dict_key = 'buying_guide');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'article_type', 'comparison', '对比评测', 60, 1, '对比评测类文章'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'article_type' AND dict_key = 'comparison');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'article_type', 'cost_analysis', '费用解析', 70, 1, '费用解析类文章'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'article_type' AND dict_key = 'cost_analysis');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'article_type', 'pitfall_guide', '避坑指南', 80, 1, '避坑指南类文章'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'article_type' AND dict_key = 'pitfall_guide');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'article_type', 'social_note', '经验笔记', 90, 1, '经验笔记类文章'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'article_type' AND dict_key = 'social_note');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'article_type', 'news_brief', '资讯简讯', 100, 1, '资讯简讯类文章'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'article_type' AND dict_key = 'news_brief');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'article_type', 'forum_discussion', '讨论帖', 110, 1, '论坛讨论帖'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'article_type' AND dict_key = 'forum_discussion');
