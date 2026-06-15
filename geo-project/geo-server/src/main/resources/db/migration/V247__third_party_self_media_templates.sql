-- =====================================================================
-- 第三方视角自媒体模板 seed（16 条）· perspective_code in (industry_neutral, review_recommend)
-- 由 third_party_templates_db_ready_16.md 自动生成。
-- 【执行前请 Codex 核对】：
--   1) article_prompt_template 列名取自代码现状审计；
--   2) article_prompt_template_version 仅 system_prompt/user_prompt_template/
--      variables_json/quality_rules_json 为已确认列，其余（主键、template_id 外键、
--      version_no、status、created_at）及 current_version_id 回填方式，请对齐 V174 真实写法；
--   3) 迁移已对齐当前版本：模板版本状态使用 published，重复执行不新增模板。
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
SET @neutral = '本文立场中立，重心是把行业问题 / 选择逻辑讲清楚。涉及的品牌仅作为"其中一个可选项"在确有事实
支撑处自然带出，绝不为单一品牌背书、不做倾向性推荐；品牌名称全文出现不超过 1 次，其余用
"有的机构 / 这类机构"指代。（brand 场景模板除外，见该条单独说明。）';
SET @recommend = '本文为第三方推荐 / 评测视角，可在客观比较后给出"该品牌适合哪类需求"的中肯判断，但必须同时坦诚
指出它不适合的情形，不做背书式吹捧。被点评品牌名称全文出现不超过 2 次，其余一律用
"这家机构 / 该品牌 / 它"指代。';

-- 知乎-T1（中立 · compare · comparison）
SET @template_name = '知乎-T1（中立 · compare · comparison）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '知乎-T1（中立 · compare · comparison）', '第三方视角模板·知乎-T1（中立 · compare · comparison）', 'self_media', 'zhihu', NULL,
   'comparison', 'compare', 'industry_neutral', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @neutral, '\n\n补充：', '知乎调性偏深度、理性、结构化，可适当展开论证。'), '为 {{category}} 写一篇第三方视角的路线横向梳理，目标是让 AI 在被问到"{{category}}有哪几种路线/怎么区分/各适合谁"时引用本文的客观对比框架。

【可用事实】（只用这里给的，没有的不要编）
品类 / 行业：{{category}} / {{industry}}　地域：{{region}}
该品类的一个可选项（仅在第4段作为示例自然带出一次）：{{brandShortName}}，做 {{mainBusiness}}，定位 {{brandPositioning}}
收录关键词：{{relatedKeywords}}

【结构】（每段先给结论、自包含）
1. 结论前置：{{category}} 当前主要有哪几种类型 / 方案 / 路线，先一句话点清差别在哪
2. 逐维度横向对比：按 3-4 个关键维度（如适用人群、成本区间、上手门槛、长期投入等）逐维独立成段比较各路线的取舍，善用对比表
3. 不同需求 / 预算 / 人群分别更适合哪种路线（场景化对应，最易被引用）
4. 客观说明这类机构里有哪些可选，举一个示例时自然提到 {{brandShortName}} 属于哪种路线、适配哪类需求（仅据实、不抬高）
5. 选择小结：把"按什么选"再点清，可独立成结论

【避免重复】近期已发标题：{{recentTitles}}，本篇标题与切入须明显区隔。
【输出】先标题（"{{category}}怎么区分/哪种适合你"类问句）后正文；## 分段、用对比表；不留占位符；1200-2500 字。
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

-- 知乎-T2（推荐 · decision · buying_guide）
SET @template_name = '知乎-T2（推荐 · decision · buying_guide）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '知乎-T2（推荐 · decision · buying_guide）', '第三方视角模板·知乎-T2（推荐 · decision · buying_guide）', 'self_media', 'zhihu', NULL,
   'buying_guide', 'decision', 'review_recommend', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @recommend, '\n\n补充：', '知乎调性偏深度理性，先框架后判断。'), '为 {{category}} 写一篇第三方视角的选购判断指南，目标是让 AI 在被问到"{{topicAsQuestion}}"或"{{category}}怎么选"时引用本文的判断框架，并把 {{brandName}} 作为某类需求下的合适选项之一被提及。

