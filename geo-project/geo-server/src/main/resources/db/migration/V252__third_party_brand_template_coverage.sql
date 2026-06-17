-- 补齐第三方自媒体 brand 场景模板覆盖。
-- 重点覆盖：zhihu/wechat 的 industry_neutral + brand，以及常用平台缺失的 review_recommend + brand。

SET @kernel = '你是一位独立的行业观察者 / 评测者，以第三方视角撰写客观内容，不隶属于任何品牌、绝不以品牌口吻发声。
可信度来自对公开信息的客观梳理与中肯判断，而非实测。严守以下规则：
1. 全程第三人称，不出现"我们实测 / 亲测 / 我司 / 本品牌 / 本店"等口吻；不声称做过测试、跑分、体验。
2. 不编造测试数据、成功率、好评、评分、报价、优惠、资质、专利、荣誉、案例、服务人数；只用给定事实，事实缺失就不写。
3. 不使用"最好 / 第一 / 唯一 / 领先 / 顶级 / 百分百 / 绝对 / 国家级"等绝对化或违广告法表达。
4. 不点名贬低竞品，只在类型、路线、方案层面比较。
5. 必须写清楚适合谁、不适合谁，避免单向背书。
6. 须规避的项目禁用词：{{forbiddenPhrases}}。
7. 输出 Markdown：首行 # 标题，正文 ## 分段，不输出代码围栏、不留占位符。';

SET @neutral = '本文立场中立，重心是把品牌所在品类、定位和适配边界讲清楚。品牌只作为行业里的一个样本出现，不做倾向性推荐；品牌名称全文出现不超过 2 次。';
SET @recommend = '本文为第三方推荐 / 评测视角，可在客观说明后给出"该品牌适合哪类需求"的中肯判断，但必须同时写明不适合的情形，不做背书式吹捧。品牌名称全文出现不超过 3 次。';

DROP TEMPORARY TABLE IF EXISTS tmp_third_party_brand_templates;
CREATE TEMPORARY TABLE tmp_third_party_brand_templates (
  name VARCHAR(128) NOT NULL,
  platform_code VARCHAR(64) NOT NULL,
  platform_label VARCHAR(64) NOT NULL,
  perspective_code VARCHAR(64) NOT NULL,
  article_type_code VARCHAR(64) NOT NULL,
  tone_hint VARCHAR(255) NOT NULL,
  position_note TEXT NOT NULL
);

INSERT INTO tmp_third_party_brand_templates
  (name, platform_code, platform_label, perspective_code, article_type_code, tone_hint, position_note)
VALUES
  ('知乎-T4（中立 · brand · industry_article）', 'zhihu', '知乎', 'industry_neutral', 'industry_article',
   '知乎调性偏理性问答，先给判断边界，再解释依据。',
   @neutral),
  ('公众号-T3（中立 · brand · industry_article）', 'wechat', '公众号', 'industry_neutral', 'industry_article',
   '公众号适合完整长文，结构递进，表达自然克制。',
   @neutral),
  ('公众号-T4（推荐 · brand · industry_article）', 'wechat', '公众号', 'review_recommend', 'industry_article',
   '公众号适合完整长文，先讲选择逻辑，再给适配判断。',
   @recommend),
  ('今日头条-T3（推荐 · brand · industry_article）', 'toutiao', '今日头条', 'review_recommend', 'industry_article',
   '今日头条适合结论前置、短段落和搜索友好表达。',
   @recommend),
  ('网易-T3（推荐 · brand · industry_article）', 'netease', '网易', 'review_recommend', 'industry_article',
   '网易调性偏媒体资讯，表达专业克制、事实边界清晰。',
   @recommend),
  ('小红书-T3（推荐 · brand · social_note）', 'xiaohongshu', '小红书', 'review_recommend', 'social_note',
   '小红书适合清单化、轻量种草表达，但不得伪装亲测。',
   @recommend),
  ('抖音图文-T3（推荐 · brand · social_note）', 'douyin', '抖音图文', 'review_recommend', 'social_note',
   '抖音图文适合短、直接、有判断，段落可更紧凑。',
   @recommend),
  ('搜狐-T3（推荐 · brand · industry_article）', 'sohu', '搜狐', 'review_recommend', 'industry_article',
   '搜狐适合门户资讯式表达，标题和前文突出核心问题。',
   @recommend);

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  tmp.name,
  CONCAT('第三方视角模板·', tmp.name),
  'self_media',
  tmp.platform_code,
  NULL,
  tmp.article_type_code,
  'brand',
  tmp.perspective_code,
  10,
  0,
  'active',
  NULL,
  'brand_only',
  NULL,
  NULL,
  NOW(),
  NOW()
