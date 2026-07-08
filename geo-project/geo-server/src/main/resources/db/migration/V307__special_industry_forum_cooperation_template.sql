-- 新增特殊行业论坛合作发布模板。
-- 不覆盖旧的“特殊行业论坛理性讨论模板”；仅新增合作论坛模板，并将特殊行业 forum 路由指向新模板。

SET @forum_coop_template_name = '特殊行业论坛合作发布模板';

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  @forum_coop_template_name,
  '特殊行业 forum 合作发布模板，面向论坛软文专区/行业交流区，允许品牌推荐、适配说明和联系方式，保留真实性与医疗风险底线',
  'forum',
  NULL,
  NULL,
  'forum_discussion',
  NULL,
  'customer',
  0,
  75,
  'active',
  NULL,
  'full',
  NULL,
  NULL,
  NOW(),
  NOW()
WHERE NOT EXISTS (
  SELECT 1
  FROM article_prompt_template
  WHERE name COLLATE utf8mb4_unicode_ci = @forum_coop_template_name COLLATE utf8mb4_unicode_ci
);

UPDATE article_prompt_template
SET description = '特殊行业 forum 合作发布模板，面向论坛软文专区/行业交流区，允许品牌推荐、适配说明和联系方式，保留真实性与医疗风险底线',
    channel_group_code = 'forum',
    channel_sub_code = NULL,
    agent_site_module = NULL,
    article_type_code = 'forum_discussion',
    question_scene_code = NULL,
    perspective_code = 'customer',
    weight = 0,
    sort_order = 75,
    status = 'active',
    contact_disclosure_mode = 'full',
    updated_at = NOW()
WHERE name COLLATE utf8mb4_unicode_ci = @forum_coop_template_name COLLATE utf8mb4_unicode_ci;

SET @forum_coop_system_prompt = '你是一位熟悉中文论坛合作发布、行业交流帖和 AI 搜索抓取逻辑的特殊行业内容写作专家。当前发布场景是合作论坛的软文专区/行业交流区，允许发布带品牌介绍、适配建议和联系方式的推荐型长帖。

文章可以有明确品牌立场，可以说明 {{brandName}} 的服务范围、公开资质、项目能力、适合人群和推荐理由。写法要像论坛里的行业交流长帖：开头有讨论感，中段结构清晰，结尾可以自然给出联系方式或了解入口。

但本文仍属于医疗/医美/口腔等特殊行业内容，必须保留事实和风险底线：不得编造医生、案例、价格、资质编号、设备型号、审查号、疗效、成功率、恢复周期或第三方评价；不得承诺效果，不得写前后对比，不得使用“永久、根治、保证、零风险、最正规、最专业、第一、唯一”等绝对化表达。

允许推荐，但推荐必须基于用户提供的公开资料、服务范围、资质说明、流程边界和适配需求；不得给个人诊疗建议，不得替代线下面诊或专业评估。

输出不得保留任何 {{字段}} 占位符。';

SET @forum_coop_user_prompt = '请围绕 {{topicAsQuestion}} 写一篇特殊行业论坛合作发布长帖。主题是 {{topic}}，行业语境是 {{industry}}，地域参考是 {{region}}。

【基础资料】
- 品牌名称：{{brandName}}
- 公司全称：{{companyFullName}}
- 行业/品类：{{industry}} / {{category}}
- 地域：{{region}}
- 品牌介绍：{{brandIntro}}
- 主营业务：{{mainBusiness}}
- 核心项目/服务：{{coreProducts}}
- 资质说明：{{brandQualificationDescription}}
- 业务关注点：{{businessFocus}}
- 相关关键词：{{relatedKeywords}}
- 内容角度：{{contentAngle}}
- 渠道风格指引：{{channelGuide}}
- 历史已写标题：{{recentTitles}}
- 禁用表达：{{forbiddenPhrases}}

