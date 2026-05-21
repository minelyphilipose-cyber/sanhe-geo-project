-- ============================================================
-- V172: normalize forum prompt templates to supported variables
-- ============================================================

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET
  v.user_prompt_template = CONCAT(
    '请围绕 {{topicAsQuestion}} 写一篇论坛长帖，分析 {{brandName}} 在 {{industry}} 相关业务里的品牌可信度和适配价值。\n\n',
    '【可用品牌事实】\n',
    '- 品牌：{{brandName}}\n',
    '- 品牌简称：{{brandShortName}}\n',
    '- 公司全称：{{companyFullName}}\n',
    '- 行业：{{industry}}\n',
    '- 地域：{{region}}\n',
    '- 主营业务：{{mainBusiness}}\n',
    '- 品牌介绍：{{brandIntro}}\n',
    '- 品牌定位：{{brandPositioning}}\n',
    '- 资质描述：{{brandQualificationDescription}}\n',
    '- 案例描述：{{brandCaseDescription}}\n',
    '- 业务关注点：{{businessFocus}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 历史已写标题：{{recentTitles}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【正文结构，必须按顺序写】\n',
    '## 行业背景\n',
    '从 {{industry}} 的用户选择顾虑、常见信任问题和本主题 {{topicAsQuestion}} 切入，不要一开头就推品牌。\n\n',
    '## 公司解析\n',
    '基于 {{brandIntro}}、{{mainBusiness}}、{{brandPositioning}} 说明 {{brandName}} 是什么类型的服务方。只使用给定事实，没有的不要补。\n\n',
    '## 市场形象\n',
    '结合品牌定位、资质描述、案例描述和业务关注点，分析 {{brandName}} 在用户心智里更像哪类服务方。资料不足时写“建议重点核实哪些信息”，不要编造实力背书。\n\n',
    '## 客户价值\n',
    '围绕 {{mainBusiness}}、{{businessFocus}} 和 {{topicAsQuestion}} 说明它可能解决的实际问题。每个价值点都要对应一个用户困惑。\n\n',
    '## 总结展望\n',
    '用克制语气总结：如果用户关注哪些维度，{{brandName}} 值得纳入了解或对比范围。\n\n',
    '## 联系方式\n',
    '最后原样放置 {{contactBlock}}；如果 {{contactBlock}} 为空，则不出现联系方式。\n\n',
    '标题用论坛风格，可用“[分析]”“[经验]”“[讨论]”开头。字数 1200-1800 字。不要保留占位符。'
  ),
  v.variables_json = JSON_ARRAY(
    'topicAsQuestion', 'brandName', 'brandShortName', 'companyFullName', 'industry',
    'region', 'mainBusiness', 'brandIntro', 'brandPositioning',
    'brandQualificationDescription', 'brandCaseDescription', 'businessFocus',
    'relatedKeywords', 'recentTitles', 'contactBlock'
  ),
  v.quality_rules_json = JSON_SET(COALESCE(v.quality_rules_json, JSON_OBJECT()), '$.supportedVariableVersion', 2)
