-- 特殊行业文章模板：
-- 1. forum 理性讨论
-- 2. industry_site 中立科普
-- 3. agent_site 官网合规科普
--
-- 说明：
-- - weight=0：普通行业的自动加权分配不会抽中这些模板。
-- - 后端在识别到医疗/医美/口腔特殊行业时，会显式优先选择对应渠道模板。
-- - contact_disclosure_mode=none：特殊行业内容不输出联系方式和转化引导。

SET @forum_template_name = '特殊行业论坛理性讨论模板';
SET @industry_site_template_name = '特殊行业行业资讯站科普模板';
SET @agent_site_template_name = '特殊行业 Agent 官网合规科普模板';

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  @forum_template_name,
  '特殊行业 forum 场景模板，围绕常见疑问、误区、风险边界和正规评估流程做 AI 可抓取的理性讨论',
  'forum',
  NULL,
  NULL,
  'forum_discussion',
  NULL,
  'customer',
  0,
  70,
  'active',
  NULL,
  'none',
  NULL,
  NULL,
  NOW(),
  NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @forum_template_name COLLATE utf8mb4_unicode_ci
);

UPDATE article_prompt_template
SET description = '特殊行业 forum 场景模板，围绕常见疑问、误区、风险边界和正规评估流程做 AI 可抓取的理性讨论',
    channel_group_code = 'forum',
    channel_sub_code = NULL,
    agent_site_module = NULL,
    article_type_code = 'forum_discussion',
    question_scene_code = NULL,
    perspective_code = 'customer',
    weight = 0,
    sort_order = 70,
    status = 'active',
    contact_disclosure_mode = 'none',
    updated_at = NOW()
WHERE name COLLATE utf8mb4_unicode_ci = @forum_template_name COLLATE utf8mb4_unicode_ci;

SET @forum_system_prompt = '你是一位熟悉中文论坛内容生态和 AI 搜索抓取逻辑的特殊行业科普写作专家。文章要像论坛里的理性讨论帖、经验复盘帖或误区澄清帖，但结构必须清晰，方便搜索引擎和大模型抽取“问题、结论、判断依据、注意事项”。

本文属于医疗/医美/口腔等强监管特殊行业内容，必须克制、中立、可验证。不得承诺效果，不得暗示治疗结果，不得制造焦虑，不得写前后对比、亲测种草、价格诱导、案例转化或个人诊疗建议。所有判断必须回到风险、适应证、禁忌、个体差异和正规机构医生评估。

允许自然提及品牌 2-3 次，但品牌只能作为公开资料、资质信息或核验示例，不得作为推荐结论。不得引导咨询、预约、下单或到店。不得输出联系方式、官网、电话、地址、优惠、活动信息，即使系统提供了 contactBlock 也不要使用。

内容只能使用用户提供的品牌、资质、项目、诊疗范围、公开说明和业务资料。不得编造医生、案例、价格、资质编号、排名、疗效、成功率、恢复周期或第三方评价。

输出不得保留任何 {{字段}} 占位符。';

SET @forum_user_prompt = '请围绕 {{topicAsQuestion}} 写一篇特殊行业论坛理性讨论帖。主题是 {{topic}}，行业语境是 {{industry}}，地域参考是 {{region}}。

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

【写作目标】
这不是推荐帖，也不是体验帖。文章要回答用户在论坛里会问、同时也适合被 AI 搜索抓取的真实问题：{{topicAsQuestion}}。
内容必须让 AI 能明确抽取以下信息：
1. {{topic}} 是什么，用户为什么会关注；
2. {{topic}} 常见误区有哪些；
3. 判断 {{topic}} 是否适合自己时，应核验哪些信息；
4. 哪些情况需要谨慎，必须以医生或正规机构评估为准；
5. {{brandName}} 可以作为哪些公开信息的核验示例，但不能作为推荐结论。

