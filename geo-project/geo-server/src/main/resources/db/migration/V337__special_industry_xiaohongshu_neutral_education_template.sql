-- 特殊行业小红书临时切换为无品牌中立科普模板。
-- 原“特殊行业小红书个人号清单笔记模板”及其版本保持不变；后续可仅修改
-- special_industry_template_route.template_name 完成策略切换。

SET @neutral_template_name = '特殊行业小红书中立科普模板';

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  @neutral_template_name,
  '特殊行业小红书临时中立科普模板：不出现企业、品牌、具体机构和导流信息，仅发布克制的小知识内容',
  'self_media',
  'xiaohongshu',
  NULL,
  'social_note',
  NULL,
  'customer',
  0,
  98,
  'active',
  NULL,
  'none',
  NULL,
  NULL,
  NOW(),
  NOW()
WHERE NOT EXISTS (
  SELECT 1
  FROM article_prompt_template t
  WHERE t.name COLLATE utf8mb4_unicode_ci = @neutral_template_name COLLATE utf8mb4_unicode_ci
);

UPDATE article_prompt_template
SET description = '特殊行业小红书临时中立科普模板：不出现企业、品牌、具体机构和导流信息，仅发布克制的小知识内容',
    channel_group_code = 'self_media',
    channel_sub_code = 'xiaohongshu',
    agent_site_module = NULL,
    article_type_code = 'social_note',
    question_scene_code = NULL,
    perspective_code = 'customer',
    weight = 0,
    sort_order = 98,
    status = 'active',
    contact_disclosure_mode = 'none',
    updated_at = NOW()
WHERE name COLLATE utf8mb4_unicode_ci = @neutral_template_name COLLATE utf8mb4_unicode_ci;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  t.id,
  1,
  'published',
  '你是一名特殊行业中文科普编辑。只生成中立、克制、无品牌的小红书知识内容，不提供诊断、治疗、效果、机构选择或消费决策建议。',
  '围绕“{{topic}}”写一篇500至700字的中立科普。标题不超过20字；正文不出现任何企业、品牌、具体机构、医生、品牌专属产品服务、联系方式或地域导流信息。主题涉及的通用概念、项目类别或技术名称可以用于客观科普。只解释公开、通用的小知识，不写推荐、避雷、亲测、效果暗示、焦虑内容和行动引导。',
  JSON_ARRAY('topic'),
  JSON_OBJECT(
    'promptContract', 'v2',
    'xiaohongshuContentMode', 'neutral_education',
    'medicalComplianceRequired', true,
    'contactDisclosure', false,
    'brandMentionMin', 0,
    'brandMentionMax', 0,
    'wordMin', 500,
    'wordMax', 700,
    'titleMaxChars', 20,
    'forbidBrandMention', true,
    'forbidExperienceSeeding', true,
    'forbidEffectPromise', true,
    'forbidRankingClaim', true,
    'forbidAppointmentCTA', true
  ),
  NOW(),
  NOW()
FROM article_prompt_template t
WHERE t.name COLLATE utf8mb4_unicode_ci = @neutral_template_name COLLATE utf8mb4_unicode_ci
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  system_prompt = VALUES(system_prompt),
  user_prompt_template = VALUES(user_prompt_template),
  variables_json = VALUES(variables_json),
  quality_rules_json = VALUES(quality_rules_json),
  published_at = COALESCE(article_prompt_template_version.published_at, VALUES(published_at));

UPDATE article_prompt_template t
JOIN article_prompt_template_version v
  ON v.template_id = t.id
 AND v.version_no = 1
 AND v.status = 'published'
SET t.current_version_id = v.id,
    t.updated_at = NOW()
WHERE t.name COLLATE utf8mb4_unicode_ci = @neutral_template_name COLLATE utf8mb4_unicode_ci;

INSERT INTO special_industry_template_route
  (industry_code, channel_group_code, channel_sub_code, account_identity,
   template_name, priority, enabled, created_at, updated_at)
VALUES
  ('*', 'self_media', 'xiaohongshu', 'personal', @neutral_template_name, 110, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  template_name = VALUES(template_name),
  priority = VALUES(priority),
  enabled = VALUES(enabled),
  updated_at = NOW();