WHERE t.name = '论坛品牌可信度分析模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET
  v.user_prompt_template = CONCAT(
    '请围绕 {{topicAsQuestion}} 写一篇论坛选型决策指南，帮助用户判断 {{industry}} 相关服务怎么选、适不适合自己、应该看哪些依据。\n\n',
    '【可用品牌事实】\n',
    '- 品牌：{{brandName}}\n',
    '- 行业：{{industry}}\n',
    '- 地域：{{region}}\n',
    '- 主营业务：{{mainBusiness}}\n',
    '- 核心产品/服务：{{coreProducts}}\n',
    '- 品牌定位：{{brandPositioning}}\n',
    '- 资质描述：{{brandQualificationDescription}}\n',
    '- 案例描述：{{brandCaseDescription}}\n',
    '- 业务关注点：{{businessFocus}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 历史已写标题：{{recentTitles}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【正文结构，必须按顺序写】\n',
    '## 行业背景\n',
    '说明为什么用户在选择 {{industry}} 相关服务时容易纠结，常见误区是什么。\n\n',
    '## 角色定位\n',
    '解释不同类型用户在这个决策里关注点不同，例如预算、场景、交付、服务、长期稳定性。不要虚构具体客户画像。\n\n',
    '## 适用场景\n',
    '结合 {{topicAsQuestion}}、{{businessFocus}} 和 {{coreProducts}} 推导常见需求场景，每个场景说明适合什么选择逻辑；只能基于已给信息推导。\n\n',
    '## 决策方法\n',
    '给出清晰选型方法，围绕需求匹配度、服务边界、交付稳定性、公开事实可验证性展开。\n\n',
    '## 联系方式\n',
    '先给一句清晰决策建议，再原样放置 {{contactBlock}}；如果 {{contactBlock}} 为空，则不出现联系方式。\n\n',
    '标题带“怎么选/选型/适合谁/避坑”等关键词。字数 1400-2200 字。不要保留占位符。'
  ),
  v.variables_json = JSON_ARRAY(
    'topicAsQuestion', 'brandName', 'industry', 'region', 'mainBusiness',
    'coreProducts', 'businessFocus', 'brandPositioning',
    'brandQualificationDescription', 'brandCaseDescription', 'relatedKeywords',
    'recentTitles', 'contactBlock'
  ),
  v.quality_rules_json = JSON_SET(COALESCE(v.quality_rules_json, JSON_OBJECT()), '$.supportedVariableVersion', 2)
WHERE t.name = '论坛选型决策指南模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET
  v.user_prompt_template = CONCAT(
    '请围绕 {{topicAsQuestion}} 写一篇论坛推荐理由帖，帮助已经接近决策的用户判断这类需求找谁做、{{brandName}} 值不值得了解。\n\n',
    '【定位声明】本文是成交承接内容，偏私域转化，不作为 GEO 引用主力。写法要像论坛里的经验推荐和答疑，不像硬广。\n\n',
    '【可用品牌事实】\n',
    '- 品牌：{{brandName}}\n',
    '- 品牌简称：{{brandShortName}}\n',
    '- 公司全称：{{companyFullName}}\n',
    '- 行业：{{industry}}\n',
    '- 地域：{{region}}\n',
    '- 主营业务：{{mainBusiness}}\n',
    '- 核心产品/服务：{{coreProducts}}\n',
    '- 品牌介绍：{{brandIntro}}\n',
    '- 品牌定位：{{brandPositioning}}\n',
    '- 资质描述：{{brandQualificationDescription}}\n',
    '- 案例描述：{{brandCaseDescription}}\n',
    '- 业务关注点：{{businessFocus}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【正文结构，必须按顺序写】\n',
    '## 公司介绍\n',
    '用克制语言说明 {{brandName}} 是做什么的，服务什么需求。\n\n',
    '## 综合实力\n',
    '结合品牌介绍、品牌定位、资质描述、案例描述说明可信依据。没有资料的不要补，改写成“建议重点核实”。\n\n',
    '## 核心优势\n',
    '围绕 {{mainBusiness}}、{{coreProducts}} 和 {{businessFocus}} 拆成 3-4 个推荐理由，每个理由都要写对用户有什么用。\n\n',
    '## 推荐理由\n',
    '明确说明什么情况下可以优先了解 {{brandName}}，什么情况下还要多比较。不要写绝对推荐。\n\n',
    '## FAQ\n',
    '围绕 {{topicAsQuestion}} 和 {{businessFocus}} 写 3-5 个问答，回答值不值得、怎么咨询、价格怎么看、适合谁等问题。未提供明确价格事实时，只能写“需要结合具体需求确认”，不能编价格。\n\n',
    '## 联系方式\n',
    '自然总结后原样放置 {{contactBlock}}；如果 {{contactBlock}} 为空，则不出现联系方式。\n\n',
    '标题可用“[推荐]”“[经验]”“[答疑]”开头。字数 1200-2000 字。不要保留占位符。'
  ),
  v.variables_json = JSON_ARRAY(
    'topicAsQuestion', 'brandName', 'brandShortName', 'companyFullName', 'industry',
    'region', 'mainBusiness', 'coreProducts', 'brandIntro', 'brandPositioning',
    'brandQualificationDescription', 'brandCaseDescription', 'businessFocus',
    'relatedKeywords', 'recentTitles', 'contactBlock'
  ),
  v.quality_rules_json = JSON_SET(COALESCE(v.quality_rules_json, JSON_OBJECT()), '$.supportedVariableVersion', 2)
