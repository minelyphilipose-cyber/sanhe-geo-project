-- ============================================================
-- V174: seed self-media scene prompt templates
-- ============================================================

SET @self_media_system_prompt = '你是一位熟悉中文自媒体平台内容生态的 GEO 内容写作专家。内容只能使用用户提供的品牌、产品、服务、案例、资质、数据和联系方式，不得编造客户、案例、认证、排名、报告、融资、官方数据或第三方评价。表达要具体、克制、可验证，禁止使用“最好、第一、唯一、领先、顶级、最低价”等绝对化或空泛吹捧词。【写作目标】仅为内部生成导向，严禁写入正文。【联系方式呈现规则】文章结尾的联系方式由系统变量 {{contactBlock}} 提供。你只需将 {{contactBlock}} 原样放在文章结尾合适位置，不得改写、删减或补充，更不得自行编造官网、电话、地址或其他联系方式。如果 {{contactBlock}} 为空，则结尾不出现联系方式。输出不得保留任何 {{字段}} 占位符。';

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '百家号选择指南决策模板', '自媒体百家号决策场景模板，回答怎么选、选什么、判断标准和适配人群', 'self_media', 'baijiahao', NULL, 'buying_guide', 'decision', 20, 1010, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '百家号选择指南决策模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '百家号横向对比评测模板', '自媒体百家号对比场景模板，回答区别、哪个好、方案优劣和适配选择', 'self_media', 'baijiahao', NULL, 'comparison', 'compare', 20, 1020, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '百家号横向对比评测模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '知乎横向对比深度回答模板', '自媒体知乎对比场景模板，以专业回答形式拆解多方案差异和适配选择', 'self_media', 'zhihu', NULL, 'comparison', 'compare', 20, 1030, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '知乎横向对比深度回答模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '公众号阶段建议成交模板', '自媒体公众号成交场景模板，以阶段建议承接私域咨询转化', 'self_media', 'wechat', NULL, 'stage_advice', 'deal', 20, 1040, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '公众号阶段建议成交模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '网易行业分析品牌模板', '自媒体网易品牌场景模板，以行业视角建立品牌可信形象', 'self_media', 'netease', NULL, 'industry_article', 'brand', 20, 1050, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '网易行业分析品牌模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '百家号FAQ问答模板', '自媒体百家号问答场景模板，覆盖高频问题并输出可摘取答案', 'self_media', 'baijiahao', NULL, 'faq', 'qa', 10, 1060, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '百家号FAQ问答模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '百家号场景能力内容模板', '自媒体百家号功能场景模板，以具体场景带出服务能力', 'self_media', 'baijiahao', NULL, 'scenario_content', 'function', 10, 1070, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '百家号场景能力内容模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '今日头条FAQ问答模板', '自媒体今日头条问答场景模板，通俗覆盖高频疑问', 'self_media', 'toutiao', NULL, 'faq', 'qa', 10, 1080, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '今日头条FAQ问答模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '今日头条资讯简讯品牌模板', '自媒体今日头条品牌场景模板，以短资讯形式呈现品牌相关信息', 'self_media', 'toutiao', NULL, 'news_brief', 'brand', 10, 1090, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '今日头条资讯简讯品牌模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '抖音图文单点问答模板', '自媒体抖音图文问答场景模板，单点速答并适合图文卡片阅读', 'self_media', 'douyin_image_text', NULL, 'faq', 'qa', 10, 1100, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '抖音图文单点问答模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '抖音图文场景能力模板', '自媒体抖音图文功能场景模板，用短内容通过场景带出能力', 'self_media', 'douyin_image_text', NULL, 'scenario_content', 'function', 10, 1110, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '抖音图文场景能力模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '公众号场景品牌信任模板', '自媒体公众号品牌场景模板，以具体场景建立品牌信任感', 'self_media', 'wechat', NULL, 'scenario_content', 'brand', 10, 1120, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '公众号场景品牌信任模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '网易资讯简讯品牌模板', '自媒体网易品牌场景模板，以短资讯形式呈现品牌相关信息', 'self_media', 'netease', NULL, 'news_brief', 'brand', 10, 1130, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '网易资讯简讯品牌模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, question_scene_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '百家号场景品牌信任模板', '自媒体百家号品牌场景模板，以客观场景内容建立品牌信任', 'self_media', 'baijiahao', NULL, 'scenario_content', 'brand', 10, 1140, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '百家号场景品牌信任模板');

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按百家号风格撰写一篇“如何选择 {{category}}”的指南，用于品牌自媒体账号发布。