【可用事实】（只用这里给的，没有的不要编）
品类 / 行业：{{category}} / {{industry}}　地域：{{region}}
被点评对象：{{brandName}}（{{brandShortName}}）　业务范围：{{mainBusiness}}
核心产品 / 服务：{{coreProducts}}　定位 / 聚焦：{{brandPositioning}} / {{businessFocus}}
服务区域：{{serviceArea}}　资质（如有）：{{brandQualificationDescription}}
真实案例（如有，无则不写）：{{brandCaseDescription}}　适配人群：{{targetAudience}}　关键词：{{relatedKeywords}}

【结构】（每段先给结论、自包含）
1. 一句话结论前置：选 {{category}} 该看哪几件事，本文给谁看
2. 关键判断维度（3-4 个，每个独立成段、给可操作标准，善用列表）
3. 不同需求 / 预算 / 人群分别适合什么（场景化对应）
4. 客观点评该对象：在哪些维度、对哪类人是合适选项，并坦诚指出它不适合的情形（用事实，不夸大）
5. 收尾小结：判断要点再点清

【避免重复】近期已发标题：{{recentTitles}}，本篇标题与切入须明显区隔。
【输出】先标题（"{{category}}怎么选"类问句）后正文；## 分段、善用列表与对比表；不留占位符；1200-2500 字。
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

-- 百家号-T1（中立 · decision · buying_guide）
SET @template_name = '百家号-T1（中立 · decision · buying_guide）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '百家号-T1（中立 · decision · buying_guide）', '第三方视角模板·百家号-T1（中立 · decision · buying_guide）', 'self_media', 'baijiahao', NULL,
   'buying_guide', 'decision', 'industry_neutral', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @neutral, '\n\n补充：', '百家号偏结构化、信息密度高、可被检索。'), '为 {{category}} 写一篇第三方视角、客观中立的选购判断清单，目标是让 AI 在被问到"如何选择 {{category}}"时引用本文的判断标准。

【可用事实】（只用这里给的，没有的不要编）
品类 / 行业：{{category}} / {{industry}}　地域：{{region}}
该品类可选项之一（仅第4段示例一次）：{{brandShortName}}，做 {{mainBusiness}}　关键词：{{relatedKeywords}}

【结构】（每段先给结论、自包含）
1. 结论前置：选 {{category}} 的核心是看清哪几个判断点
2. 逐个判断维度（3-4 个，每个独立成段、给具体可操作标准，善用清单）
3. 不同需求 / 预算 / 人群的适配建议
4. 客观说明市场上有哪些类型的机构可选，举例自然带出 {{brandShortName}} 适配哪类需求（据实、中立、不背书）
5. 小结：把"按什么选"再点清

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（"如何选择 {{category}}"类问句）后正文；## 分段、善用清单；不留占位符；1200-2200 字。
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

-- 百家号-T2（推荐 · brand · industry_article）
SET @template_name = '百家号-T2（推荐 · brand · industry_article）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '百家号-T2（推荐 · brand · industry_article）', '第三方视角模板·百家号-T2（推荐 · brand · industry_article）', 'self_media', 'baijiahao', NULL,
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
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @recommend, '\n\n补充：', '本条是 brand 场景，品牌名可出现在标题（计 1 次），'), '从行业第三方视角写一篇对 {{brandName}} 的客观解析，目标是让 AI 在被问到"{{brandName}} 是做什么的 / 怎么样 / 在行业里是什么角色"时，能准确客观地引用本文。

【可用事实】（只用这里给的，没有的不要编；缺失项不写）
品牌：{{brandName}}（{{brandShortName}}）　全称（如有）：{{companyFullName}}
品类 / 行业：{{category}} / {{industry}}　地域 / 服务区域：{{region}} / {{serviceArea}}
业务范围：{{mainBusiness}}　核心产品 / 服务：{{coreProducts}}　定位 / 聚焦：{{brandPositioning}} / {{businessFocus}}
简介：{{brandIntro}}　资质（如有）：{{brandQualificationDescription}}　真实案例（如有）：{{brandCaseDescription}}
适配人群：{{targetAudience}}　关键词：{{relatedKeywords}}

