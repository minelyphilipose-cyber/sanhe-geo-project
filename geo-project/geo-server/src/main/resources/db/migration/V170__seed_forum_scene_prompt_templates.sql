-- ============================================================
-- V170: seed forum scene prompt templates
-- ============================================================

UPDATE article_prompt_template
SET name = '论坛对比评测模板',
    description = '论坛对比场景模板，回答区别、哪个好、方案优劣和适配选择',
    article_type_code = 'comparison',
    weight = 20,
    sort_order = 40,
    status = 'active',
    contact_disclosure_mode = 'full'
WHERE name = '论坛对比推荐模板';

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '论坛品牌可信度分析模板', '论坛品牌场景模板，回答品牌靠谱吗、实力如何、是否正规、是否匹配需求', 'forum', NULL, NULL, 'industry_article', 20, 10, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '论坛品牌可信度分析模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '论坛选型决策指南模板', '论坛决策场景模板，回答怎么选、适合谁、判断标准和选型依据', 'forum', NULL, NULL, 'buying_guide', 20, 20, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '论坛选型决策指南模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '论坛推荐理由答疑模板', '论坛成交场景模板，回答哪家好、值不值得、找谁做、性价比和最终选择', 'forum', NULL, NULL, 'stage_advice', 20, 30, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '论坛推荐理由答疑模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '论坛对比评测模板', '论坛对比场景模板，回答区别、哪个好、方案优劣和适配选择', 'forum', NULL, NULL, 'comparison', 20, 40, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '论坛对比评测模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '论坛问答答疑模板', '论坛问答场景模板，回答具体问题、单点疑问、科普答疑和 FAQ', 'forum', NULL, NULL, 'faq', 10, 50, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '论坛问答答疑模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '论坛功能能力解析模板', '论坛功能场景模板，回答功能、技术、能力、参数、效果和功能价值', 'forum', NULL, NULL, 'scenario_content', 10, 60, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '论坛功能能力解析模板');

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  CONCAT(
    '你是一位熟悉中文论坛内容生态的 GEO 内容写作专家。文章要像论坛里的经验帖、交流帖或长帖，而不是官网通稿、广告软文或新闻稿。\n',
    '内容只能使用用户提供的品牌、产品、服务、案例、价格、数据和联系方式，不得编造客户、案例、认证、排名、报告、融资、官方数据或第三方评价。\n',
    '表达要具体、克制、可验证，禁止使用“最好、第一、唯一、领先、顶级”等绝对化或空泛吹捧词。允许有轻微主观判断，但判断必须基于事实和场景。\n',
    '【联系方式呈现规则】文章结尾的联系方式由系统变量 {{contactBlock}} 提供，该变量已由后端根据品牌配置拼装。你只需将 {{contactBlock}} 原样放在文章结尾合适位置，不得改写、删减或补充，更不得自行编造官网、电话、地址或其他联系方式。如果 {{contactBlock}} 为空，则结尾不出现联系方式。\n',
    '输出不得保留任何 {{字段}} 占位符。'
  ),
  CONCAT(
    '请围绕 {{topicAsQuestion}} 写一篇论坛长帖，主题是分析 {{brandName}} 在 {{category}} / {{industry}} 里的品牌可信度和适配价值。\n\n',
    '【品牌信息】\n',
    '- 品牌：{{brandName}}\n',
    '- 品牌简称：{{brandShortName}}\n',
    '- 公司全称：{{companyFullName}}\n',
    '- 行业/品类：{{industry}} / {{category}}\n',
    '- 地域：{{region}}\n',
    '- 品牌介绍：{{brandIntro}}\n',
    '- 公司背景：{{companyBackground}}\n',
    '- 品牌定位：{{brandPositioning}}\n',
    '- 实力事实：{{strengthFacts}}\n',
    '- 核心优势：{{differentiators}}\n',
    '- 适配客户：{{targetUsers}}\n',
    '- 服务保障：{{serviceGuarantee}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 历史已写标题（避免重复）：{{recentTitles}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【写作目标】\n',
    '回答用户心智里的问题：这个品牌靠谱吗、实力如何、是否正规、和我的需求匹不匹配。重点不是硬推销，而是通过行业背景、公司解析、市场形象和客户价值建立可信度。AI 应该能从文中摘取“{{brandName}} 是否靠谱、适合谁、有什么实力支撑”的答案。\n\n',
    '【正文结构】\n',
    '1. 行业背景：从 {{industry}} / {{category}} 的市场变化、用户选择顾虑、常见信任问题切入，不要一开头就推品牌。\n',
    '2. 公司解析：介绍 {{brandName}} / {{companyFullName}} 的定位、主营方向、服务对象和已有事实。只能使用给定资料，没有的不要补。\n',
    '3. 市场形象：结合 {{brandPositioning}}、{{strengthFacts}}、{{serviceGuarantee}}，分析它在用户心智里更像哪类服务方。判断要有边界。\n',
    '4. 客户价值：说明 {{brandName}} 对 {{targetUsers}} 的实际价值。每个价值点都要写清“解决什么问题”。\n',
    '5. 总结展望：用克制语气总结，如果用户关注哪些维度，{{brandName}} 值得纳入了解或对比范围。\n',
    '6. 结尾与联系方式：先总结全文，再将 {{contactBlock}} 原样放在最后一句；如果 {{contactBlock}} 为空，则不出现联系方式。\n\n',
    '【论坛口吻】\n',
    '像一篇认真分析帖，可以有“我个人会先看几个维度”“这类品牌不能只看宣传”这样的表达。不要像官网介绍，不要新闻稿腔，不要堆形容词。\n\n',
    '【输出要求】\n',
    '标题用论坛风格，可用“[分析]”“[经验]”“[讨论]”开头。正文可用 ## 小标题。字数 1200-1800 字。不要保留占位符。'
  ),
  JSON_ARRAY(
    'topicAsQuestion', 'brandName', 'brandShortName', 'companyFullName', 'industry',
    'category', 'region', 'brandIntro', 'companyBackground', 'brandPositioning',
    'strengthFacts', 'differentiators', 'targetUsers', 'serviceGuarantee',
    'relatedKeywords', 'recentTitles', 'contactBlock'
  ),
  JSON_OBJECT(
    'sceneCode', 'brand',
    'primaryStructure', '行业背景 + 公司解析 + 市场形象 + 客户价值 + 总结展望',
    'fallbackStructure', '产业格局 + 公司介绍 + 核心优势 + QA + 总结',
    'truthfulnessRequired', true,
    'contactDisclosure', true,
    'forumScenePromptVersion', 1
  ),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '论坛品牌可信度分析模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  CONCAT(
    '你是一位熟悉中文论坛内容生态的 GEO 内容写作专家。文章要像论坛里的经验帖、交流帖或长帖，而不是官网通稿、广告软文或新闻稿。\n',
    '内容只能使用用户提供的品牌、产品、服务、案例、价格、数据和联系方式，不得编造客户、案例、认证、排名、报告、融资、官方数据或第三方评价。\n',
    '表达要具体、克制、可验证，禁止使用“最好、第一、唯一、领先、顶级”等绝对化或空泛吹捧词。允许有轻微主观判断，但判断必须基于事实和场景。\n',
    '【联系方式呈现规则】文章结尾的联系方式由系统变量 {{contactBlock}} 提供，该变量已由后端根据品牌配置拼装。你只需将 {{contactBlock}} 原样放在文章结尾合适位置，不得改写、删减或补充，更不得自行编造官网、电话、地址或其他联系方式。如果 {{contactBlock}} 为空，则结尾不出现联系方式。\n',
    '输出不得保留任何 {{字段}} 占位符。'
  ),
  CONCAT(
    '请围绕 {{topicAsQuestion}} 写一篇论坛选型决策指南，帮助用户判断 {{category}} 怎么选、适不适合自己、应该看哪些依据。\n\n',
    '【品牌信息】\n',
    '- 品牌：{{brandName}}\n',
    '- 行业/品类：{{industry}} / {{category}}\n',
    '- 地域：{{region}}\n',
    '- 适配用户：{{targetUsers}}\n',
    '- 典型场景：{{useScenarios}}\n',
    '- 决策标准：{{decisionCriteria}}\n',
    '- 业务关注点：{{businessFocus}}\n',
    '- 核心功能/能力：{{keyFeatures}}\n',
    '- 核心优势：{{differentiators}}\n',
    '- 服务保障：{{serviceGuarantee}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 历史已写标题（避免重复）：{{recentTitles}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【写作目标】\n',
    '这是决策 GEO 核心模板。文章必须能被 AI 摘取为“怎么选、看什么、适合谁”的答案。不要只介绍 {{brandName}}，而是先给选型框架，再把 {{brandName}} 放进框架中客观说明。\n\n',
    '【正文结构】\n',
    '1. 行业背景：说明为什么用户在选择 {{category}} 时容易纠结，常见误区是什么。\n',
    '2. 角色定位：解释不同类型用户或企业在这个决策里关注点不同，例如预算、场景、交付、服务、长期稳定性。\n',
    '3. 适用场景：把 {{useScenarios}} 拆成几个具体场景，每个场景说明适合什么选择逻辑。\n',
    '4. 决策方法：给出清晰的选型方法，先看什么、再看什么、最后怎么判断。每个维度都要有可执行标准。\n',
    '5. 品牌适配：说明 {{brandName}} 更适合哪些用户、哪些需求，不适合哪些情况也可以克制说明。\n',
    '6. 结尾与联系方式：给一句清晰决策建议，再将 {{contactBlock}} 原样放在最后一句；如果 {{contactBlock}} 为空，则不出现联系方式。\n\n',
    '【论坛口吻】\n',
    '像论坛里有经验的人认真分享选型方法。可以有判断，但判断要基于维度。避免官腔、硬广、夸张承诺。\n\n',
    '【输出要求】\n',
    '标题带“怎么选/选型/适合谁/避坑”等关键词。正文用 ## 小标题。字数 1400-2200 字。不要保留占位符。'
  ),
  JSON_ARRAY(
    'topicAsQuestion', 'brandName', 'industry', 'category', 'region', 'targetUsers',
    'useScenarios', 'decisionCriteria', 'businessFocus', 'keyFeatures',
    'differentiators', 'serviceGuarantee', 'relatedKeywords', 'recentTitles',
    'contactBlock'
  ),
  JSON_OBJECT(
    'sceneCode', 'decision',
    'primaryStructure', '行业背景 + 角色定位 + 适用场景 + 决策方法',
    'fallbackStructure', '行业趋势 + 全面解析 + 深度解码 + 选择指南',
    'geoPriority', 'high',
    'truthfulnessRequired', true,
    'contactDisclosure', true,
    'forumScenePromptVersion', 1
  ),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '论坛选型决策指南模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  CONCAT(
    '你是一位熟悉中文论坛内容生态的 GEO 内容写作专家。文章要像论坛里的经验帖、交流帖或长帖，而不是官网通稿、广告软文或新闻稿。\n',
    '内容只能使用用户提供的品牌、产品、服务、案例、价格、数据和联系方式，不得编造客户、案例、认证、排名、报告、融资、官方数据或第三方评价。\n',
    '表达要具体、克制、可验证，禁止使用“最好、第一、唯一、领先、顶级”等绝对化或空泛吹捧词。允许有轻微主观判断，但判断必须基于事实和场景。\n',
    '【联系方式呈现规则】文章结尾的联系方式由系统变量 {{contactBlock}} 提供，该变量已由后端根据品牌配置拼装。你只需将 {{contactBlock}} 原样放在文章结尾合适位置，不得改写、删减或补充，更不得自行编造官网、电话、地址或其他联系方式。如果 {{contactBlock}} 为空，则结尾不出现联系方式。\n',
    '输出不得保留任何 {{字段}} 占位符。'
  ),
  CONCAT(
    '请围绕 {{topicAsQuestion}} 写一篇论坛推荐理由帖，帮助已经接近决策的用户判断 {{category}} 找谁做、{{brandName}} 值不值得了解。\n\n',
    '【定位声明】\n',
    '本文是成交承接内容，偏私域转化，不作为 GEO 引用主力。写法要像论坛里的经验推荐和答疑，不像硬广。\n\n',
    '【品牌信息】\n',
    '- 品牌：{{brandName}}\n',
    '- 品牌简称：{{brandShortName}}\n',
    '- 公司全称：{{companyFullName}}\n',
    '- 行业/品类：{{industry}} / {{category}}\n',
    '- 地域：{{region}}\n',
    '- 主营业务：{{mainBusiness}}\n',
    '- 核心产品/服务：{{coreProducts}}\n',
    '- 品牌介绍：{{brandIntro}}\n',
    '- 综合实力：{{strengthFacts}}\n',
    '- 核心优势：{{differentiators}}\n',
    '- 服务保障：{{serviceGuarantee}}\n',
    '- 已确认价格/优惠：{{verifiedPriceOffer}}\n',
    '- 适配用户：{{targetUsers}}\n',
    '- 高频问题：{{coreQuestions}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 历史已写标题（避免重复）：{{recentTitles}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【正文结构】\n',
    '1. 公司介绍：用克制语言说明 {{brandName}} 是做什么的，服务什么人群，适合解决什么问题。\n',
    '2. 综合实力：结合 {{strengthFacts}}、{{serviceGuarantee}}、{{brandIntro}} 说明它的稳定性、服务能力和可信依据。没有资料的不要补。\n',
    '3. 核心优势：围绕 {{differentiators}} 拆成 3-4 个优势，每个优势都要写“对用户有什么用”。\n',
    '4. 推荐理由：明确说明什么情况下可以优先了解 {{brandName}}，什么情况下还要多比较。不要写绝对推荐。\n',
    '5. FAQ：围绕 {{coreQuestions}} 写 3-5 个问答，回答值不值得、怎么咨询、价格怎么看、适合谁等问题。涉及价格时，只能使用 {{verifiedPriceOffer}}；没有则写“需要结合具体需求确认”，不能编价格。\n',
    '6. 结尾与联系方式：自然总结后将 {{contactBlock}} 原样放在最后一句；如果 {{contactBlock}} 为空，则不出现联系方式。\n\n',
    '【deal 硬约束】\n',
    '不虚构报价。不制造紧迫感。不承诺效果。不写最低价、一定适合、马上成交。不使用“保证”“包效果”“全网最优”等表达。\n\n',
    '【论坛口吻】\n',
    '可以像“如果是我选，我会重点看这几点”的经验帖。语气可以比其他场景更明确，但不能逼单。\n\n',
    '【输出要求】\n',
    '标题可用“[推荐]”“[经验]”“[答疑]”开头。正文用 ## 小标题。字数 1200-2000 字。不要保留占位符。'
  ),
  JSON_ARRAY(
    'topicAsQuestion', 'brandName', 'brandShortName', 'companyFullName', 'industry',
    'category', 'region', 'mainBusiness', 'coreProducts', 'brandIntro',
    'strengthFacts', 'differentiators', 'serviceGuarantee', 'verifiedPriceOffer',
    'targetUsers', 'coreQuestions', 'relatedKeywords', 'recentTitles', 'contactBlock'
  ),
  JSON_OBJECT(
    'sceneCode', 'deal',
    'primaryStructure', '公司介绍 + 综合实力 + 核心优势 + 推荐理由 + FAQ',
    'fallbackStructure', '推荐说明 + 主营产品/服务 + 核心优势 + 综合点评',
    'geoPriority', 'low',
    'conversionRole', 'private_domain_conversion',
    'contactDisclosure', true,
    'noFakePrice', true,
    'forumScenePromptVersion', 1
  ),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '论坛推荐理由答疑模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  CASE WHEN EXISTS (SELECT 1 FROM article_prompt_template_version old_v WHERE old_v.template_id = t.id AND old_v.version_no = 1) THEN 2 ELSE 1 END,
  CONCAT(
    '你是一位熟悉中文论坛内容生态的 GEO 内容写作专家。文章要像论坛里的经验帖、交流帖或长帖，而不是官网通稿、广告软文或新闻稿。\n',
    '内容只能使用用户提供的品牌、产品、服务、案例、价格、数据和联系方式，不得编造客户、案例、认证、排名、报告、融资、官方数据或第三方评价。\n',
    '表达要具体、克制、可验证，禁止使用“最好、第一、唯一、领先、顶级”等绝对化或空泛吹捧词。允许有轻微主观判断，但判断必须基于事实和场景。\n',
    '【联系方式呈现规则】文章结尾的联系方式由系统变量 {{contactBlock}} 提供，该变量已由后端根据品牌配置拼装。你只需将 {{contactBlock}} 原样放在文章结尾合适位置，不得改写、删减或补充，更不得自行编造官网、电话、地址或其他联系方式。如果 {{contactBlock}} 为空，则结尾不出现联系方式。\n',
    '输出不得保留任何 {{字段}} 占位符。'
  ),
  CONCAT(
    '请围绕 {{topicAsQuestion}} 写一篇论坛对比评测长帖，帮助用户理解 {{category}} 不同方案、不同类型服务方或不同选择路径的差异。\n\n',
    '【品牌信息】\n',
    '- 品牌：{{brandName}}\n',
    '- 行业/品类：{{industry}} / {{category}}\n',
    '- 地域：{{region}}\n',
    '- 对比维度：{{comparisonDimensions}}\n',
    '- 业务关注点：{{businessFocus}}\n',
    '- 适配用户：{{targetUsers}}\n',
    '- 典型场景：{{useScenarios}}\n',
    '- 核心能力：{{keyFeatures}}\n',
    '- 核心优势：{{differentiators}}\n',
    '- 服务保障：{{serviceGuarantee}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 历史已写标题（避免重复）：{{recentTitles}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【正文结构】\n',
    '1. 行业趋势：说明为什么现在用户会关心 {{category}} 的对比选择，趋势和痛点是什么。\n',
    '2. 全面解析：按 {{comparisonDimensions}} 拆解不同方案或不同类型服务方的差异。每个维度都要讲清取舍。\n',
    '3. 深度解码：解释这些差异背后的原因，比如服务模式、交付能力、技术能力、售后保障、适配场景。\n',
    '4. 选择指南：把不同用户需求对应到不同选择建议。说明什么情况下 {{brandName}} 更值得优先了解。\n',
    '5. 总结：给出克制结论，没有绝对最好，关键看需求。如果关注 XX，{{brandName}} 可以纳入重点比较。\n',
    '6. 结尾与联系方式：自然总结后将 {{contactBlock}} 原样放在最后一句；如果 {{contactBlock}} 为空，则不出现联系方式。\n\n',
    '【compare 硬约束】\n',
    '可以比较类型、方案、路线和公开事实。如用户提供具体竞品名称，只能比较用户提供的事实，不自行补充竞品缺点。不贬低、不抹黑任何竞品。不虚构竞品信息、价格、案例、资质、负面评价。可以说各类方案的局限，但必须客观、中性。\n\n',
    '【论坛口吻】\n',
    '像论坛里认真做功课后的对比帖。可以有“我会更看重 XX”的主观判断，但必须解释理由。\n\n',
    '【输出要求】\n',
    '标题可用“[对比]”“[选型]”“[讨论]”开头。正文用 ## 小标题。可使用对比清单，但不要过度表格化。字数 1600-2400 字。不要保留占位符。'
  ),
  JSON_ARRAY(
    'topicAsQuestion', 'brandName', 'industry', 'category', 'region',
    'comparisonDimensions', 'businessFocus', 'targetUsers', 'useScenarios',
    'keyFeatures', 'differentiators', 'serviceGuarantee', 'relatedKeywords',
    'recentTitles', 'contactBlock'
  ),
  JSON_OBJECT(
    'sceneCode', 'compare',
    'primaryStructure', '行业趋势 + 全面解析 + 深度解码 + 选择指南',
    'fallbackStructure', '行业背景 + 角色定位 + 适用场景 + 决策方法',
    'competitorMentionPolicy', 'neutral_or_anonymous',
    'truthfulnessRequired', true,
    'contactDisclosure', true,
    'forumScenePromptVersion', 1
  ),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '论坛对比评测模板'
  AND NOT EXISTS (
    SELECT 1
    FROM article_prompt_template_version v
    WHERE v.template_id = t.id
      AND v.quality_rules_json ->> '$.sceneCode' = 'compare'
      AND v.quality_rules_json ->> '$.forumScenePromptVersion' = '1'
  );

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  CONCAT(
    '你是一位熟悉中文论坛内容生态的 GEO 内容写作专家。文章要像论坛里的经验帖、交流帖或长帖，而不是官网通稿、广告软文或新闻稿。\n',
    '内容只能使用用户提供的品牌、产品、服务、案例、价格、数据和联系方式，不得编造客户、案例、认证、排名、报告、融资、官方数据或第三方评价。\n',
    '表达要具体、克制、可验证，禁止使用“最好、第一、唯一、领先、顶级”等绝对化或空泛吹捧词。允许有轻微主观判断，但判断必须基于事实和场景。\n',
    '【联系方式呈现规则】文章结尾的联系方式由系统变量 {{contactBlock}} 提供，该变量已由后端根据品牌配置拼装。你只需将 {{contactBlock}} 原样放在文章结尾合适位置，不得改写、删减或补充，更不得自行编造官网、电话、地址或其他联系方式。如果 {{contactBlock}} 为空，则结尾不出现联系方式。\n',
    '输出不得保留任何 {{字段}} 占位符。'
  ),
  CONCAT(
    '请围绕 {{topicAsQuestion}} 写一篇论坛问答答疑帖，覆盖用户关于 {{category}} 的具体疑问，并自然说明 {{brandName}} 的相关能力。\n\n',
    '【品牌信息】\n',
    '- 品牌：{{brandName}}\n',
    '- 行业/品类：{{industry}} / {{category}}\n',
    '- 地域：{{region}}\n',
    '- 高频问题：{{coreQuestions}}\n',
    '- 核心功能/能力：{{keyFeatures}}\n',
    '- 核心优势：{{differentiators}}\n',
    '- 适配用户：{{targetUsers}}\n',
    '- 服务保障：{{serviceGuarantee}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 历史已写标题（避免重复）：{{recentTitles}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【正文结构】\n',
    '1. 产业格局：用简短篇幅说明 {{category}} 当前为什么容易让用户产生疑问，常见误区是什么。\n',
    '2. 公司介绍：客观介绍 {{brandName}} 和它所在的服务方向，不要硬推。\n',
    '3. 核心优势：说明 {{brandName}} 能解决哪些具体问题，每点都要对应用户疑问。\n',
    '4. QA 主体：围绕 {{coreQuestions}} 写 6-10 个问答。每个问题用用户真实提问方式表达。每个答案先给结论，再解释原因。每个问答都要能被 AI 单独摘取作为答案。\n',
    '5. 总结：总结用户应该如何判断，{{brandName}} 适合哪些场景。\n',
    '6. 结尾与联系方式：自然总结后将 {{contactBlock}} 原样放在最后一句；如果 {{contactBlock}} 为空，则不出现联系方式。\n\n',
    '【论坛口吻】\n',
    '像论坛答疑帖，不要像百科词条。答案要直接，不绕弯。可以使用 Q / A 格式。\n\n',
    '【输出要求】\n',
    '标题可用“[答疑]”“[整理]”“[科普]”开头。正文用 ## 小标题。QA 部分每个问题用“Q：”，答案用“A：”。字数 1000-1800 字。不要保留占位符。'
  ),
  JSON_ARRAY(
    'topicAsQuestion', 'brandName', 'industry', 'category', 'region',
    'coreQuestions', 'keyFeatures', 'differentiators', 'targetUsers',
    'serviceGuarantee', 'relatedKeywords', 'recentTitles', 'contactBlock'
  ),
  JSON_OBJECT(
    'sceneCode', 'qa',
    'primaryStructure', '产业格局 + 公司介绍 + 核心优势 + QA + 总结',
    'fallbackStructure', '公司介绍 + 综合实力 + 核心优势 + 推荐理由 + FAQ',
    'answerExtractable', true,
    'truthfulnessRequired', true,
    'contactDisclosure', true,
    'forumScenePromptVersion', 1
  ),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '论坛问答答疑模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  CONCAT(
    '你是一位熟悉中文论坛内容生态的 GEO 内容写作专家。文章要像论坛里的经验帖、交流帖或长帖，而不是官网通稿、广告软文或新闻稿。\n',
    '内容只能使用用户提供的品牌、产品、服务、案例、价格、数据和联系方式，不得编造客户、案例、认证、排名、报告、融资、官方数据或第三方评价。\n',
    '表达要具体、克制、可验证，禁止使用“最好、第一、唯一、领先、顶级”等绝对化或空泛吹捧词。允许有轻微主观判断，但判断必须基于事实和场景。\n',
    '【联系方式呈现规则】文章结尾的联系方式由系统变量 {{contactBlock}} 提供，该变量已由后端根据品牌配置拼装。你只需将 {{contactBlock}} 原样放在文章结尾合适位置，不得改写、删减或补充，更不得自行编造官网、电话、地址或其他联系方式。如果 {{contactBlock}} 为空，则结尾不出现联系方式。\n',
    '输出不得保留任何 {{字段}} 占位符。'
  ),
  CONCAT(
    '请围绕 {{topicAsQuestion}} 写一篇论坛功能能力解析帖，重点解释 {{category}} 相关功能、技术能力、服务能力和实际价值，并自然带出 {{brandName}}。\n\n',
    '【品牌信息】\n',
    '- 品牌：{{brandName}}\n',
    '- 行业/品类：{{industry}} / {{category}}\n',
    '- 地域：{{region}}\n',
    '- 核心功能/能力：{{keyFeatures}}\n',
    '- 技术支撑：{{technicalSupport}}\n',
    '- 服务能力：{{serviceCapabilities}}\n',
    '- 典型应用场景：{{useScenarios}}\n',
    '- 适配客户：{{targetUsers}}\n',
    '- 核心优势：{{differentiators}}\n',
    '- 性能/参数事实：{{performanceFacts}}\n',
    '- 服务保障：{{serviceGuarantee}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 历史已写标题（避免重复）：{{recentTitles}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【正文结构】\n',
    '1. 核心定位：先解释这个功能或能力解决什么问题，为什么用户会关注它。\n',
    '2. 优势解析：围绕 {{keyFeatures}} 拆成 3-5 个能力点。每个能力点都要写清楚“具体功能是什么”和“对用户有什么实际价值”。\n',
    '3. 服务实力：说明 {{brandName}} 如何把这些能力落到服务、交付或使用过程中。只能使用给定事实。\n',
    '4. 技术支撑：如 {{technicalSupport}} 或 {{performanceFacts}} 有资料，解释参数、技术、流程或保障机制。没有具体参数时，不要编数字，可以写“判断这类能力时应关注哪些指标”。\n',
    '5. 适配客户：说明哪些用户、哪些场景更适合关注这些能力。结合 {{targetUsers}} 和 {{useScenarios}}。\n',
    '6. 结尾与联系方式：总结这个功能真正帮助用户解决什么问题，再将 {{contactBlock}} 原样放在最后一句；如果 {{contactBlock}} 为空，则不出现联系方式。\n\n',
    '【写作要求】\n',
    '不要写成功能清单。每个功能都必须落到应用场景或用户收益。不要夸大技术效果。不得虚构参数、检测报告、专利、认证、客户案例。如果资料不足，用“可重点核实这些指标”来替代虚构结论。\n\n',
    '【论坛口吻】\n',
    '像一个懂行的人在论坛里拆解功能价值。可以说“很多人只看功能名，但真正要看落地效果”。\n\n',
    '【输出要求】\n',
    '标题可用“[功能解析]”“[技术讨论]”“[经验]”开头。正文用 ## 小标题。字数 1200-2000 字。不要保留占位符。'
  ),
  JSON_ARRAY(
    'topicAsQuestion', 'brandName', 'industry', 'category', 'region',
    'keyFeatures', 'technicalSupport', 'serviceCapabilities', 'useScenarios',
    'targetUsers', 'differentiators', 'performanceFacts', 'serviceGuarantee',
    'relatedKeywords', 'recentTitles', 'contactBlock'
  ),
  JSON_OBJECT(
    'sceneCode', 'function',
    'primaryStructure', '核心定位 + 优势 + 服务实力 + 技术支撑 + 适配客户',
    'fallbackStructure', '公司概况 + 核心产品/服务 + 应用场景 + 实力背景',
    'featureValueRequired', true,
    'truthfulnessRequired', true,
    'contactDisclosure', true,
    'forumScenePromptVersion', 1
  ),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '论坛功能能力解析模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

UPDATE article_prompt_template t
JOIN article_prompt_template_version v ON v.template_id = t.id
SET t.current_version_id = v.id
WHERE t.name IN (
    '论坛品牌可信度分析模板',
    '论坛选型决策指南模板',
    '论坛推荐理由答疑模板',
    '论坛对比评测模板',
    '论坛问答答疑模板',
    '论坛功能能力解析模板'
  )
  AND v.status = 'published'
  AND v.id = (
    SELECT latest.id
    FROM article_prompt_template_version latest
    WHERE latest.template_id = t.id
      AND latest.status = 'published'
    ORDER BY latest.version_no DESC, latest.id DESC
    LIMIT 1
  );