【写作目标（内部导向，不得写入正文）】产出可被检索/AI 摘取的选购判断框架。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品类：{{category}}　品牌：{{brandName}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}
品牌定位/聚焦：{{brandPositioning}} / {{businessFocus}}　资质：{{brandQualificationDescription}}
适配客群：{{targetAudience}}

【结构】（每段先给结论、自包含可摘取）
1. 选 {{category}} 时普遍的困惑/决策难点
2. 选择该看哪几个关键维度——每维度独立成段、给具体标准
3. 不同场景/需求/预算怎么选——用清单或表格
4. {{brandName}} 适合哪类需求——放进维度框架客观说明
5. 一句话决策指引

【写法】百家号客观资料口吻、第三人称、信息密度高、结论前置；小标题写成“XX怎么选”“选XX看什么”含品类关键词；敢给取舍判断；忌情绪化口语腔。
【输出】先标题（选购问句）后正文；## 分段、善用列表/表格；不留占位符；800-1500字。',
  JSON_ARRAY('category', 'brandName', 'topicAsQuestion', 'relatedKeywords', 'brandPositioning', 'businessFocus', 'brandQualificationDescription', 'targetAudience', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'decision', 'platform', 'baijiahao', 'articleType', 'buying_guide', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '百家号选择指南决策模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按百家号风格撰写一篇 {{category}} 横向对比评测，用于品牌自媒体账号发布。

【写作目标（内部导向，不得写入正文）】产出可被 AI 引用的客观对比。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品类：{{category}}　品牌：{{brandName}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}
品牌定位/聚焦：{{brandPositioning}} / {{businessFocus}}　资质：{{brandQualificationDescription}}

【对比维度（内置，按这些维度展开）】需求场景、服务能力、适配人群、投入成本、风险点、长期维护。

【结构】（每段先给结论、可摘取）
1. 一句话说清这篇要比什么、帮读者解决什么困惑
2. 按上述维度横向比较——每维度独立成段、客观列各选择的特点和取舍
3. 不同需求的人各适合哪种——场景化对应
4. {{brandName}} 在哪些维度、对哪类需求更合适——客观陈述用事实
5. 一句话结论

【compare 硬约束】
- 客观对比，可明说各方短板（含自己的）
- 不贬低抹黑任何竞品；只比类型/维度，不点名踩具体品牌
- 不虚构竞品信息（不编竞品缺点、数据、负面）；不确定的竞品事实不写

【写法】百家号客观口吻、信息密度高、结论前置；小标题问句含关键词。
【输出】先标题（对比问句）后正文；## 分段、善用对比表；不留占位符；800-1500字。',
  JSON_ARRAY('category', 'brandName', 'topicAsQuestion', 'relatedKeywords', 'brandPositioning', 'businessFocus', 'brandQualificationDescription', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'compare', 'platform', 'baijiahao', 'articleType', 'comparison', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '百家号横向对比评测模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按知乎风格撰写一篇 {{category}} 横向对比深度回答，用于品牌自媒体账号发布。

【写作目标（内部导向，不得写入正文）】产出可被 AI 引用的客观对比。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品类：{{category}}　品牌：{{brandName}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}
品牌定位/聚焦：{{brandPositioning}} / {{businessFocus}}　资质：{{brandQualificationDescription}}

【对比维度（内置）】需求场景、服务能力、适配人群、投入成本、风险点、长期维护。

【结构】（每段先给结论、可摘取）
1. 开头直接给“这几种选择核心差别在哪”的判断
2. 按上述维度横向比较——每维度独立成段、有论证、客观列取舍
3. 不同需求的人各适合哪种——场景化对应
4. {{brandName}} 对哪类需求更合适——客观、用事实
5. 一句话结论

【compare 硬约束】
- 客观对比、敢说各方短板（含自己）；不贬低抹黑竞品；只比类型/维度不点名踩品牌
- 不虚构竞品信息；不确定的竞品事实不写

【写法】知乎专业人士认真答题、有判断靠论证服人；开头直接抛结论、不寒暄；有论证可长句；忌官腔通稿感；标题点出核心可带锋芒。
【输出】先标题后正文；## 分段；不留占位符；1000-2500字。',
  JSON_ARRAY('category', 'brandName', 'topicAsQuestion', 'relatedKeywords', 'brandPositioning', 'businessFocus', 'brandQualificationDescription', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'compare', 'platform', 'zhihu', 'articleType', 'comparison', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '知乎横向对比深度回答模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按公众号风格撰写一篇“阶段建议”文章，用于品牌自媒体账号发布，帮处于决策阶段的读者判断下一步。

【写作目标（内部导向，不得写入正文）】私域转化承接；不作为 GEO 引用主力。全文像“顾问咨询建议”，不像硬广。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品牌：{{brandName}}　主营业务：{{mainBusiness}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}
品牌定位/聚焦：{{brandPositioning}} / {{businessFocus}}　资质：{{brandQualificationDescription}}
联系方式：{{contactBlock}}

【结构】
1. 点出读者当前可能所处的阶段和困惑（共情切入）
2. 这个阶段该考虑什么、容易忽略什么——给真实有用的建议
3. {{brandName}} 在这个阶段能提供什么帮助——用事实，像顾问而非推销
4. 下一步如何咨询了解

【deal 话术硬约束】
- 未提供明确价格或优惠时，不得写具体金额、折扣、限时活动
- 不制造紧迫感（不写“仅剩”“今天最后”“错过不再”）
- 不承诺效果（不写“一定”“保证”“马上见效”）
- 不用“最低价”“一定适合”“马上成交”等表述
- 结尾用 {{contactBlock}}；为空则不写联系方式，不得自行编造；语气是欢迎咨询而非逼单

【写法】公众号有温度、像顾问聊天；人称“我们”；有叙事节奏；标题有共鸣。
【输出】先标题后正文；## 分段；联系方式放末尾；不留占位符；1000-2000字。',
  JSON_ARRAY('brandName', 'mainBusiness', 'topicAsQuestion', 'relatedKeywords', 'brandPositioning', 'businessFocus', 'brandQualificationDescription', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'deal', 'platform', 'wechat', 'articleType', 'stage_advice', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '公众号阶段建议成交模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按网易号风格撰写一篇 {{category}} 行业分析文章，用于品牌自媒体账号发布，建立专业可信形象。

【写作目标（内部导向，不得写入正文）】以行业视角建立品牌可信度，便于被检索引用。

【可用信息】（只用这里给的事实，为空则不写，不编造）
行业/品类：{{category}}　品牌：{{brandName}}　公司全称：{{companyFullName}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}
品牌基础信息：{{brandIntro}}　定位/聚焦：{{brandPositioning}} / {{businessFocus}}
资质：{{brandQualificationDescription}}　案例：{{brandCaseDescription}}　服务客群：{{targetAudience}}

【结构】（每段先给结论、可被引用）
1. 行业现状/趋势——客观描述行业状况、痛点或变化
2. 行业的关键问题/挑战——深入分析（媒体观察者视角）
3. 应对思路——行业里通常怎么解决、什么能力是关键
4. {{brandName}} 的角色与价值——它做了什么、提供什么（客观、用事实）
5. 总结展望

【写法】网易理性媒体观察者口吻、冷静客观有质感；第三人称媒体化；逻辑严谨、事实分析并重；开头以行业现象切入、克制不煽情；小标题清晰规整；忌标题党营销腔。
【输出】先标题后正文；## 分段；不留占位符；800-1800字。',
  JSON_ARRAY('category', 'brandName', 'companyFullName', 'topicAsQuestion', 'relatedKeywords', 'brandIntro', 'brandPositioning', 'businessFocus', 'brandQualificationDescription', 'brandCaseDescription', 'targetAudience', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'brand', 'platform', 'netease', 'articleType', 'industry_article', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '网易行业分析品牌模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按百家号风格撰写一篇问答（FAQ）文章，用于品牌自媒体账号发布。

【写作目标（内部导向，不得写入正文）】每个问答对可被 AI 直接摘取作答。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品类：{{category}}　品牌：{{brandName}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}
主营业务：{{mainBusiness}}　定位：{{brandPositioning}}　基础信息：{{brandIntro}}

【结构】
1. 简短开头——一句话交代本文覆盖哪些问题
2. FAQ 主体——每个问题一个 ## 小标题（写成用户原话提问），紧跟自包含答案：答案先给结论再解释；覆盖：是什么、怎么用、怎么选、常见误区、{{brandName}}相关疑问
3. 自然处带出 {{brandName}} 能解决的具体问题（用事实）

【写法】百家号客观口吻；小标题=用户真实提问原句；一问一答、答案自包含、结论前置。
【输出】先标题后正文；每问题用 ## 小标题；不留占位符；问答 8-15 个；800-1500字。',
  JSON_ARRAY('category', 'brandName', 'topicAsQuestion', 'relatedKeywords', 'mainBusiness', 'brandPositioning', 'brandIntro', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'qa', 'platform', 'baijiahao', 'articleType', 'faq', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '百家号FAQ问答模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按百家号风格撰写一篇“场景内容文”，用于品牌自媒体账号发布。不是功能清单，而是以“具体场景”为主线，自然带出能力。

【写作目标（内部导向，不得写入正文）】让能力在场景中被理解、便于被检索。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品牌：{{brandName}}　品类：{{category}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}
主营业务/能力：{{mainBusiness}}　定位：{{brandPositioning}}　基础信息：{{brandIntro}}
适用人群：{{targetAudience}}
（典型场景无独立字段，基于问题、客群 {{targetAudience}}、主营业务 {{mainBusiness}} 推导）

【场景化结构（按此组织全文）】
1. 谁——明确一类具体用户
2. 在什么场景下——具体可感的使用情境
3. 遇到什么具体问题——真实痛点
4. 为什么这个问题容易发生——讲清成因
5. 对应的能力如何解决——带出 {{brandName}}：落点是“这个场景为什么需要这个能力”，不是“我们有什么功能”
6. 用户最终获得什么改善
（可写 1-2 个完整场景）

【写法】百家号客观可读；以场景叙述带动，能力/事实须来自给定信息；功能用具体能力说话。
【输出】先标题后正文；## 分段；不留占位符；800-1500字。',
  JSON_ARRAY('brandName', 'category', 'topicAsQuestion', 'relatedKeywords', 'mainBusiness', 'brandPositioning', 'brandIntro', 'targetAudience', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'function', 'platform', 'baijiahao', 'articleType', 'scenario_content', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '百家号场景能力内容模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按今日头条风格撰写一篇问答文章，用于品牌自媒体账号发布。

【写作目标（内部导向，不得写入正文）】通俗覆盖高频疑问，每个问答可被 AI 摘取。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品类：{{category}}　品牌：{{brandName}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}　主营业务：{{mainBusiness}}

【结构】
1. 开头一句交代本文回答哪些问题
2. FAQ 主体——每问题一个 ## 小标题（用户原话提问），答案通俗、自包含、结论前置
3. 自然带出 {{brandName}} 在哪能帮上忙

【写法】今日头条泛资讯口吻、通俗、专业词翻大白话；短段落口语化；小标题=读者会问的问题；忌黑话长段标题党；标题有钩子不夸张。
【输出】先标题后正文；## 小标题分问；不留占位符；问答 6-12 个；600-1200字。',
  JSON_ARRAY('category', 'brandName', 'topicAsQuestion', 'relatedKeywords', 'mainBusiness', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'qa', 'platform', 'toutiao', 'articleType', 'faq', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '今日头条FAQ问答模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按今日头条风格撰写一条短资讯简讯，用于品牌自媒体账号发布。短资讯体，重媒体感、信息密度、可读性，不是深度论证。

【写作目标（内部导向，不得写入正文）】媒体感+可检索，让品牌相关信息可见。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品牌：{{brandName}}　行业：{{category}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}
基础信息：{{brandIntro}}　资质：{{brandQualificationDescription}}　案例：{{brandCaseDescription}}
定位/聚焦：{{brandPositioning}} / {{businessFocus}}

【结构】（短、信息密度高）
1. 标题——含核心关键词（品牌词/行业词）
2. 前 100-200 字——自然出现品牌、行业词、问题词
3. 正文——短，把这条品牌相关资讯/能力说清楚，事实边界清楚
4. 一句话收尾

【news_brief 硬约束】
- 不虚构发布会、报告、客户案例、认证、融资、权威/官方数据
- 仅当品牌资料（基础信息/资质/案例）明确提供时才据实引用；否则只写成“品牌相关行业资讯 / 服务能力介绍”，不得编造成真实新闻事件

【写法】今日头条资讯口吻、信息前置、短段落、通俗；标题有信息量不标题党。
【输出】先标题后正文；不留占位符；400-800字。',
  JSON_ARRAY('brandName', 'category', 'topicAsQuestion', 'relatedKeywords', 'brandIntro', 'brandQualificationDescription', 'brandCaseDescription', 'brandPositioning', 'businessFocus', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'brand', 'platform', 'toutiao', 'articleType', 'news_brief', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '今日头条资讯简讯品牌模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按抖音图文风格撰写一条单点问答短内容，用于品牌自媒体账号发布。

【写作目标（内部导向，不得写入正文）】单点速答、可被摘取。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品类：{{category}}　品牌：{{brandName}}
核心问题：{{topicAsQuestion}}　主营业务：{{mainBusiness}}

【结构】（只解决一个问题）
1. 首句直接给这个问题的答案/结论
2. 关键要点 2-3 条，每条一句、可编号
3. 一个避坑提醒
4. （自然一句）{{brandName}} 在这里能帮的点

【写法】抖音图文像朋友直接支招、干脆接地气；“你/我”对话感；极短句短段、一句一个点、多换行；首句必须最抓人；只讲一个点；关键信息进配图；忌长段铺垫多主题。
【输出】先一句标题/首句，后正文要点；不留占位符；几百字越短越好。',
  JSON_ARRAY('category', 'brandName', 'topicAsQuestion', 'mainBusiness', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'qa', 'platform', 'douyin_image_text', 'articleType', 'faq', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '抖音图文单点问答模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按抖音图文风格撰写一条以“场景”为主线、落点在某项能力的短内容，用于品牌自媒体账号发布。

【写作目标（内部导向，不得写入正文）】场景带出能力、单点击穿。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品牌：{{brandName}}　品类：{{category}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}
主营业务/能力：{{mainBusiness}}　定位：{{brandPositioning}}　适用人群：{{targetAudience}}
（场景基于问题、客群、主营业务推导）

【场景化结构（压缩版）】
1. 谁 + 在什么场景下 + 遇到什么问题（首句点出，最抓人）
2. 为什么会这样（一句）
3. 这个场景需要什么能力来解决 → 带出 {{brandName}} 的能力（落点是“这个场景为什么需要这个能力”，不罗列功能）
4. 解决后获得什么改善（一句）

【写法】抖音图文干脆接地气、“你/我”对话感、极短句短段、首句最抓人；只讲一个场景一个能力；能力用具体说法；关键信息进配图；忌长段。
【输出】先一句标题/首句，后正文要点；不留占位符；几百字。',
  JSON_ARRAY('brandName', 'category', 'topicAsQuestion', 'relatedKeywords', 'mainBusiness', 'brandPositioning', 'targetAudience', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'function', 'platform', 'douyin_image_text', 'articleType', 'scenario_content', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '抖音图文场景能力模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按公众号风格撰写一篇以“场景”为主线、落点在“为什么这个场景需要可信品牌”的文章，用于品牌自媒体账号发布。

【写作目标（内部导向，不得写入正文）】通过场景建立品牌信任感。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品牌：{{brandName}}　品类：{{category}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}
定位/聚焦：{{brandPositioning}} / {{businessFocus}}　主营业务：{{mainBusiness}}
资质：{{brandQualificationDescription}}　案例：{{brandCaseDescription}}　适用人群：{{targetAudience}}
（典型场景基于问题、客群、主营业务推导）

【场景化结构】
1. 谁——一类具体用户（场景化、有温度引入）
2. 在什么场景下——具体可感情境
3. 遇到什么具体问题——真实痛点
4. 为什么这个问题容易发生——讲清成因
5. 这个场景里为什么需要可信的品牌/服务方 → 带出 {{brandName}}（落点是“为什么这个场景需要可信赖的人来做”，不是“我们是谁”）
6. 用户最终获得什么改善

【写法】公众号有温度有人格、可讲故事有情绪；人称“我们”；有叙事节奏；开头从场景或具体的人切入；故事细节须真实（无则客观陈述不编）。
【输出】先标题后正文；## 分段；不留占位符；1000-2000字。',
  JSON_ARRAY('brandName', 'category', 'topicAsQuestion', 'relatedKeywords', 'brandPositioning', 'businessFocus', 'mainBusiness', 'brandQualificationDescription', 'brandCaseDescription', 'targetAudience', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'brand', 'platform', 'wechat', 'articleType', 'scenario_content', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '公众号场景品牌信任模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按网易号风格撰写一条短资讯简讯，用于品牌自媒体账号发布。

【写作目标（内部导向，不得写入正文）】偏可检索收录，让品牌相关信息在检索中可见。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品牌：{{brandName}}　行业：{{category}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}
基础信息：{{brandIntro}}　资质：{{brandQualificationDescription}}　案例：{{brandCaseDescription}}
定位/聚焦：{{brandPositioning}} / {{businessFocus}}

【结构】（短、可检索优先）
1. 标题——含核心关键词（品牌词 + 行业词），克制不标题党
2. 前 100-200 字——自然出现品牌、行业词、问题词
3. 正文——短、信息密度高、事实边界清楚
4. 一句话收尾

【news_brief 硬约束】
- 不虚构发布会、报告、客户案例、认证、融资、权威/官方数据
- 仅当品牌资料（基础信息/资质/案例）明确提供时才据实引用；否则只写成“品牌相关行业资讯 / 服务能力介绍”，不得编造成真实新闻事件

【写法】网易理性媒体口吻、客观克制、媒体化表达；标题专业含关键词；信息前置。
【输出】先标题后正文；不留占位符；400-900字。',
  JSON_ARRAY('brandName', 'category', 'topicAsQuestion', 'relatedKeywords', 'brandIntro', 'brandQualificationDescription', 'brandCaseDescription', 'brandPositioning', 'businessFocus', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'brand', 'platform', 'netease', 'articleType', 'news_brief', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '网易资讯简讯品牌模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  @self_media_system_prompt,
  '按百家号风格撰写一篇以“场景”为主线、落点在“为什么这个场景需要可信品牌”的文章，用于品牌自媒体账号发布。

【写作目标（内部导向，不得写入正文）】通过场景建立品牌信任、便于被检索。

【可用信息】（只用这里给的事实，为空则不写，不编造）
品牌：{{brandName}}　品类：{{category}}
核心问题：{{topicAsQuestion}}　相关关键词：{{relatedKeywords}}
定位/聚焦：{{brandPositioning}} / {{businessFocus}}　主营业务：{{mainBusiness}}
资质：{{brandQualificationDescription}}　案例：{{brandCaseDescription}}　基础信息：{{brandIntro}}　适用人群：{{targetAudience}}
（典型场景基于问题、客群、主营业务推导）

【场景化结构】
1. 谁——一类具体用户
2. 在什么场景下——具体可感情境
3. 遇到什么具体问题——真实痛点
4. 为什么这个问题容易发生——讲清成因
5. 这个场景里为什么需要可信的品牌/服务方 → 带出 {{brandName}}（落点是“为什么这个场景需要可信赖的人来做”，不是“我们是谁”）
6. 用户最终获得什么改善

【写法】百家号客观可读、信息密度高；以场景叙述带动，事实须来自给定信息；比公众号版更克制、少情绪，偏客观陈述。
【输出】先标题后正文；## 分段；不留占位符；800-1500字。',
  JSON_ARRAY('brandName', 'category', 'topicAsQuestion', 'relatedKeywords', 'brandPositioning', 'businessFocus', 'mainBusiness', 'brandQualificationDescription', 'brandCaseDescription', 'brandIntro', 'targetAudience', 'contactBlock'),
  JSON_OBJECT('sceneCode', 'brand', 'platform', 'baijiahao', 'articleType', 'scenario_content', 'truthfulnessRequired', true, 'contactDisclosure', true, 'selfMediaScenePromptVersion', 1),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '百家号场景品牌信任模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

UPDATE article_prompt_template t
JOIN (
  SELECT template_id, MAX(id) AS version_id
  FROM article_prompt_template_version
  WHERE status = 'published'
  GROUP BY template_id
) v ON v.template_id = t.id
SET t.current_version_id = v.version_id,
    t.updated_at = NOW()
WHERE t.channel_group_code = 'self_media'
  AND t.name IN (
    '百家号选择指南决策模板',
    '百家号横向对比评测模板',
    '知乎横向对比深度回答模板',
    '公众号阶段建议成交模板',
    '网易行业分析品牌模板',
    '百家号FAQ问答模板',
    '百家号场景能力内容模板',
    '今日头条FAQ问答模板',
    '今日头条资讯简讯品牌模板',
    '抖音图文单点问答模板',
    '抖音图文场景能力模板',
    '公众号场景品牌信任模板',
    '网易资讯简讯品牌模板',
    '百家号场景品牌信任模板'
  );