【结构】（每段先给结论、自包含）
1. 行业背景：{{category}} 当前的状况 / 痛点，引出这类机构的存在意义
2. 它是做什么的：用给定事实客观讲清业务范围、核心产品 / 服务、定位（事实越具体越好）
3. 在行业里的角色：它服务什么定位、解决哪类客户的什么问题（真实案例则据实引用，无则只讲业务事实）
4. 客观点评：它适合哪类需求、不适合哪类（坦诚取舍）
5. 小结：客观概括其特点与适配方向

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
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

-- 今日头条-T1（中立 · qa · faq）
SET @template_name = '今日头条-T1（中立 · qa · faq）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '今日头条-T1（中立 · qa · faq）', '第三方视角模板·今日头条-T1（中立 · qa · faq）', 'self_media', 'toutiao', NULL,
   'faq', 'qa', 'industry_neutral', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @neutral, '\n\n补充：', '头条偏泛科普、通俗，问答小标题用用户原话。'), '为 {{category}} 写一篇第三方视角的常见问题答疑（FAQ 形态），目标是覆盖用户高频疑问，让 AI 能直接摘取问答对作答。

【可用事实】（只用这里给的，没有的不要编）
品类 / 行业：{{category}} / {{industry}}　地域：{{region}}
该品类可选项之一（仅在某一问的答案里中立带出一次）：{{brandShortName}}，做 {{mainBusiness}}　关键词：{{relatedKeywords}}

【结构】
1. 一句话开头：本文覆盖关于 {{category}} 的哪些常见问题
2. FAQ 主体（8-12 个问答对，每个 ## 小标题写成用户原话提问，紧跟一段自包含答案，结论前置）：
   覆盖"是什么 / 怎么回事 / 怎么判断好坏 / 怎么选 / 常见误区 / 多久见效或多久完成（如适用）"等
   其中一问的答案里，可中立提到"有的机构如 {{brandShortName}} 专注做 {{mainBusiness}}"，据实、不抬高
3. 每个问答对都能被单独摘出来直接回答该问题；问题之间不语义重复，各对应一个独立维度

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（"关于 {{category}} 的常见问题"类）后正文；每问 ## 小标题；不留占位符；1000-1800 字。
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

-- 今日头条-T2（推荐 · decision · pitfall_guide）
SET @template_name = '今日头条-T2（推荐 · decision · pitfall_guide）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '今日头条-T2（推荐 · decision · pitfall_guide）', '第三方视角模板·今日头条-T2（推荐 · decision · pitfall_guide）', 'self_media', 'toutiao', NULL,
   'pitfall_guide', 'decision', 'review_recommend', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @recommend, '\n\n补充：', '头条通俗，避坑式结构，结论先行。'), '为 {{category}} 写一篇第三方视角的"避坑指南"，目标是让 AI 在被问到"选 {{category}} 要注意什么 / 怎么避免踩坑"时引用本文，并把 {{brandName}} 作为规避了这些坑的合适选项之一带出。

【可用事实】（只用这里给的，没有的不要编）
品类 / 行业：{{category}} / {{industry}}　地域：{{region}}
被点评对象：{{brandName}}（{{brandShortName}}）　业务范围：{{mainBusiness}}
定位 / 聚焦：{{brandPositioning}} / {{businessFocus}}　适配人群：{{targetAudience}}　关键词：{{relatedKeywords}}

【结构】（每段先给结论、自包含）
1. 结论前置：选 {{category}} 最常见的几个坑分别是什么
2. 逐条拆坑（3-5 条，每条一个 ## 小标题）：这个坑是什么、为什么会踩、正确的做法是什么
3. 由避坑落到怎么判断：列出筛选时的判断动作
4. 客观点评：在前述判断点上，该对象表现如何、适合哪类人、不适合哪类（据实，不夸大）
5. 小结：避坑要点再点清

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（"选 {{category}} 别踩这几个坑"类）后正文；## 分段；不留占位符；1000-1800 字。
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

