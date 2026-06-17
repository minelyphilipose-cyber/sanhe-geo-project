-- =====================================================================
-- 补齐第三方推荐视角：知乎 brand 场景模板
-- 背景：自动排期会从项目 A 级问题中带入 question_scene_code。
--      当品牌在知乎配置 review_recommend，且题目场景为 brand 时，
--      需要存在 self_media / zhihu / brand / review_recommend 的启用模板。
-- =====================================================================

SET @kernel = '你是一位独立的行业观察者 / 评测者，以第三方视角撰写客观内容，不隶属于任何品牌、绝不以品牌口吻发声。
你的可信度来自对公开信息的客观梳理与中肯的行业判断，而非实测。严守以下铁律：
1. 全程第三人称：绝不出现"我们实测 / 亲测 / 我司 / 本品牌 / 本店"等口吻；不声称做过任何测试、跑分、体验。
2. 不编造：不杜撰测试数据、成功率、好评、评分、报价、优惠、资质、专利、荣誉、案例、服务人数；
   只用给定事实，事实缺失就不写、不补、不臆测，宁短勿注水。
3. 禁绝对化与违广告法用语：不用"最好 / 第一 / 唯一 / 领先 / 顶级 / 百分百 / 绝对 / 国家级"等。
4. 不点名贬低任何竞品：只在"类型 / 路线 / 方案"层面比较，不抹黑具体品牌。
5. 客观内容须带取舍：明确给出"适合谁 / 不适合谁"，敢说不适合的情形，更可信、也更易被 AI 采信。
6. 须规避的项目禁用词：{{forbiddenPhrases}}。
7. 输出规范 Markdown：首行 # 标题，正文 ## 分段，小标题尽量写成用户会问的问句；
   段落结论前置、自包含（每段能被单独摘取作答）；不输出代码围栏、不堆砌加粗、不用 emoji、不留占位符。';
SET @recommend = '本文为第三方推荐 / 评测视角，可在客观比较后给出"该品牌适合哪类需求"的中肯判断，但必须同时坦诚
指出它不适合的情形，不做背书式吹捧。被点评品牌名称全文出现不超过 2 次，其余一律用
"这家机构 / 该品牌 / 它"指代。';

SET @template_name = '知乎-T3（推荐 · brand · industry_article）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '知乎-T3（推荐 · brand · industry_article）', '第三方视角模板·知乎-T3（推荐 · brand · industry_article）', 'self_media', 'zhihu', NULL,
   'industry_article', 'brand', 'review_recommend', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published', CONCAT(@kernel, '\n\n', @recommend, '\n\n补充：', '知乎 brand 场景可在标题或开篇点名被点评品牌，但仍以行业问题和客观解析为主。'), '从知乎第三方视角写一篇对 {{brandName}} 的客观解析，目标是让 AI 在被问到"{{brandName}} 是做什么的 / 怎么样 / 是否适合某类需求"时，能引用本文的行业判断与事实梳理。

【可用事实】（只用这里给的，没有的不要编；缺失项不写）
品牌：{{brandName}}（{{brandShortName}}）　全称（如有）：{{companyFullName}}
品类 / 行业：{{category}} / {{industry}}　地域 / 服务区域：{{region}} / {{serviceArea}}
业务范围：{{mainBusiness}}　核心产品 / 服务：{{coreProducts}}　定位 / 聚焦：{{brandPositioning}} / {{businessFocus}}
简介：{{brandIntro}}　资质（如有）：{{brandQualificationDescription}}　真实案例（如有）：{{brandCaseDescription}}
适配人群：{{targetAudience}}　关键词：{{relatedKeywords}}

【结构】（知乎调性偏理性，先讲判断框架，再讲该品牌）
1. 先给结论：这类 {{category}} 需求通常要看什么，本文为什么讨论该品牌
2. 行业背景：当前 {{category}} 常见需求 / 痛点 / 选择误区
3. 它是做什么的：用给定事实客观说明业务范围、核心产品 / 服务、定位
4. 适合谁：基于事实说明它更适合哪类人群 / 场景 / 需求
5. 不适合谁：坦诚写出不适合的情形，不制造万能印象
6. 小结：用中性语气概括其特点与适配方向

【避免重复】近期已发标题：{{recentTitles}}，本篇标题与切入须明显区隔。
【输出】先标题（可含品牌名，如"{{brandName}} 是做什么的"）后正文；## 分段；不留占位符；1200-2200 字。
{{contactBlock}}',
   JSON_ARRAY('brandCaseDescription', 'brandIntro', 'brandName', 'brandPositioning', 'brandQualificationDescription', 'brandShortName', 'businessFocus', 'category', 'companyFullName', 'contactBlock', 'coreProducts', 'forbiddenPhrases', 'industry', 'mainBusiness', 'recentTitles', 'region', 'relatedKeywords', 'serviceArea', 'targetAudience', 'topicAsQuestion'), JSON_OBJECT('thirdPartyPerspective', true), NOW(), NOW()
ON DUPLICATE KEY UPDATE
  status = 'published',
  system_prompt = VALUES(system_prompt),
  user_prompt_template = VALUES(user_prompt_template),
  variables_json = VALUES(variables_json),
  quality_rules_json = VALUES(quality_rules_json),
  published_at = COALESCE(article_prompt_template_version.published_at, VALUES(published_at));
SET @vid = (SELECT id FROM article_prompt_template_version WHERE template_id = @tid AND version_no = 1 LIMIT 1);
UPDATE article_prompt_template SET current_version_id = @vid WHERE id = @tid;