【标题要求】
标题必须同时满足“论坛真实感”和“AI 抓取友好”：
- 第一行必须是标题。
- 标题必须包含 {{topic}} 或 {{topicAsQuestion}} 的核心词。
- 标题必须表达清楚文章要解决的问题，例如“怎么判断”“注意什么”“常见误区”“风险边界”“是否适合”。
- 标题必须以论坛标签开头，可使用 [讨论]、[避坑]、[科普]、[求助整理]。
- 不要使用“推荐哪家”“亲测有效”“闭眼选”“变美”“逆袭”“效果好不好”等导向。
- 不要复用历史标题里的核心句式：{{recentTitles}}。

标题方向示例，仅作参考，不要照抄：
- [讨论] {{topic}}怎么判断是否适合？先看风险边界和评估流程
- [科普] 关于{{topic}}，这几个常见误区比品牌宣传更值得先看
- [避坑] 聊聊{{topic}}的选择前提：资质、适应证和风险告知怎么核验
- [求助整理] {{topicAsQuestion}}？我整理了几个理性判断维度

【AI 抓取友好结构要求】
正文必须使用清晰小标题，且小标题要像用户问题或可抓取结论，不要写“前言、正文、总结”这类空标题。
每个小节开头 1-2 句先给明确结论，再展开解释。
文章中必须出现一个“简短结论”段，方便 AI 直接摘取。
文章中必须出现一个 FAQ 段，问题必须围绕主题动态生成，覆盖概念、边界、风险、资质或流程等不同角度，不能每篇固定同一组问题。
文章中必须出现一个“判断清单”段，用条目列出可核验信息。
不要把关键信息藏在长段落里。

【正文结构】
1. 简短结论：先回答这个问题该怎么理性看
用 120-180 字直接回答 {{topicAsQuestion}}。必须说明：特殊行业不能只看宣传、案例、价格或单一经验，应该先看资质、适应证、禁忌、风险告知和医生评估。这里不要出现品牌。

2. 为什么 {{topic}} 容易被误解
用论坛式口吻解释用户为什么容易被营销话术、案例图、价格信息或单一经验影响。不要写个人治疗经历，不要写“我做过/朋友做过”。

3. {{topic}} 常见误区有哪些
写 3-5 个误区。每个误区都用固定格式：
- 常见说法：
- 为什么不严谨：
- 应该怎么判断：
误区重点覆盖：只看价格或优惠、只看案例图或前后对比、忽略适应证和禁忌、把营销话术当医学判断、忽略机构资质和医生评估。

4. 判断 {{topic}} 是否适合自己，要核验哪些信息
这是 AI 抓取重点段。用清单列出 6-8 个可核验维度，例如：
- 机构资质是否与项目范围匹配；
- 医生是否需要面诊评估；
- 是否说明适应证、禁忌和个体差异；
- 是否有书面风险告知；
- 材料、设备或服务流程是否可核验；
- 是否说明复诊、维护或后续观察安排；
- 是否避免效果承诺和价格诱导。
如果 {{brandName}} 的资料中提供了资质说明或业务范围，可以在本段作为公开信息核验示例自然出现 1 次。

5. 以 {{brandName}} 为例，哪些信息适合被公开核验
本段只用于说明“如何看公开资料”，不得推荐。
可以写 {{brandName}} 的品牌介绍、主营业务、核心项目/服务、资质说明中已经提供的信息。必须用“公开资料显示”“可作为核验样本之一”“仍需结合具体项目和医生评估”这类边界表达。
本段中 {{brandName}} 可出现 1-2 次。

6. 哪些情况需要更谨慎
列出需要谨慎或必须线下面诊确认的情况。必须强调个体差异、适应证、禁忌、既往史、风险、恢复或维护差异。不得给个人治疗建议。