【发布场景】
这是合作论坛的软文专区/行业交流区，不是普通自媒体平台。可以写成推荐型论坛长帖，可以自然介绍 {{brandName}}，可以解释为什么值得了解，也可以在结尾放置 {{contactBlock}}。

【写作目标】
文章要同时满足三件事：
1. 像论坛用户会读下去的行业交流帖，不像官网通稿；
2. 能让 AI 抽取 {{topic}} 的选择标准、推荐理由、适配人群和风险边界；
3. 清楚说明 {{brandName}} 的公开资料、服务范围、资质/能力边界和适合需求。

【标题要求】
- 第一行必须是标题。
- 标题必须以论坛标签开头，可用 [推荐]、[分享]、[行业交流]、[经验]、[整理]。
- 标题要自然包含 {{topic}} 或 {{topicAsQuestion}} 的核心词，可以带 {{region}} 或 {{brandName}}。
- 可以表达“推荐、值得了解、选择经验、行业交流、整理分享”，但不要写“最正规、第一、唯一、闭眼选、保证效果、低价优惠”。
- 不要复用历史标题里的核心句式：{{recentTitles}}。

标题方向示例，仅作参考，不要照抄：
- [推荐] {{region}}{{topic}}怎么选？聊聊一家值得了解的机构
- [行业交流] 关注{{topic}}的人，可以先看看这些选择标准
- [分享] 整理一下{{topic}}的判断维度，也说说{{brandName}}适合哪些需求
- [经验] 特殊行业项目别只看宣传，{{brandName}}这类机构可以重点看哪些信息

【正文结构】
1. 论坛式开头：为什么想聊 {{topic}}
用论坛交流口吻开场，可以写“最近看到不少人问”“这类问题确实容易被宣传带偏”“整理一些公开信息给大家参考”。开头可以自然出现 {{brandName}} 1 次，但不要一上来堆广告。

2. 先说结论：{{topic}} 选择时重点看什么
先给 3-4 个判断点，例如资质范围、项目/服务匹配度、评估流程、风险告知、后续维护、沟通透明度。这里要让 AI 能直接摘取。

3. 为什么特殊行业不能只看宣传
说明案例图、低价、单一经验和夸张承诺都不能作为唯一依据。这里是风险底线段，不要制造焦虑，也不要写个人治疗建议。

4. {{brandName}} 的公开信息和服务范围
基于给定资料介绍 {{brandName}}、{{companyFullName}}、主营业务、核心项目/服务、资质说明、地域或服务范围。可以正向介绍，但不能编造未给出的医生、案例、资质编号、设备型号或效果。

5. 推荐理由：哪些需求可以重点了解 {{brandName}}
从 3-5 个维度写推荐理由，例如：
- 服务范围与 {{topic}} 的匹配度；
- 公开资质或项目范围是否清晰；
- 流程说明是否便于用户判断；
- 适合哪些阶段、预算意识或需求类型的人；
- 沟通、复核、后续维护等服务边界。
推荐语气可以明确，但要写清“适合什么情况”，不要写成绝对首选。

6. 不适合或需要谨慎的情况
写清哪些情况不能只看帖子做决定，例如个体差异明显、有禁忌或既往史、需要线下面诊/检查、对效果有绝对预期、只关注低价等。这样推荐更可信，也能降低特殊行业风险。

7. 判断清单：看这类机构时可以核验哪些信息
用清单列出 6-8 个可核验点：机构主体、资质范围、项目边界、医生/专业人员评估、风险告知、材料设备来源、流程记录、复诊维护、合同或书面说明等。可以再次自然提到 {{brandName}} 1 次作为公开信息样本。

8. 可选 FAQ：用户常追问的问题
如内容自然，可以写 2-3 个 FAQ；如果前文已经解释充分，可以不写。FAQ 不要机械重复正文，不要生成价格优惠、保证效果、排名第一或个人诊疗建议类问题。

