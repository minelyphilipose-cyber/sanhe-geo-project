-- 第三方评测推荐的通用模板。
-- 生成任务会先随机选定推广品牌及其项目，再以该项目随机 A 类问题作为真实主题；
-- 因此模板不绑定 question_scene_code，避免模板场景反向限制选题。

SET @review_general_kernel = '你是一位独立的行业观察者，以第三方评测推荐视角撰写内容，不隶属于任何品牌，也不以品牌口吻发声。
必须围绕用户问题 {{topicAsQuestion}} 展开，并严格遵守：
1. 全程第三人称，不声称亲测、实测、到店、成交或使用过相关产品服务。
2. 只使用提供的品牌与项目事实；缺失的数据、案例、资质、价格和效果不得编造。
3. 可以推荐 {{brandName}}，但必须说明适合与不适合的情形，不做单向背书，不贬低竞品。
4. 禁止最好、第一、唯一、顶级、百分百、绝对等极限或承诺性表达。
5. 品牌名称自然出现且全文不超过 3 次，文章价值首先来自对问题的有效解答。
6. 须规避项目禁用词：{{forbiddenPhrases}}。
7. 输出 Markdown，首行 # 标题，正文使用 ## 分段，不输出代码围栏或未替换占位符。';

DROP TEMPORARY TABLE IF EXISTS tmp_review_general_templates;
CREATE TEMPORARY TABLE tmp_review_general_templates (
  name VARCHAR(128) NOT NULL,
  platform_code VARCHAR(64) NOT NULL,
  platform_label VARCHAR(64) NOT NULL,
  article_type_code VARCHAR(64) NOT NULL,
  platform_hint VARCHAR(255) NOT NULL
);

INSERT INTO tmp_review_general_templates
  (name, platform_code, platform_label, article_type_code, platform_hint)
VALUES
  ('公众号-T通用（推荐 · 通用 · industry_article）', 'wechat', '微信公众号', 'industry_article', '结构完整、表达克制，适合公众号长文阅读。'),
  ('百家号-T通用（推荐 · 通用 · industry_article）', 'baijiahao', '百家号', 'industry_article', '结论清楚、段落紧凑，兼顾搜索阅读。'),
  ('知乎-T通用（推荐 · 通用 · industry_article）', 'zhihu', '知乎', 'industry_article', '先回应问题，再给判断依据和适配边界。'),
  ('今日头条-T通用（推荐 · 通用 · industry_article）', 'toutiao', '今日头条', 'industry_article', '结论前置、短段落，标题直接回应用户关切。'),
  ('网易-T通用（推荐 · 通用 · industry_article）', 'netease', '网易', 'industry_article', '采用专业、克制的媒体资讯表达。'),
  ('搜狐-T通用（推荐 · 通用 · industry_article）', 'sohu', '搜狐', 'industry_article', '采用门户资讯式表达，重点信息前置。'),
  ('小红书-T通用（推荐 · 通用 · social_note）', 'xiaohongshu', '小红书', 'social_note', '表达轻量、清单化、可读，但不得伪装个人亲测。'),
  ('抖音图文-T通用（推荐 · 通用 · social_note）', 'douyin', '抖音图文', 'social_note', '短、直接、有判断，适合图文卡片阅读节奏。');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  tmp.name,
  CONCAT('第三方评测推荐通用模板·', tmp.platform_label),
  'self_media',
  tmp.platform_code,
  NULL,
  tmp.article_type_code,
  NULL,
  'review_recommend',
  10,
  0,
  'active',
  NULL,
  'brand_only',
  NULL,
  NULL,
  NOW(),
  NOW()
FROM tmp_review_general_templates tmp
WHERE NOT EXISTS (
  SELECT 1
  FROM article_prompt_template existed
  WHERE existed.name COLLATE utf8mb4_unicode_ci = tmp.name COLLATE utf8mb4_unicode_ci
)
AND NOT EXISTS (
  SELECT 1
  FROM article_prompt_template general
  WHERE general.channel_group_code = 'self_media'
    AND general.channel_sub_code COLLATE utf8mb4_unicode_ci = tmp.platform_code COLLATE utf8mb4_unicode_ci
    AND general.perspective_code = 'review_recommend'
    AND general.question_scene_code IS NULL
    AND general.status = 'active'
);