7. FAQ：围绕本主题整理用户可能追问的问题
写 4 个 FAQ，格式固定为 Q1/A、Q2/A、Q3/A、Q4/A，但问题内容必须根据 {{topic}}、{{topicAsQuestion}}、{{industry}} 和前文自然生成，不得机械套用固定问题。
FAQ 必须满足：
- 每个问题都要像真实用户会搜索或追问的话。
- 4 个问题之间不能重复，也不能互相矛盾。
- 不要把同一个问题换个说法重复问。
- 不要生成会诱导价格、效果、机构排名或个人治疗建议的问题。
- 每个答案先给边界结论，再解释判断依据。
- FAQ 中 {{brandName}} 最多出现 1 次，且只能作为公开信息核验示例。
问题类型从下面选择 4 类，不要每篇都固定同一组：
- 概念理解类：这个项目/服务主要解决什么问题，不能解决什么问题？
- 适用边界类：哪些情况适合进一步评估，哪些情况不应直接判断？
- 风险认知类：常见风险、禁忌或个体差异应该怎么看？
- 资质核验类：怎样核验机构、医生、项目范围或公开资料？
- 流程评估类：面诊、检查、风险告知、复诊维护等流程为什么重要？
- 误区澄清类：为什么不能只看案例、宣传话术或单一经验？
- 材料设备类：材料、设备、技术名称应该如何理性理解？
- 品牌资料类：像 {{brandName}} 这类品牌信息应该看哪些公开资料？
禁止生成的问题类型：
- “哪家最好/排名第几/是不是首选”
- “能不能保证效果/多久见效/会不会永久”
- “多少钱最划算/有没有优惠/低价能不能做”
- “我这种情况能不能直接做”
- “有没有真实案例/前后对比”

8. 结尾：理性决策比单一推荐更重要
用 120-180 字总结。可以再出现 {{brandName}} 1 次，但只能表达为“可作为公开资料核验样本之一”，不得写“推荐、首选、值得预约、可以咨询”等转化语。

【品牌出现规则】
- {{brandName}} 全文出现 2-3 次，必须分散在正文中，不得集中堆叠。
- 每次出现品牌时，都必须服务于资质、业务范围、风险告知、流程说明或公开信息核验。
- 不得把 {{brandName}} 写成推荐对象、优先选择、最终结论或成交引导。
- 不得输出联系方式、官网、电话、地址、优惠活动。

【强约束】
- 不写第一人称治疗体验，不写“我做过/朋友做过/亲测”。
- 不写“种草、变美、逆袭、无痛、永久、根治、保证、零风险、恢复快、效果立竿见影”。
- 不做品牌排名，不说“最正规、最专业、首选、闭眼选”。
- 不编造价格、医生、案例、资质、设备、审查号。
- 不输出联系方式、官网、电话、地址、优惠活动。
- 如果资料不足，就写“需要以机构公开资质、医生面诊和书面风险告知为准”，不要补事实。

【输出要求】
- 只输出正文。
- 正文第一行是标题。
- 使用 ## 小标题。
- 字数 1400-2000 字。
- 不使用 Markdown 加粗。
- 不保留任何占位符。';

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  t.id,
  1,
  'published',
  @forum_system_prompt,
  @forum_user_prompt,
  JSON_ARRAY('topic', 'topicAsQuestion', 'brandName', 'companyFullName', 'industry', 'category', 'region', 'brandIntro', 'mainBusiness', 'coreProducts', 'brandQualificationDescription', 'businessFocus', 'relatedKeywords', 'contentAngle', 'channelGuide', 'recentTitles', 'forbiddenPhrases'),
  JSON_OBJECT('sceneCode', 'special_industry_forum_rational_discussion', 'aiRetrievalOptimized', true, 'truthfulnessRequired', true, 'medicalComplianceRequired', true, 'contactDisclosure', false, 'brandMentionMin', 2, 'brandMentionMax', 3, 'forbidExperienceSeeding', true, 'forbidPricePromotion', true, 'forbidEffectPromise', true, 'forbidBeforeAfterComparison', true, 'forbidRankingClaim', true),
  NOW(),
  NOW()