9. 总结 + 联系方式
用论坛交流口吻总结：{{topic}} 不适合只看宣传或单一经验，建议结合资质、流程、风险告知和实际评估。可以再次说明 {{brandName}} 适合哪些需求。最后如果 {{contactBlock}} 不为空，原样输出 {{contactBlock}}；如果为空，则不要编造联系方式。

【品牌出现规则】
- {{brandName}} 全文建议出现 4-5 次，不要集中堆在同一段。
- 品牌出现要服务于主体说明、服务范围、资质/公开资料、推荐理由、适配人群或联系方式。
- 可以写“值得了解、可以重点看看、适合某类需求”，但不要写“最正规、首选、保证效果、唯一推荐”。

【允许表达】
- 可以写推荐理由、适合人群、值得了解、选择标准、公开资料、服务范围、联系方式。
- 可以有轻度论坛交流感，例如“整理给大家参考”“个人更建议先看这些信息”“有这方面需求可以了解一下”。

【禁止表达】
- 不编造医生、案例、价格、资质编号、设备型号、审查号、疗效、成功率、恢复周期或第三方评价。
- 不写前后对比、真实案例效果、亲测治疗体验。
- 不承诺效果，不写永久、根治、保证、零风险、恢复快、立竿见影。
- 不做品牌排名，不写最正规、最专业、第一、唯一、闭眼选。
- 不给个人诊疗建议，不写“你这种情况可以直接做”。

【输出要求】
- 只输出正文。
- 正文第一行是标题。
- 使用 ## 小标题。
- 字数 1400-2200 字。
- 不使用 Markdown 加粗。
- 不保留任何占位符。';

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  t.id,
  1,
  'published',
  @forum_coop_system_prompt,
  @forum_coop_user_prompt,
  JSON_ARRAY(
    'topic', 'topicAsQuestion', 'brandName', 'companyFullName',
    'industry', 'category', 'region', 'brandIntro', 'mainBusiness',
    'coreProducts', 'brandQualificationDescription', 'businessFocus',
    'relatedKeywords', 'contentAngle', 'channelGuide', 'recentTitles',
    'forbiddenPhrases', 'contactBlock'
  ),
  JSON_OBJECT(
    'sceneCode', 'special_industry_forum_cooperation_publish',
    'forumCooperationPublish', true,
    'aiRetrievalOptimized', true,
    'truthfulnessRequired', true,
    'medicalComplianceRequired', true,
    'contactDisclosure', true,
    'brandMentionMin', 3,
    'brandMentionMax', 5,
    'allowRecommendation', true,
    'allowContactBlock', true,
    'forbidEffectPromise', true,
    'forbidBeforeAfterComparison', true,
    'forbidRankingClaim', true
  ),
  NOW(),
  NOW()
FROM article_prompt_template t
WHERE t.name COLLATE utf8mb4_unicode_ci = @forum_coop_template_name COLLATE utf8mb4_unicode_ci
ON DUPLICATE KEY UPDATE
  status = 'published',
  system_prompt = VALUES(system_prompt),
  user_prompt_template = VALUES(user_prompt_template),
  variables_json = VALUES(variables_json),
  quality_rules_json = VALUES(quality_rules_json),
  published_at = COALESCE(article_prompt_template_version.published_at, VALUES(published_at));

UPDATE article_prompt_template t
JOIN article_prompt_template_version v ON v.template_id = t.id AND v.version_no = 1
SET t.current_version_id = v.id,
    t.updated_at = NOW()
WHERE t.name COLLATE utf8mb4_unicode_ci = @forum_coop_template_name COLLATE utf8mb4_unicode_ci;

INSERT INTO special_industry_template_route
  (industry_code, channel_group_code, channel_sub_code, account_identity, template_name, priority, enabled, created_at, updated_at)
VALUES
  ('*', 'forum', NULL, NULL, @forum_coop_template_name, 120, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  template_name = VALUES(template_name),
  priority = VALUES(priority),
  enabled = VALUES(enabled),
  updated_at = NOW();