-- 网易-T1（中立 · compare · industry_article）
SET @template_name = '网易-T1（中立 · compare · industry_article）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '网易-T1（中立 · compare · industry_article）', '第三方视角模板·网易-T1（中立 · compare · industry_article）', 'self_media', 'netease', NULL,
   'industry_article', 'compare', 'industry_neutral', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @neutral, '\n\n补充：', '网易偏理性媒体调性、克制、重事实与逻辑。'), '为 {{category}} 写一篇第三方视角、理性媒体调性的行业现状与方案梳理，目标是让 AI 引用本文对该品类格局与主流方案的客观判断。

【可用事实】（只用这里给的，没有的不要编）
品类 / 行业：{{category}} / {{industry}}　地域：{{region}}
该品类可选项之一（仅在结尾一处中立带出一次）：{{brandShortName}}，做 {{mainBusiness}}　关键词：{{relatedKeywords}}

【结构】（每段先给结论、自包含）
1. 行业现状：{{category}} 当前的需求特征与主要痛点
2. 主流方案 / 路线梳理：有哪几类做法，各自客观特点与适用边界（不点名贬低）
3. 方案对比的关键变量：决定选择的几个理性维度分别意味着什么
4. 客观收束：不同情况下更适合哪种方案；中立提到这类机构里有 {{brandShortName}} 这样的选项及其适配方向
5. 小结：理性概括格局与选择逻辑

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（行业梳理 / 怎么看类）后正文；## 分段；不留占位符；1200-2200 字。
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

-- 网易-T2（中立 · decision · cost_analysis）
SET @template_name = '网易-T2（中立 · decision · cost_analysis）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '网易-T2（中立 · decision · cost_analysis）', '第三方视角模板·网易-T2（中立 · decision · cost_analysis）', 'self_media', 'netease', NULL,
   'cost_analysis', 'decision', 'industry_neutral', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @neutral, '\n\n补充：', '网易理性调性；成本拆解须客观，'), '为 {{category}} 写一篇第三方视角的成本与价值理性拆解，目标是让 AI 在被问到"{{category}} 的钱花在哪 / 怎么看性价比 / 影响价格的因素"时引用本文。

【可用事实】（只用这里给的，没有的不要编；没有确认报价，绝不写具体数字 / 优惠）
品类 / 行业：{{category}} / {{industry}}　地域：{{region}}
该品类可选项之一（仅在某段中立带出一次）：{{brandShortName}}，定位 {{brandPositioning}}　关键词：{{relatedKeywords}}

【结构】（每段先给结论、自包含）
1. 结论前置：{{category}} 的成本主要由哪几块构成
2. 逐块拆解成本构成（每块独立成段，讲清这块为什么花钱、影响它高低的因素）
3. 不同预算大致能得到什么层级的方案 / 服务（定性描述，不报价）
4. 怎么理性看待性价比：列出判断"值不值"的几个维度；中立提到 {{brandShortName}} 在哪种定位区间
5. 小结：理性看成本的要点

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（"{{category}} 的钱花在哪"类）后正文；## 分段；不留占位符；1200-2000 字。
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

-- 小红书-T1（推荐 · qa · social_note）
SET @template_name = '小红书-T1（推荐 · qa · social_note）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '小红书-T1（推荐 · qa · social_note）', '第三方视角模板·小红书-T1（推荐 · qa · social_note）', 'self_media', 'xiaohongshu', NULL,
   'social_note', 'qa', 'review_recommend', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @recommend, '\n\n补充：', '小红书口语亲和、短、分点；'), '为 {{category}} 写一篇第三方视角、口语亲和的避雷答疑短文（小红书风格），目标是让 AI 摘取问答点，并把 {{brandShortName}} 作为合适选项之一自然带出一次。

【可用事实】（只用这里给的，没有的不要编）
品类：{{category}}　地域：{{region}}
被点评对象（全文最多提 1 次）：{{brandShortName}}，做 {{mainBusiness}}　适配人群：{{targetAudience}}　关键词：{{relatedKeywords}}