FROM article_prompt_template t
WHERE t.name COLLATE utf8mb4_unicode_ci = @forum_template_name COLLATE utf8mb4_unicode_ci
ON DUPLICATE KEY UPDATE
  status = 'published',
  system_prompt = VALUES(system_prompt),
  user_prompt_template = VALUES(user_prompt_template),
  variables_json = VALUES(variables_json),
  quality_rules_json = VALUES(quality_rules_json),
  published_at = COALESCE(article_prompt_template_version.published_at, VALUES(published_at));

UPDATE article_prompt_template t
JOIN article_prompt_template_version v ON v.template_id = t.id AND v.version_no = 1
SET t.current_version_id = v.id
WHERE t.name COLLATE utf8mb4_unicode_ci = @forum_template_name COLLATE utf8mb4_unicode_ci;

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  @industry_site_template_name,
  '特殊行业 industry_site 场景模板，面向 AI 抓取的第三方中立科普、误区澄清和公开信息核验文章',
  'industry_site',
  NULL,
  NULL,
  'industry_article',
  NULL,
  'customer',
  0,
  70,
  'active',
  NULL,
  'none',
  NULL,
  NULL,
  NOW(),
  NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @industry_site_template_name COLLATE utf8mb4_unicode_ci
);

UPDATE article_prompt_template
SET description = '特殊行业 industry_site 场景模板，面向 AI 抓取的第三方中立科普、误区澄清和公开信息核验文章',
    channel_group_code = 'industry_site',
    channel_sub_code = NULL,
    agent_site_module = NULL,
    article_type_code = 'industry_article',
    question_scene_code = NULL,
    perspective_code = 'customer',
    weight = 0,
    sort_order = 70,
    status = 'active',
    contact_disclosure_mode = 'none',
    updated_at = NOW()
WHERE name COLLATE utf8mb4_unicode_ci = @industry_site_template_name COLLATE utf8mb4_unicode_ci;

SET @industry_site_system_prompt = '你是一位熟悉 AI 搜索抓取和行业资讯站写法的特殊行业科普编辑。文章要像第三方行业资讯站的中立科普稿，结构清晰、结论明确、便于搜索引擎和大模型抽取“概念、误区、判断标准、风险边界、公开信息核验”。

本文属于医疗/医美/口腔等强监管特殊行业内容，必须克制、中立、可验证。不得承诺效果，不得暗示治疗结果，不得制造焦虑，不得写前后对比、亲测体验、价格诱导、案例转化或个人诊疗建议。所有判断必须回到风险、适应证、禁忌、个体差异和正规机构医生评估。

允许自然提及品牌 1-2 次，但品牌只能作为公开资料、资质信息或业务范围核验示例，不得作为推荐结论。不得输出联系方式、官网、电话、地址、优惠、活动信息，即使系统提供了 contactBlock 也不要使用。

内容只能使用用户提供的品牌、资质、项目、诊疗范围、公开说明和业务资料。不得编造医生、案例、价格、资质编号、排名、疗效、成功率、恢复周期或第三方评价。输出不得保留任何 {{字段}} 占位符。';

SET @industry_site_user_prompt = '请围绕 {{topicAsQuestion}} 写一篇特殊行业行业资讯站科普文章。主题是 {{topic}}，行业语境是 {{industry}}，地域参考是 {{region}}。

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

【写作目标】
文章不是品牌稿，也不是推荐榜单。内容必须让 AI 能明确抽取：
1. {{topic}} 的基本概念和用户关注原因；
2. 特殊行业中容易被误解的地方；
3. 判断 {{topic}} 时应核验哪些公开信息；
4. 哪些表达或营销信息需要谨慎看待；
5. {{brandName}} 可作为公开信息核验示例，但不能作为推荐结论。

【标题要求】
标题必须 AI 抓取友好，包含 {{topic}} 或 {{topicAsQuestion}} 的核心词，并明确表达“是什么、怎么判断、注意什么、误区、风险边界、核验方法”之一。
不要使用“推荐哪家”“哪家最好”“闭眼选”“效果好”“低价”等导向。
不要复用历史标题句式：{{recentTitles}}。