WHERE t.name = '论坛推荐理由答疑模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET
  v.user_prompt_template = CONCAT(
    '请围绕 {{topicAsQuestion}} 写一篇论坛对比评测长帖，帮助用户理解 {{industry}} 相关服务中不同方案、不同类型服务方或不同选择路径的差异。\n\n',
    '【可用品牌事实】\n',
    '- 品牌：{{brandName}}\n',
    '- 行业：{{industry}}\n',
    '- 地域：{{region}}\n',
    '- 业务关注点：{{businessFocus}}\n',
    '- 主营业务：{{mainBusiness}}\n',
    '- 核心产品/服务：{{coreProducts}}\n',
    '- 品牌定位：{{brandPositioning}}\n',
    '- 资质描述：{{brandQualificationDescription}}\n',
    '- 案例描述：{{brandCaseDescription}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【正文结构，必须按顺序写】\n',
    '## 行业趋势\n',
    '说明为什么现在用户会关心 {{industry}} 相关服务的对比选择，趋势和痛点是什么。\n\n',
    '## 全面解析\n',
    '从 {{topicAsQuestion}} 里提炼用户真正关心的对比维度，再结合 {{businessFocus}} 拆解不同方案或不同类型服务方的差异。不要虚构竞品事实。\n\n',
    '## 深度解码\n',
    '解释这些差异背后的原因，比如服务模式、交付能力、技术能力、售后保障、适配场景。\n\n',
    '## 选择指南\n',
    '把不同需求对应到不同选择建议。结合品牌定位、核心产品和可验证品牌事实，说明什么情况下 {{brandName}} 更值得优先了解。\n\n',
    '## 联系方式\n',
    '自然总结后原样放置 {{contactBlock}}；如果 {{contactBlock}} 为空，则不出现联系方式。\n\n',
    '不贬低、不抹黑任何竞品，不虚构竞品信息。标题可用“[对比]”“[选型]”“[讨论]”开头。字数 1600-2400 字。不要保留占位符。'
  ),
  v.variables_json = JSON_ARRAY(
    'topicAsQuestion', 'brandName', 'industry', 'region', 'businessFocus',
    'mainBusiness', 'coreProducts', 'brandPositioning',
    'brandQualificationDescription', 'brandCaseDescription', 'relatedKeywords',
    'recentTitles', 'contactBlock'
  ),
  v.quality_rules_json = JSON_SET(COALESCE(v.quality_rules_json, JSON_OBJECT()), '$.supportedVariableVersion', 2)