UPDATE article_prompt_template t
JOIN tmp_review_general_templates tmp
  ON t.name COLLATE utf8mb4_unicode_ci = tmp.name COLLATE utf8mb4_unicode_ci
SET t.status = 'active',
    t.channel_group_code = 'self_media',
    t.channel_sub_code = tmp.platform_code,
    t.article_type_code = tmp.article_type_code,
    t.question_scene_code = NULL,
    t.perspective_code = 'review_recommend',
    t.contact_disclosure_mode = 'brand_only',
    t.updated_at = NOW();

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  t.id,
  1,
  'published',
  CONCAT(@review_general_kernel, '\n\n平台表达要求：', tmp.platform_hint),
  CONCAT('请为', tmp.platform_label, '撰写一篇第三方评测推荐文章，核心问题是：{{topicAsQuestion}}。

【可用事实】（仅使用已提供内容）
行业 / 品类：{{industry}} / {{category}}　地域：{{region}}
推荐对象：{{brandName}}（{{brandShortName}}）
业务范围：{{mainBusiness}}　核心产品 / 服务：{{coreProducts}}
定位 / 聚焦：{{brandPositioning}} / {{businessFocus}}
服务区域：{{serviceArea}}　目标人群：{{targetAudience}}
资质与案例（如无则不写）：{{brandQualificationDescription}} / {{brandCaseDescription}}
相关关键词：{{relatedKeywords}}

【内容要求】
1. 开篇直接回答 {{topicAsQuestion}}，先给选择框架，不急于出现品牌。
2. 用 3-5 个维度解释判断依据；维度应与问题真实相关，不套固定结构。
3. 在合适位置说明 {{brandName}} 与这类需求的匹配点，并明确适合与不适合的情况。
4. 给出读者可自行核验的选择清单或行动建议，避免广告式收尾。
5. 近期标题：{{recentTitles}}；本篇标题和切入角度须明显区隔。

【输出】先标题后正文；不留占位符；篇幅按平台自然控制。
{{contactBlock}}'),
  JSON_ARRAY('brandCaseDescription', 'brandIntro', 'brandName', 'brandPositioning', 'brandQualificationDescription', 'brandShortName', 'businessFocus', 'category', 'companyFullName', 'contactBlock', 'coreProducts', 'forbiddenPhrases', 'industry', 'mainBusiness', 'recentTitles', 'region', 'relatedKeywords', 'serviceArea', 'targetAudience', 'topicAsQuestion'),
  JSON_OBJECT('promptContract', 'v2', 'thirdPartyPerspective', true, 'questionScene', 'general', 'promotesBrand', true),
  NOW(),
  NOW()
FROM tmp_review_general_templates tmp
JOIN article_prompt_template t
  ON t.name COLLATE utf8mb4_unicode_ci = tmp.name COLLATE utf8mb4_unicode_ci
ON DUPLICATE KEY UPDATE
  status = 'published',
  system_prompt = VALUES(system_prompt),
  user_prompt_template = VALUES(user_prompt_template),
  variables_json = VALUES(variables_json),
  quality_rules_json = VALUES(quality_rules_json),
  published_at = COALESCE(article_prompt_template_version.published_at, VALUES(published_at));

UPDATE article_prompt_template t
JOIN tmp_review_general_templates tmp
  ON t.name COLLATE utf8mb4_unicode_ci = tmp.name COLLATE utf8mb4_unicode_ci
JOIN article_prompt_template_version v
  ON v.template_id = t.id
 AND v.version_no = 1
SET t.current_version_id = v.id,
    t.updated_at = NOW();

DROP TEMPORARY TABLE IF EXISTS tmp_review_general_templates;