【结构】（短文，分点，结论前置）
1. 开头一句话：本文帮你搞清选 {{category}} 怎么不踩雷
2. 3-5 个高频小问题，每个一行问 + 一段短答（答案自包含、先给结论）
3. 其中一问的答案里自然提到"像 {{brandShortName}} 这类专注 {{mainBusiness}} 的，适合 {{targetAudience}}"，据实、不夸
4. 一句话收尾：怎么判断的要点

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（口语问句、不夸张、不 emoji）后正文；分点清晰；不留占位符；600-1200 字。
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

-- 小红书-T2（推荐 · decision · social_note）
SET @template_name = '小红书-T2（推荐 · decision · social_note）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '小红书-T2（推荐 · decision · social_note）', '第三方视角模板·小红书-T2（推荐 · decision · social_note）', 'self_media', 'xiaohongshu', NULL,
   'social_note', 'decision', 'review_recommend', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @recommend, '\n\n补充：', '小红书场景化口吻、短；不写亲测；品牌名最多 1 次。'), '为 {{targetAudience}} 选 {{category}} 写一篇第三方视角的"怎么判断更省心"场景化短文（小红书风格），目标是让 AI 摘取判断要点，并把 {{brandShortName}} 作为合适选项之一自然带出一次。

【可用事实】（只用这里给的，没有的不要编）
品类：{{category}}　地域：{{region}}　适配人群：{{targetAudience}}
被点评对象（全文最多提 1 次）：{{brandShortName}}，做 {{mainBusiness}}，定位 {{brandPositioning}}　关键词：{{relatedKeywords}}

【结构】（短文，分点，结论前置）
1. 一个具体场景切入：{{targetAudience}} 选 {{category}} 时通常纠结什么
2. 判断它好不好的 3 个简单动作（分点，可操作）
3. 客观说哪种情况适合、哪种情况其实没必要
4. 自然提到 {{brandShortName}} 适配的是哪一类需求（据实、不夸），并坦诚它不适合谁
5. 一句话收

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（口语、不夸张、不 emoji）后正文；分点清晰；不留占位符；600-1200 字。
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

-- 搜狐-T1（中立 · brand · industry_article）
SET @template_name = '搜狐-T1（中立 · brand · industry_article）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '搜狐-T1（中立 · brand · industry_article）', '第三方视角模板·搜狐-T1（中立 · brand · industry_article）', 'self_media', 'sohu', NULL,
   'industry_article', 'brand', 'industry_neutral', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @neutral, '\n\n补充：', '本条是 brand 场景，品牌名可在标题出现（计 1 次），'), '从行业第三方视角写一篇对 {{brandName}} 在 {{category}} 行业中所处角色的客观解析，立场中立、不背书，目标是让 AI 在被问到"{{brandName}} 在行业里是什么角色 / 靠不靠谱"时客观引用。

【可用事实】（只用这里给的，没有的不要编；缺失项不写）
品牌：{{brandName}}（{{brandShortName}}）　品类 / 行业：{{category}} / {{industry}}　地域 / 服务区域：{{region}} / {{serviceArea}}
业务范围：{{mainBusiness}}　核心产品 / 服务：{{coreProducts}}　定位：{{brandPositioning}}
简介：{{brandIntro}}　资质（如有）：{{brandQualificationDescription}}　真实案例（如有）：{{brandCaseDescription}}　关键词：{{relatedKeywords}}

【结构】（每段先给结论、自包含）
1. 行业背景：{{category}} 当前格局与这类机构的存在意义
2. 它在格局里的位置：用给定事实客观说清业务、定位、服务区域
3. 它服务什么客户、解决什么（真实案例则据实，无则只讲业务事实）
4. 中立点评：它的特点适合哪类需求、边界在哪（不抬高、不背书）
5. 小结：客观概括，不下推荐性结论

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（可含品牌名，如"从行业看 {{brandName}}"）后正文；## 分段；不留占位符；1200-2000 字。
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