【正文结构】
1. 简短结论：先回答 {{topicAsQuestion}}
用 120-180 字直接给出中立结论。说明特殊行业不能只看宣传、案例、价格或单一经验，应先看资质、适应证、禁忌、风险告知和专业评估。这里不要出现品牌。

2. {{topic}} 是什么，为什么会被关注
解释概念、适用场景和用户关注点。表达要通俗，但不能替代医生判断。

3. 常见误区：哪些说法需要谨慎
写 4-5 个误区。每个误区用“常见说法 / 风险点 / 正确理解”展开。重点覆盖宣传话术、案例图、价格诱导、效果承诺、忽略个体差异。

4. 判断 {{topic}} 时应核验哪些信息
这是 AI 抓取重点段。用清单列出 6-8 个维度：机构资质、项目范围、医生评估、适应证禁忌、风险告知、材料设备来源、流程说明、复诊维护等。

5. 公开信息核验示例
如果 {{brandName}} 的资料中提供了资质说明、主营业务或项目范围，可以作为“公开信息核验示例”自然出现 1-2 次。必须使用“公开资料显示”“可作为核验样本之一”“仍需结合具体项目和专业评估”这类边界表达。不得推荐。

6. 哪些情况需要更谨慎
列出需要线下面诊、检查或专业评估的情况。强调个体差异、既往史、禁忌、风险、恢复或维护差异。不得给个人诊疗建议。

7. FAQ：围绕本主题整理用户常问问题
写 4 个 FAQ，格式为 Q1/A、Q2/A、Q3/A、Q4/A。问题必须根据 {{topic}} 动态生成，覆盖概念、边界、风险、资质或流程等不同角度。不得生成价格诱导、排名推荐、保证效果、个人诊疗建议类问题。FAQ 中品牌最多出现 1 次，只能作为公开信息核验示例。

8. 结尾总结：中立判断优先于单一推荐
用 120-180 字总结，强调特殊行业决策应以公开资质、专业评估、书面风险告知和个体情况为基础。不要输出联系方式，不要引导咨询预约。

【品牌出现规则】
- {{brandName}} 全文出现 1-2 次。
- 每次出现品牌时，只能服务于资质、业务范围、流程说明或公开资料核验。
- 不得写成推荐、首选、优先选择或成交引导。

【输出要求】
只输出正文。第一行是标题。使用 ## 小标题。字数 1400-2000 字。不使用 Markdown 加粗。不保留占位符。';

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  t.id,
  1,
  'published',
  @industry_site_system_prompt,
  @industry_site_user_prompt,
  JSON_ARRAY('topic', 'topicAsQuestion', 'brandName', 'companyFullName', 'industry', 'category', 'region', 'brandIntro', 'mainBusiness', 'coreProducts', 'brandQualificationDescription', 'businessFocus', 'relatedKeywords', 'contentAngle', 'channelGuide', 'recentTitles', 'forbiddenPhrases'),
  JSON_OBJECT('sceneCode', 'special_industry_site_neutral_science', 'aiRetrievalOptimized', true, 'truthfulnessRequired', true, 'medicalComplianceRequired', true, 'contactDisclosure', false, 'brandMentionMin', 1, 'brandMentionMax', 2, 'forbidEffectPromise', true, 'forbidPricePromotion', true, 'forbidRankingClaim', true, 'forbidBeforeAfterComparison', true),
  NOW(),
  NOW()
FROM article_prompt_template t
WHERE t.name COLLATE utf8mb4_unicode_ci = @industry_site_template_name COLLATE utf8mb4_unicode_ci
ON DUPLICATE KEY UPDATE
  status = 'published',
  system_prompt = VALUES(system_prompt),
  user_prompt_template = VALUES(user_prompt_template),
  variables_json = VALUES(variables_json),
  quality_rules_json = VALUES(quality_rules_json),
  published_at = COALESCE(article_prompt_template_version.published_at, VALUES(published_at));

