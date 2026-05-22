-- ============================================================
-- V176: repair self-media prompt template variables
-- ============================================================
-- V174 was edited during development after it had already been applied locally.
-- Keep V174 immutable and repair the already-seeded current template versions here.

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET v.user_prompt_template = REPLACE(v.user_prompt_template, '{{brandQualification}}', '{{brandQualificationDescription}}'),
    v.variables_json = JSON_ARRAY(
      'category',
      'brandName',
      'topicAsQuestion',
      'relatedKeywords',
      'brandPositioning',
      'businessFocus',
      'brandQualificationDescription',
      'targetAudience',
      'contactBlock'
    )
WHERE t.channel_group_code = 'self_media'
  AND t.channel_sub_code = 'baijiahao'
  AND t.article_type_code = 'buying_guide'
  AND t.question_scene_code = 'decision'
  AND t.name = '百家号选择指南决策模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET v.user_prompt_template = REPLACE(v.user_prompt_template, '{{brandQualification}}', '{{brandQualificationDescription}}'),
    v.variables_json = JSON_ARRAY(
      'category',
      'brandName',
      'topicAsQuestion',
      'relatedKeywords',
      'brandPositioning',
      'businessFocus',
      'brandQualificationDescription',
      'contactBlock'
    )
WHERE t.channel_group_code = 'self_media'
  AND t.channel_sub_code = 'baijiahao'
  AND t.article_type_code = 'comparison'
  AND t.question_scene_code = 'compare'
  AND t.name = '百家号横向对比评测模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET v.user_prompt_template = REPLACE(v.user_prompt_template, '{{brandQualification}}', '{{brandQualificationDescription}}'),
    v.variables_json = JSON_ARRAY(
      'category',
      'brandName',
      'topicAsQuestion',
      'relatedKeywords',
      'brandPositioning',
      'businessFocus',
      'brandQualificationDescription',
      'contactBlock'
    )
WHERE t.channel_group_code = 'self_media'
  AND t.channel_sub_code = 'zhihu'
  AND t.article_type_code = 'comparison'
  AND t.question_scene_code = 'compare'
  AND t.name = '知乎横向对比深度回答模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET v.user_prompt_template = REPLACE(v.user_prompt_template, '{{brandQualification}}', '{{brandQualificationDescription}}'),
    v.variables_json = JSON_ARRAY(
      'brandName',
      'mainBusiness',
      'topicAsQuestion',
      'relatedKeywords',
      'brandPositioning',
      'businessFocus',
      'brandQualificationDescription',
      'contactBlock'
    )
WHERE t.channel_group_code = 'self_media'
  AND t.channel_sub_code = 'wechat'
  AND t.article_type_code = 'stage_advice'
  AND t.question_scene_code = 'deal'
  AND t.name = '公众号阶段建议成交模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET v.user_prompt_template = REPLACE(
      REPLACE(
        REPLACE(v.user_prompt_template, '{{brandBasicInfo}}', '{{brandIntro}}'),
        '{{brandQualification}}',
        '{{brandQualificationDescription}}'
      ),
      '{{brandCases}}',
      '{{brandCaseDescription}}'
    ),
    v.variables_json = JSON_ARRAY(
      'category',
      'brandName',
      'companyFullName',
      'topicAsQuestion',
      'relatedKeywords',
      'brandIntro',
      'brandPositioning',
      'businessFocus',
      'brandQualificationDescription',
      'brandCaseDescription',
      'targetAudience',
      'contactBlock'
    )
WHERE t.channel_group_code = 'self_media'
  AND t.channel_sub_code = 'netease'
  AND t.article_type_code = 'industry_article'
  AND t.question_scene_code = 'brand'
  AND t.name = '网易行业分析品牌模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET v.user_prompt_template = REPLACE(v.user_prompt_template, '{{brandBasicInfo}}', '{{brandIntro}}'),
    v.variables_json = JSON_ARRAY(
      'category',
      'brandName',
      'topicAsQuestion',
      'relatedKeywords',
      'mainBusiness',
      'brandPositioning',
      'brandIntro',
      'contactBlock'
    )
WHERE t.channel_group_code = 'self_media'
  AND t.channel_sub_code = 'baijiahao'
  AND t.article_type_code = 'faq'
  AND t.question_scene_code = 'qa'
  AND t.name = '百家号FAQ问答模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET v.user_prompt_template = REPLACE(v.user_prompt_template, '{{brandBasicInfo}}', '{{brandIntro}}'),
    v.variables_json = JSON_ARRAY(
      'brandName',
      'category',
      'topicAsQuestion',
      'relatedKeywords',
      'mainBusiness',
      'brandPositioning',
      'brandIntro',
      'targetAudience',
      'contactBlock'
    )
