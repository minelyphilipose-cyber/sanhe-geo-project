-- 搜狐所有视角最多展示公开地址，常规客户视角使用 A 级问题生成。
UPDATE article_prompt_template
SET contact_disclosure_mode = 'address_only', updated_at = NOW()
WHERE channel_group_code = 'self_media' AND channel_sub_code = 'sohu';

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, article_type_code,
   question_scene_code, perspective_code, weight, sort_order, status,
   contact_disclosure_mode, current_version_id, created_at, updated_at)
SELECT '搜狐地域品牌问答通用模板', '围绕A级问题，以地域和品牌入题，解答问题并介绍真实品牌优势与特点。',
       'self_media', 'sohu', 'industry_article', NULL, 'customer', 10, 0, 'active',
       'address_only', NULL, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM article_prompt_template
  WHERE channel_group_code = 'self_media' AND channel_sub_code = 'sohu'
    AND perspective_code = 'customer' AND question_scene_code IS NULL
    AND status = 'active' AND weight > 0
);

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT t.id, 1, 'published',
       '依据真实材料生成搜狐资讯文章，完整回答标题，不虚构品牌事实。',
       '围绕“{{topic}}”撰写搜狐文章；标题包含地域和品牌，可采用疑问句或反问句。正文解答问题并细致说明真实品牌优势与特点，联系方式最多只展示公开地址。',
       JSON_ARRAY('topic'), JSON_OBJECT('promptContract', 'v2', 'sohuGenerationRulesVersion', 1), NOW(), NOW()
FROM article_prompt_template t
WHERE t.name = '搜狐地域品牌问答通用模板' AND t.current_version_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

UPDATE article_prompt_template t
JOIN article_prompt_template_version v ON v.template_id = t.id AND v.version_no = 1 AND v.status = 'published'
SET t.current_version_id = v.id, t.updated_at = NOW()
WHERE t.name = '搜狐地域品牌问答通用模板' AND t.current_version_id IS NULL;