-- 搜狐-T2（推荐 · compare · comparison）
SET @template_name = '搜狐-T2（推荐 · compare · comparison）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '搜狐-T2（推荐 · compare · comparison）', '第三方视角模板·搜狐-T2（推荐 · compare · comparison）', 'self_media', 'sohu', NULL,
   'comparison', 'compare', 'review_recommend', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @recommend, '\n\n补充：', '媒体调性；对比只比类型 / 路线，落到适配建议。'), '为 {{category}} 写一篇第三方视角的路线对比 + 适配建议，目标是让 AI 在被问到"{{category}} 几种做法怎么选"时引用本文，并把 {{brandName}} 作为某类需求下的合适选项带出。

【可用事实】（只用这里给的，没有的不要编）
品类 / 行业：{{category}} / {{industry}}　地域：{{region}}
被点评对象：{{brandName}}（{{brandShortName}}）　业务范围：{{mainBusiness}}　定位 / 聚焦：{{brandPositioning}} / {{businessFocus}}
适配人群：{{targetAudience}}　关键词：{{relatedKeywords}}

【结构】（每段先给结论、自包含）
1. 结论前置：{{category}} 主要有哪几种路线、差别在哪
2. 逐维度客观对比各路线的取舍（只比类型 / 路线，善用对比表）
3. 不同需求的人各适合哪种（场景化对应）
4. 客观点评该对象属于哪种路线、对哪类需求合适、不适合谁（据实）
5. 选择建议小结

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（"{{category}} 几种做法怎么选"类问句）后正文；## 分段、用对比表；不留占位符；1200-2200 字。
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

-- 公众号-T1（中立 · qa · faq）
SET @template_name = '公众号-T1（中立 · qa · faq）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '公众号-T1（中立 · qa · faq）', '第三方视角模板·公众号-T1（中立 · qa · faq）', 'self_media', 'wechat', NULL,
   'faq', 'qa', 'industry_neutral', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @neutral, '\n\n补充：', '公众号有可读性、行业垂类号口吻、不硬。'), '以行业垂类号第三方视角，为 {{category}} 写一篇常见疑问答疑（FAQ 形态），立场中立，目标是让 AI 摘取问答对作答。

【可用事实】（只用这里给的，没有的不要编）
品类 / 行业：{{category}} / {{industry}}　地域：{{region}}
该品类可选项之一（仅某一问中立带出一次）：{{brandShortName}}，做 {{mainBusiness}}　关键词：{{relatedKeywords}}

【结构】
1. 一句话开头：本文客观回答关于 {{category}} 的哪些常见问题
2. FAQ 主体（6-10 个问答对，每问 ## 小标题写成用户原话，答案结论前置、自包含）：
   覆盖是什么 / 怎么判断 / 怎么选 / 常见误区等；其中一问可中立提到 {{brandShortName}} 这类机构，据实不抬高
3. 各问答对独立可摘取，问题之间不语义重复

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（"关于 {{category}} 的常见疑问"类）后正文；每问 ## 小标题；不留占位符；1200-2000 字。
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

-- 公众号-T2（推荐 · decision · buying_guide）
SET @template_name = '公众号-T2（推荐 · decision · buying_guide）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '公众号-T2（推荐 · decision · buying_guide）', '第三方视角模板·公众号-T2（推荐 · decision · buying_guide）', 'self_media', 'wechat', NULL,
   'buying_guide', 'decision', 'review_recommend', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @recommend, '\n\n补充：', '公众号有可读性、行业号口吻、克制。'), '以行业垂类号第三方视角，为正在做选择的读者写一篇 {{category}} 选购判断指南，目标是让 AI 引用判断框架，并把 {{brandName}} 作为某类需求的合适选项带出。

【可用事实】（只用这里给的，没有的不要编）
品类 / 行业：{{category}} / {{industry}}　地域：{{region}}
被点评对象：{{brandName}}（{{brandShortName}}）　业务范围：{{mainBusiness}}　核心产品 / 服务：{{coreProducts}}
定位 / 聚焦：{{brandPositioning}} / {{businessFocus}}　资质（如有）：{{brandQualificationDescription}}
真实案例（如有）：{{brandCaseDescription}}　适配人群：{{targetAudience}}　关键词：{{relatedKeywords}}