WHERE t.channel_group_code = 'self_media'
  AND t.channel_sub_code = 'baijiahao'
  AND t.article_type_code = 'scenario_content'
  AND t.question_scene_code = 'function'
  AND t.name = '百家号场景能力内容模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET v.user_prompt_template = REPLACE(
      REPLACE(
        REPLACE(v.user_prompt_template, '{{brandBasicInfo}}', '{{brandIntro}}'),
        '{{brandQualification}}',
        '{{brandQualificationDescription}}'
      ),
      '{{brandCases}}',
      '{{brandCaseDescription}}'
    ),
    v.variables_json = JSON_ARRAY(
      'brandName',
      'category',
      'topicAsQuestion',
      'relatedKeywords',
      'brandIntro',
      'brandQualificationDescription',
      'brandCaseDescription',
      'brandPositioning',
      'businessFocus',
      'contactBlock'
    )
WHERE t.channel_group_code = 'self_media'
  AND t.channel_sub_code = 'toutiao'
  AND t.article_type_code = 'news_brief'
  AND t.question_scene_code = 'brand'
  AND t.name = '今日头条资讯简讯品牌模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET v.user_prompt_template = REPLACE(
      REPLACE(v.user_prompt_template, '{{brandQualification}}', '{{brandQualificationDescription}}'),
      '{{brandCases}}',
      '{{brandCaseDescription}}'
    ),
    v.variables_json = JSON_ARRAY(
      'brandName',
      'category',
      'topicAsQuestion',
      'relatedKeywords',
      'brandPositioning',
      'businessFocus',
      'mainBusiness',
      'brandQualificationDescription',
      'brandCaseDescription',
      'targetAudience',
      'contactBlock'
    )
WHERE t.channel_group_code = 'self_media'
  AND t.channel_sub_code = 'wechat'
  AND t.article_type_code = 'scenario_content'
  AND t.question_scene_code = 'brand'
  AND t.name = '公众号场景品牌信任模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET v.user_prompt_template = REPLACE(
      REPLACE(
        REPLACE(v.user_prompt_template, '{{brandBasicInfo}}', '{{brandIntro}}'),
        '{{brandQualification}}',
        '{{brandQualificationDescription}}'
      ),
      '{{brandCases}}',
      '{{brandCaseDescription}}'
    ),
    v.variables_json = JSON_ARRAY(
      'brandName',
      'category',
      'topicAsQuestion',
      'relatedKeywords',
      'brandIntro',
      'brandQualificationDescription',
      'brandCaseDescription',
      'brandPositioning',
      'businessFocus',
      'contactBlock'
    )
WHERE t.channel_group_code = 'self_media'
  AND t.channel_sub_code = 'netease'
  AND t.article_type_code = 'news_brief'
  AND t.question_scene_code = 'brand'
  AND t.name = '网易资讯简讯品牌模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET v.user_prompt_template = REPLACE(
      REPLACE(
        REPLACE(v.user_prompt_template, '{{brandBasicInfo}}', '{{brandIntro}}'),
        '{{brandQualification}}',
        '{{brandQualificationDescription}}'
      ),
      '{{brandCases}}',
      '{{brandCaseDescription}}'
    ),
    v.variables_json = JSON_ARRAY(
      'brandName',
      'category',
      'topicAsQuestion',
      'relatedKeywords',
      'brandPositioning',
      'businessFocus',
      'mainBusiness',
      'brandQualificationDescription',
      'brandCaseDescription',
      'brandIntro',
      'targetAudience',
      'contactBlock'
    )
WHERE t.channel_group_code = 'self_media'
  AND t.channel_sub_code = 'baijiahao'
  AND t.article_type_code = 'scenario_content'
  AND t.question_scene_code = 'brand'
  AND t.name = '百家号场景品牌信任模板';

UPDATE article_prompt_template
SET updated_at = NOW()
WHERE channel_group_code = 'self_media'
  AND name IN (
    '百家号选择指南决策模板',
    '百家号横向对比评测模板',
    '知乎横向对比深度回答模板',
    '公众号阶段建议成交模板',
    '网易行业分析品牌模板',
    '百家号FAQ问答模板',
    '百家号场景能力内容模板',
    '今日头条资讯简讯品牌模板',
    '公众号场景品牌信任模板',
    '网易资讯简讯品牌模板',
    '百家号场景品牌信任模板'
  );