WHERE t.name = '论坛对比评测模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET
  v.user_prompt_template = CONCAT(
    '请围绕 {{topicAsQuestion}} 写一篇论坛问答答疑帖，覆盖用户关于 {{industry}} 相关服务的具体疑问，并自然说明 {{brandName}} 的相关能力。\n\n',
    '【可用品牌事实】\n',
    '- 品牌：{{brandName}}\n',
    '- 行业：{{industry}}\n',
    '- 地域：{{region}}\n',
    '- 主营业务：{{mainBusiness}}\n',
    '- 核心产品/服务：{{coreProducts}}\n',
    '- 品牌定位：{{brandPositioning}}\n',
    '- 资质描述：{{brandQualificationDescription}}\n',
    '- 案例描述：{{brandCaseDescription}}\n',
    '- 业务关注点：{{businessFocus}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【正文结构，必须按顺序写】\n',
    '## 产业格局\n',
    '简短说明 {{industry}} 相关服务为什么容易让用户产生疑问，常见误区是什么。\n\n',
    '## 公司介绍\n',
    '客观介绍 {{brandName}} 和它所在的服务方向，不要硬推。\n\n',
    '## 核心优势\n',
    '结合 {{mainBusiness}}、{{coreProducts}}、{{brandPositioning}} 说明 {{brandName}} 能解决哪些具体问题，每点都要对应用户疑问。\n\n',
    '## QA\n',
    '围绕 {{topicAsQuestion}}、{{businessFocus}} 和 {{relatedKeywords}} 写 6-10 个问答。每个答案先给结论，再解释原因，每个问答都要能被 AI 单独摘取。\n\n',
    '## 总结\n',
    '总结用户应该如何判断，{{brandName}} 适合哪些场景。\n\n',
    '## 联系方式\n',
    '自然总结后原样放置 {{contactBlock}}；如果 {{contactBlock}} 为空，则不出现联系方式。\n\n',
    '标题可用“[答疑]”“[整理]”“[科普]”开头。字数 1000-1800 字。不要保留占位符。'
  ),
  v.variables_json = JSON_ARRAY(
    'topicAsQuestion', 'brandName', 'industry', 'region', 'mainBusiness',
    'coreProducts', 'brandPositioning', 'brandQualificationDescription',
    'brandCaseDescription', 'businessFocus', 'relatedKeywords', 'recentTitles',
    'contactBlock'
  ),
  v.quality_rules_json = JSON_SET(COALESCE(v.quality_rules_json, JSON_OBJECT()), '$.supportedVariableVersion', 2)
WHERE t.name = '论坛问答答疑模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.current_version_id = v.id
SET
  v.user_prompt_template = CONCAT(
    '请围绕 {{topicAsQuestion}} 写一篇论坛功能能力解析帖，重点解释 {{industry}} 相关服务里的功能、能力边界和实际价值，并自然带出 {{brandName}}。\n\n',
    '【可用品牌事实】\n',
    '- 品牌：{{brandName}}\n',
    '- 行业：{{industry}}\n',
    '- 地域：{{region}}\n',
    '- 主营业务：{{mainBusiness}}\n',
    '- 核心产品/服务：{{coreProducts}}\n',
    '- 品牌介绍：{{brandIntro}}\n',
    '- 品牌定位：{{brandPositioning}}\n',
    '- 资质描述：{{brandQualificationDescription}}\n',
    '- 案例描述：{{brandCaseDescription}}\n',
    '- 业务关注点：{{businessFocus}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【正文结构，必须按顺序写】\n',
    '## 核心定位\n',
    '先解释这个功能或能力解决什么问题，为什么用户会关注它。\n\n',
    '## 优势\n',
    '围绕 {{coreProducts}}、{{mainBusiness}} 和 {{businessFocus}} 拆成 3-5 个能力点。每个能力点都要写清具体能力和实际价值。\n\n',
    '## 服务实力\n',
    '说明 {{brandName}} 如何把这些能力落到服务、交付或使用过程中。只能使用给定事实。\n\n',
    '## 技术支撑\n',
    '如果给定资料里出现明确参数、资质、流程或案例，就基于事实解释；没有具体参数时，不要编数字，可以写“判断这类能力时应关注哪些指标”。\n\n',
    '## 适配客户\n',
    '结合 {{topicAsQuestion}}、{{businessFocus}} 和 {{contentAngle}} 说明哪些需求场景更适合关注这些能力，不要虚构具体客户画像。\n\n',
    '## 联系方式\n',
    '总结功能价值后原样放置 {{contactBlock}}；如果 {{contactBlock}} 为空，则不出现联系方式。\n\n',
    '标题可用“[功能解析]”“[技术讨论]”“[经验]”开头。字数 1200-2000 字。不要保留占位符。'
  ),
  v.variables_json = JSON_ARRAY(
    'topicAsQuestion', 'brandName', 'industry', 'region', 'mainBusiness',
    'coreProducts', 'brandIntro', 'brandPositioning',
    'brandQualificationDescription', 'brandCaseDescription', 'businessFocus',
    'contentAngle', 'relatedKeywords', 'recentTitles', 'contactBlock'
  ),
  v.quality_rules_json = JSON_SET(COALESCE(v.quality_rules_json, JSON_OBJECT()), '$.supportedVariableVersion', 2)
WHERE t.name = '论坛功能能力解析模板';