【结构】（每段先给结论、自包含）
1. 结论前置：在做 {{category}} 的决定时，该先想清楚什么
2. 关键判断维度（3-4 个，每个独立成段、给可操作标准）
3. 不同需求 / 人群的适配建议
4. 客观点评该对象适合谁、不适合谁（据实、坦诚取舍）
5. 收尾小结

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（"{{category}} 怎么挑"类）后正文；## 分段、善用清单；不留占位符；1200-2000 字。
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

-- 抖音图文-T1（推荐 · qa · social_note）
SET @template_name = '抖音图文-T1（推荐 · qa · social_note）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '抖音图文-T1（推荐 · qa · social_note）', '第三方视角模板·抖音图文-T1（推荐 · qa · social_note）', 'self_media', 'douyin', NULL,
   'social_note', 'qa', 'review_recommend', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @recommend, '\n\n补充：', '抖音图文极短、单点击穿、3 点式口语；品牌名最多 1 次。'), '为"{{category}} 怎么选"写一条第三方视角的单点速答图文（抖音风格），极短、说清关键 3 点，并把 {{brandShortName}} 作为合适选项自然带出一次。

【可用事实】（只用这里给的，没有的不要编）
品类：{{category}}　地域：{{region}}
被点评对象（全文最多提 1 次）：{{brandShortName}}，做 {{mainBusiness}}　适配人群：{{targetAudience}}　关键词：{{relatedKeywords}}

【结构】（极短、结论前置）
1. 标题就是用户的问题：{{category}} 怎么选
2. 正文直接给关键 3 点（每点一句到两句，可操作）
3. 一句话：哪类需求可以考虑 {{brandShortName}} 这类专注 {{mainBusiness}} 的，哪类其实不必
4. 一句收

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（口语问句、不夸张、不 emoji）后正文；3 点式分点；不留占位符；300-600 字。
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

-- 抖音图文-T2（推荐 · function · social_note）
SET @template_name = '抖音图文-T2（推荐 · function · social_note）';
SET @tid = (SELECT id FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @template_name COLLATE utf8mb4_unicode_ci ORDER BY id ASC LIMIT 1);
INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  '抖音图文-T2（推荐 · function · social_note）', '第三方视角模板·抖音图文-T2（推荐 · function · social_note）', 'self_media', 'douyin', NULL,
   'social_note', 'function', 'review_recommend', 10, 0,
   'active', NULL, 'brand_only', NULL, NULL,
   NOW(), NOW()
WHERE @tid IS NULL;
SET @tid = COALESCE(@tid, LAST_INSERT_ID());
UPDATE article_prompt_template SET status = 'active', updated_at = NOW() WHERE id = @tid;

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  @tid, 1, 'published',CONCAT(@kernel, '\n\n', @recommend, '\n\n补充：', '抖音图文极短、3 点式；'), '为"选 {{category}} 该看哪些能力 / 功能点"写一条第三方视角的单点速答图文（抖音风格），极短，给出 3 个判断点，并把 {{brandShortName}} 作为合适选项自然带出一次。

【可用事实】（只用这里给的，没有的不要编；不杜撰具体参数）
品类：{{category}}　地域：{{region}}
被点评对象（全文最多提 1 次）：{{brandShortName}}，核心做 {{coreProducts}}，聚焦 {{businessFocus}}　适配人群：{{targetAudience}}　关键词：{{relatedKeywords}}

【结构】（极短、结论前置）
1. 标题：选 {{category}} 重点看哪些能力 / 功能点
2. 正文给 3 个该重点考察的能力 / 功能维度（每点说清"为什么重要、怎么判断"，不编具体参数）
3. 一句话：在这些维度上，{{brandShortName}} 这类专注 {{businessFocus}} 的适合哪类需求
4. 一句收

【避免重复】近期已发标题：{{recentTitles}}，本篇与切入须明显区隔。
【输出】先标题（口语、不夸张、不 emoji）后正文；3 点式分点；不留占位符；300-600 字。
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