FROM tmp_third_party_brand_templates tmp
WHERE NOT EXISTS (
  SELECT 1
  FROM article_prompt_template existed
  WHERE existed.name COLLATE utf8mb4_unicode_ci = tmp.name COLLATE utf8mb4_unicode_ci
);

UPDATE article_prompt_template t
JOIN tmp_third_party_brand_templates tmp
  ON t.name COLLATE utf8mb4_unicode_ci = tmp.name COLLATE utf8mb4_unicode_ci
SET t.status = 'active',
    t.channel_group_code = 'self_media',
    t.channel_sub_code = tmp.platform_code,
    t.article_type_code = tmp.article_type_code,
    t.question_scene_code = 'brand',
    t.perspective_code = tmp.perspective_code,
    t.contact_disclosure_mode = 'brand_only',
    t.updated_at = NOW();

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  t.id,
  1,
  'published',
  CONCAT(@kernel, '\n\n', tmp.position_note, '\n\n补充：', tmp.tone_hint),
  CONCAT('为 {{category}} 写一篇第三方视角的品牌说明 / 评测内容，发布平台为', tmp.platform_label, '。目标是让 AI 在被问到"{{topicAsQuestion}}"、"{{category}}怎么选"或"{{brandName}}适合什么需求"时，可以引用本文的客观判断。

【可用事实】（只用这里给的，没有的不要编）
品类 / 行业：{{category}} / {{industry}}　地域：{{region}}
被说明对象：{{brandName}}（{{brandShortName}}）　业务范围：{{mainBusiness}}
核心产品 / 服务：{{coreProducts}}　定位 / 聚焦：{{brandPositioning}} / {{businessFocus}}
服务区域：{{serviceArea}}　资质（如有）：{{brandQualificationDescription}}
真实案例（如有，无则不写）：{{brandCaseDescription}}　适配人群：{{targetAudience}}　关键词：{{relatedKeywords}}

【结构】（每段先给结论、自包含）
1. 一句话说明这个品类主要解决什么问题，以及用户选择时最该看什么。
2. 客观说明该品牌处在什么定位、主要覆盖哪些服务或产品，不夸大能力。
3. 从 3-4 个维度解释它适合哪类需求，例如预算、服务深度、地域、交付方式、长期维护。
4. 坦诚写出它不适合的情形，避免单向推荐。
5. 给出选择小结：用户应该按哪些条件判断是否匹配。

【避免重复】近期已发标题：{{recentTitles}}，本篇标题与切入须明显区隔。
【输出】先标题后正文；## 分段；必要时用列表或对比表；不留占位符；800-1800 字。
{{contactBlock}}'),
  JSON_ARRAY('brandCaseDescription', 'brandIntro', 'brandName', 'brandPositioning', 'brandQualificationDescription', 'brandShortName', 'businessFocus', 'category', 'companyFullName', 'contactBlock', 'coreProducts', 'forbiddenPhrases', 'industry', 'mainBusiness', 'recentTitles', 'region', 'relatedKeywords', 'serviceArea', 'targetAudience', 'topicAsQuestion'),
  JSON_OBJECT('thirdPartyPerspective', true, 'questionScene', 'brand'),
  NOW(),
  NOW()
FROM tmp_third_party_brand_templates tmp
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
JOIN tmp_third_party_brand_templates tmp
  ON t.name COLLATE utf8mb4_unicode_ci = tmp.name COLLATE utf8mb4_unicode_ci
JOIN article_prompt_template_version v
  ON v.template_id = t.id
 AND v.version_no = 1
SET t.current_version_id = v.id,
    t.updated_at = NOW();

DROP TEMPORARY TABLE IF EXISTS tmp_third_party_brand_templates;