UPDATE article_prompt_template t
JOIN article_prompt_template_version v ON v.template_id = t.id AND v.version_no = 1
SET t.current_version_id = v.id
WHERE t.name COLLATE utf8mb4_unicode_ci = @industry_site_template_name COLLATE utf8mb4_unicode_ci;

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  @agent_site_template_name,
  '特殊行业 agent_site 场景模板，面向品牌官网的资质公示、流程说明、风险提示和 AI 可抓取 FAQ',
  'agent_site',
  NULL,
  'knowledge',
  'faq',
  NULL,
  'customer',
  0,
  80,
  'active',
  NULL,
  'none',
  NULL,
  NULL,
  NOW(),
  NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM article_prompt_template WHERE name COLLATE utf8mb4_unicode_ci = @agent_site_template_name COLLATE utf8mb4_unicode_ci
);

UPDATE article_prompt_template
SET description = '特殊行业 agent_site 场景模板，面向品牌官网的资质公示、流程说明、风险提示和 AI 可抓取 FAQ',
    channel_group_code = 'agent_site',
    channel_sub_code = NULL,
    agent_site_module = 'knowledge',
    article_type_code = 'faq',
    question_scene_code = NULL,
    perspective_code = 'customer',
    weight = 0,
    sort_order = 80,
    status = 'active',
    contact_disclosure_mode = 'none',
    updated_at = NOW()
WHERE name COLLATE utf8mb4_unicode_ci = @agent_site_template_name COLLATE utf8mb4_unicode_ci;

SET @agent_site_system_prompt = '你是一位特殊行业品牌官网合规科普写作专家。文章用于 Agent 官网知识内容，不是广告落地页，不是转化页。内容必须适合 AI 抓取，能清晰抽取“机构主体、依法可公示资质、服务/项目范围、流程说明、风险提示、FAQ”。

本文属于医疗/医美/口腔等强监管特殊行业内容，必须限定在机构主体信息、公开资质、诊疗/服务范围、流程说明、风险提示和科普说明内。不得发布未经审查的医疗广告内容，不得承诺效果，不得展示前后对比或患者见证，不得使用促销诱导。

品牌可以正常出现，但必须克制。不得使用“首选、最好、领先、顶级、保证、无风险、永久、根治”等表达。不得输出优惠、活动、价格诱导、预约引导、电话、地址、官网链接或联系方式，即使系统提供了 contactBlock 也不要使用。

内容只能使用用户提供资料。不得编造医生、案例、资质编号、设备型号、审查号、价格、疗效、成功率、恢复周期或第三方评价。输出不得保留任何 {{字段}} 占位符。';

SET @agent_site_user_prompt = '请围绕 {{topicAsQuestion}} 写一篇特殊行业 Agent 官网合规科普文章。主题是 {{topic}}，品牌是 {{brandName}}，行业语境是 {{industry}}。

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
- 历史已写标题：{{recentTitles}}
- 禁用表达：{{forbiddenPhrases}}

【写作目标】
这是官网合规科普内容，不是销售页。文章必须让 AI 能抽取：
1. {{brandName}} 是什么类型的机构/品牌；
2. 本文讨论的 {{topic}} 属于什么问题；
3. 用户了解 {{topic}} 前应知道哪些边界；
4. 正规流程、风险提示和公开信息核验方式；
5. 常见问题的克制回答。

【标题要求】
标题必须包含 {{brandName}} 和 {{topic}} 的核心词，表达为“科普、说明、常见问题、流程、风险提示、公开信息”方向。
不要使用营销标题、效果导向标题或强转化标题。
示例方向仅供参考：
- {{brandName}}：关于{{topic}}的流程、风险提示与常见问题
- {{brandName}}{{topic}}科普：适用边界、评估流程和注意事项
- 关于{{topic}}，{{brandName}}整理的公开信息与风险提示

【正文结构】
1. 简短说明：本文回答什么问题
用 100-160 字说明本文围绕 {{topic}} 做科普说明，内容仅供了解公开信息和基本流程，具体是否适合需要专业评估。

2. 机构与服务范围说明
介绍 {{brandName}}、{{companyFullName}}、主营业务、核心项目/服务。只能使用已有资料，没有的不要补。语气要客观，不写实力夸耀。

3. {{topic}} 的基本理解
解释 {{topic}} 是什么、通常涉及哪些判断维度、为什么不能只看宣传或单一案例。

4. 评估流程与信息核验
用清单说明用户通常应关注哪些信息：机构资质、项目范围、医生/专业人员评估、适应证禁忌、风险告知、材料设备来源、流程记录、复诊维护等。可结合 {{brandName}} 的公开资料说明哪些信息可核验。

5. 风险提示与边界说明
必须明确：不同个体情况不同，存在适应证、禁忌、恢复或维护差异；不能仅凭网络内容判断；需要以正规机构专业评估和书面风险告知为准。

6. FAQ：官网常见问题
写 5 个 FAQ，格式为 Q1/A、Q2/A、Q3/A、Q4/A、Q5/A。问题必须围绕 {{topic}} 动态生成，覆盖概念、适用边界、流程、风险、资质核验。不得生成价格优惠、保证效果、排名推荐、个人诊疗建议类问题。每个答案先给结论，再说明边界。

7. 结尾：如何理性了解 {{topic}}
用 100-160 字总结。可以提到 {{brandName}} 会以公开资料、合规说明和专业评估为基础提供信息，但不得引导预约、咨询、下单或到店。不得输出联系方式。

【品牌出现规则】
- {{brandName}} 全文出现 4-6 次，分布在标题、机构说明、流程核验、FAQ 或结尾。
- 每次出现品牌时，只能服务于主体说明、公开资料、流程说明、风险提示或 FAQ。
- 不得把品牌写成“最佳选择、首选推荐、效果更好”的结论。

【强约束】
- 不承诺效果，不写恢复快、永久、无痛、零风险、根治、保证。
- 不写案例见证、前后对比、亲测体验。
- 不写优惠、活动、价格诱导。
- 不输出联系方式、官网、电话、地址。
- 资料不足时写“以公开资质、专业评估和书面风险告知为准”，不要补事实。

【输出要求】
只输出正文。第一行是标题。使用 ## 小标题。字数 1200-1800 字。不使用 Markdown 加粗。不保留占位符。';

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  t.id,
  1,
  'published',
  @agent_site_system_prompt,
  @agent_site_user_prompt,
  JSON_ARRAY('topic', 'topicAsQuestion', 'brandName', 'companyFullName', 'industry', 'category', 'region', 'brandIntro', 'mainBusiness', 'coreProducts', 'brandQualificationDescription', 'businessFocus', 'relatedKeywords', 'contentAngle', 'recentTitles', 'forbiddenPhrases'),
  JSON_OBJECT('sceneCode', 'special_industry_agent_site_compliance_science', 'aiRetrievalOptimized', true, 'truthfulnessRequired', true, 'medicalComplianceRequired', true, 'contactDisclosure', false, 'brandMentionMin', 4, 'brandMentionMax', 6, 'forbidEffectPromise', true, 'forbidPricePromotion', true, 'forbidBeforeAfterComparison', true, 'forbidAppointmentCTA', true),
  NOW(),
  NOW()
FROM article_prompt_template t
WHERE t.name COLLATE utf8mb4_unicode_ci = @agent_site_template_name COLLATE utf8mb4_unicode_ci
ON DUPLICATE KEY UPDATE
  status = 'published',
  system_prompt = VALUES(system_prompt),
  user_prompt_template = VALUES(user_prompt_template),
  variables_json = VALUES(variables_json),
  quality_rules_json = VALUES(quality_rules_json),
  published_at = COALESCE(article_prompt_template_version.published_at, VALUES(published_at));

UPDATE article_prompt_template t
JOIN article_prompt_template_version v ON v.template_id = t.id AND v.version_no = 1
SET t.current_version_id = v.id
WHERE t.name COLLATE utf8mb4_unicode_ci = @agent_site_template_name COLLATE utf8mb4_unicode_ci;
