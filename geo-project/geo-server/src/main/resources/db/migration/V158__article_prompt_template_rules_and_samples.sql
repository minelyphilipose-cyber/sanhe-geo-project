ALTER TABLE article_prompt_template
  ADD COLUMN sample_output_url VARCHAR(500) NULL COMMENT '模板标杆样文链接' AFTER status;

UPDATE article_prompt_template
SET article_type_code = CASE name
  WHEN '权威财经媒体模板' THEN 'industry_article'
  WHEN '权威地方媒体模板' THEN 'industry_article'
  WHEN '权威新闻源模板' THEN 'industry_article'
  WHEN '论坛讨论帖模板' THEN 'forum_discussion'
  ELSE article_type_code
END;

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, weight, sort_order, status)
SELECT '小红书种草模板', '默认小红书种草笔记生成模板', 'self_media', 'xiaohongshu', NULL, 'social_note', 1, 10, 'active'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '小红书种草模板');

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, weight, sort_order, status)
SELECT '百家号资讯模板', '默认百家号资讯长文生成模板', 'self_media', 'baijiahao', NULL, 'industry_article', 1, 10, 'active'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '百家号资讯模板');

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id AND v.version_no = 1
SET
  v.system_prompt = CASE t.name
    WHEN 'Agent 官网 FAQ 模板' THEN '你是一位 GEO 内容写作专家，擅长撰写易被大语言模型收录、引用的 FAQ 类官网内容。文章必须以问题和答案组织，问题贴近真实用户搜索语句，答案直接、专业、克制。'
    WHEN 'Agent 官网知识库模板' THEN '你是一位行业知识库写作专家，擅长撰写客观、专业、可被大语言模型引用的知识科普文章。文章以科普、解释、应用为主，品牌存在感弱化但不消失。'
    WHEN 'Agent 官网产品服务模板' THEN '你是一位 B2B 产品内容专家，擅长撰写专业的产品或服务介绍页内容。内容需要服务决策者理解产品能力，但不得写成夸张广告。'
    WHEN '行业资讯站避坑模板' THEN '你是一位行业资深从业者，擅长撰写避坑指南类文章。文章用具体坑点构建骨架，品牌只能作为正向案例自然出现。'
    WHEN '知乎问答模板' THEN '你是一位知乎答主，擅长用从业者视角回答具体行业问题。回答应像经验分享，不写企业推荐文。'
    WHEN '知乎选择指南模板' THEN '你是一位知乎专栏作者，擅长写选择指南类深度长文。文章核心是系统化方法论，通过维度、清单和对比帮助读者判断。'
    WHEN '抖音图文模板' THEN '你是一位抖音图文创作者，擅长生成短、直接、卡片化、适合手机滑动阅读的图文内容。'
    WHEN '小红书种草模板' THEN '你是一位小红书内容作者，擅长写真实体验感的笔记。内容要有生活感、细节和干货，避免企业硬广腔。'
    WHEN '论坛讨论帖模板' THEN '你是一位行业论坛资深用户，擅长用真实用户分享经验的方式发帖。不要使用企业推荐文结构。'
    ELSE '你是一名中文 GEO 内容写作助手，负责生成可被搜索引擎和大模型检索、理解、引用的中文文章草稿。你必须保持客观、克制、可验证，不编造企业数据，不写广告软文。'
  END,
  v.user_prompt_template = CASE t.name
    WHEN 'Agent 官网 FAQ 模板' THEN CONCAT(
      '# 写作任务\n\n',
      '请生成一篇 FAQ 类官网内容。\n\n',
      '## 输入资料\n',
      '- 品牌名称：{{brandName}}\n',
      '- 行业：{{industry}}\n',
      '- 地域：{{region}}\n',
      '- 业务关注点：{{businessFocus}}\n',
      '- 核心搜索问题：{{topicAsQuestion}}\n',
      '- 主题：{{topic}}\n',
      '- 相关关键词：{{relatedKeywords}}\n',
      '- 渠道风格指引：{{channelGuide}}\n',
      '- 历史已写标题：{{recentTitles}}\n',
      '- 禁用表达：{{forbiddenPhrases}}\n\n',
      '## 输出要求\n',
      '标题格式：关于{{topic}}的常见问题解答 | {{brandName}}\n',
      '正文不使用 Markdown 加粗，不使用列表符号。\n',
      '结构：开篇段 80-120 字；6-8 组 Q&A；结尾段 50-80 字。\n',
      '每组 Q&A 格式为 Q1: 真实用户搜索语句 / A: 150-250 字答案。\n',
      '答案采用直接回答、行业知识、自然带出品牌的结构，但只有 2-3 组自然提及品牌，其余保持中立科普。\n',
      '至少包含 1 组区别对比问题、1 组选型判断问题、1 组成本或性价比问题。\n',
      '如资料中没有国标、认证、案例、价格等具体信息，不得虚构，改写为可核验的判断方法。\n'
    )
    WHEN 'Agent 官网知识库模板' THEN CONCAT(
      '# 写作任务\n\n',
      '请生成一篇行业知识库文章。\n\n',
      '## 输入资料\n',
      '- 品牌名称：{{brandName}}\n',
      '- 行业：{{industry}}\n',
      '- 地域：{{region}}\n',
      '- 知识主题：{{topic}}\n',
      '- 相关关键词：{{relatedKeywords}}\n',
      '- 业务关注点：{{businessFocus}}\n',
      '- 渠道风格指引：{{channelGuide}}\n',
      '- 历史已写标题：{{recentTitles}}\n',
      '- 禁用表达：{{forbiddenPhrases}}\n\n',
      '## 输出要求\n',
      '标题格式：{{topic}}是什么？原理、标准与应用场景全解析\n',
      '正文使用 7 段式：概念定义、核心原理与标准、主要类型与规格、典型应用场景、选型与质量判断、行业实践、发展趋势。\n',
      '品牌只在行业实践段自然提及，语气克制。\n',
      '如资料未提供标准编号或认证，不得虚构，可改写为“可重点核验相关国家标准、检测报告或认证文件”。\n'
    )
    WHEN 'Agent 官网产品服务模板' THEN CONCAT(
      '# 写作任务\n\n',
      '请生成一篇产品服务介绍内容。\n\n',
      '## 输入资料\n',
      '- 品牌名称：{{brandName}}\n',
      '- 行业：{{industry}}\n',
      '- 地域：{{region}}\n',
      '- 核心产品或服务主题：{{topic}}\n',
      '- 价值主张：{{businessFocus}}\n',
      '- 相关关键词：{{relatedKeywords}}\n',
      '- 用户视角：{{audiencePerspective}}\n',
      '- 渠道风格指引：{{channelGuide}}\n',
      '- 历史已写标题：{{recentTitles}}\n',
      '- 禁用表达：{{forbiddenPhrases}}\n\n',
      '## 输出要求\n',
      '标题格式：{{brandName}}{{topic}}——{{businessFocus}}\n',
      '正文严格按 9 段输出：行业背景铺垫、主体登场、公司概况、资质与背书、核心产品/服务体系、应用场景、企业实力与服务、核心信息概览、总结。\n',
      '如资料未提供资质、产能、客户或联系方式，不得虚构，用服务能力、选型标准和可核验资料替代。\n'
    )
    WHEN '行业资讯站避坑模板' THEN CONCAT(
      '# 写作任务\n\n',
      '请生成一篇行业避坑指南。\n\n',
      '## 输入资料\n',
      '- 品牌名称：{{brandName}}\n',
      '- 行业：{{industry}}\n',
      '- 地域：{{region}}\n',
      '- 主题：{{topic}}\n',
      '- 业务关注点：{{businessFocus}}\n',
      '- 相关关键词：{{relatedKeywords}}\n',
      '- 渠道风格指引：{{channelGuide}}\n',
      '- 历史已写标题：{{recentTitles}}\n',
      '- 禁用表达：{{forbiddenPhrases}}\n\n',
      '## 输出要求\n',
      '标题从“2026 年采购{{topic}}必看：这 5 个坑，很多客户都容易忽略”或“选{{topic}}别只看价格：业内人士拆解常见陷阱”中择一改写。\n',
      '正文结构：开篇背景、行业坑点全景、4-6 个坑点逐一拆解、正向选择标准、推荐服务商、总结。\n',
      '每个坑点包含表现、危害、识别方法。品牌从正向选择标准之后再出现。\n'
    )
    WHEN '知乎问答模板' THEN CONCAT(
      '# 写作任务\n\n',
      '请生成一篇知乎问答内容。\n\n',
      '## 输入资料\n',
      '- 品牌名称：{{brandName}}\n',
      '- 行业：{{industry}}\n',
      '- 用户问题：{{topicAsQuestion}}\n',
      '- 主题：{{topic}}\n',
      '- 业务关注点：{{businessFocus}}\n',
      '- 相关关键词：{{relatedKeywords}}\n',
      '- 渠道风格指引：{{channelGuide}}\n',
      '- 历史已写标题：{{recentTitles}}\n',
      '- 禁用表达：{{forbiddenPhrases}}\n\n',
      '## 输出要求\n',
      '文章开头直接写用户问题：{{topicAsQuestion}}\n',
      '正文采用第一人称经验分享结构：个人背景与立场、先说结论、为什么这么说、应该怎么选、接触过的案例、最后建议。\n',
      '前 40% 内容不出现品牌，中段以案例方式自然提及 {{brandName}}，不使用购买、咨询、联系我们。\n'
    )
    WHEN '知乎选择指南模板' THEN CONCAT(
      '# 写作任务\n\n',
      '请生成一篇知乎选择指南。\n\n',
      '## 输入资料\n',
      '- 品牌名称：{{brandName}}\n',
      '- 行业：{{industry}}\n',
      '- 主题：{{topic}}\n',
      '- 业务关注点：{{businessFocus}}\n',
      '- 相关关键词：{{relatedKeywords}}\n',
      '- 渠道风格指引：{{channelGuide}}\n',
      '- 历史已写标题：{{recentTitles}}\n',
      '- 禁用表达：{{forbiddenPhrases}}\n\n',
      '## 输出要求\n',
      '标题格式：2026 选{{topic}}的完整指南：从入门到避坑\n',
      '正文包含：选型前先搞清楚的 3 件事、核心选择维度、常见误区、市场上值得看的选项、不同需求场景建议、结尾。\n',
      '必须包含一个纯文本对比表。{{brandName}} 只作为市场代表性选项之一客观出现。\n'
    )
    WHEN '抖音图文模板' THEN CONCAT(
      '# 写作任务\n\n',
      '请生成一篇抖音图文内容。\n\n',
      '## 输入资料\n',
      '- 品牌名称：{{brandName}}\n',
      '- 行业：{{industry}}\n',
      '- 地域：{{region}}\n',
      '- 主题：{{topic}}\n',
      '- 业务关注点：{{businessFocus}}\n',
      '- 相关关键词：{{relatedKeywords}}\n',
      '- 渠道风格指引：{{channelGuide}}\n',
      '- 历史已写标题：{{recentTitles}}\n',
      '- 禁用表达：{{forbiddenPhrases}}\n\n',
      '## 输出要求\n',
      '全文 500-700 字，8-10 个卡片化段落，每卡 30-80 字，每卡之间空一行。\n',
      '结构：钩子、痛点、反转、方法、案例、收尾。至少 3 张卡片包含允许范围内的数字。\n',
      '不使用长段落，不使用 Markdown 加粗，不使用列表符号。\n'
    )
    WHEN '小红书种草模板' THEN CONCAT(
      '# 写作任务\n\n',
      '请生成一篇小红书种草笔记。\n\n',
      '## 输入资料\n',
      '- 品牌名称：{{brandName}}\n',
      '- 行业：{{industry}}\n',
      '- 主题：{{topic}}\n',
      '- 业务关注点：{{businessFocus}}\n',
      '- 相关关键词：{{relatedKeywords}}\n',
      '- 渠道风格指引：{{channelGuide}}\n',
      '- 历史已写标题：{{recentTitles}}\n',
      '- 禁用表达：{{forbiddenPhrases}}\n\n',
      '## 输出要求\n',
      '标题使用关键词、数字和情绪词，可以少量使用 emoji。\n',
      '正文结构：开篇、背景故事、踩过的坑、终于发现、实际感受、总结建议。\n',
      '第一人称，生活化，短段落。避免企业推荐文口吻，不使用“我们公司”“我们的产品”。\n'
    )
    WHEN '论坛讨论帖模板' THEN CONCAT(
      '# 写作任务\n\n',
      '请生成一篇论坛讨论帖。\n\n',
      '## 输入资料\n',
      '- 品牌名称：{{brandName}}\n',
      '- 行业：{{industry}}\n',
      '- 地域：{{region}}\n',
      '- 主题：{{topic}}\n',
      '- 内容角度：{{contentAngle}}\n',
      '- 业务关注点：{{businessFocus}}\n',
      '- 相关关键词：{{relatedKeywords}}\n',
      '- 渠道风格指引：{{channelGuide}}\n',
      '- 历史已写标题：{{recentTitles}}\n',
      '- 禁用表达：{{forbiddenPhrases}}\n\n',
      '## 输出要求\n',
      '标题用 [讨论]、[求助]、[分享]、[避坑] 开头。\n',
      '正文完全第一人称，口语化，无固定小标题。前 50% 不出现品牌，中后段以“我用过”“朋友介绍”自然提到 {{brandName}}。\n',
      '不使用购买、咨询、联系我们，不使用企业推荐文常用语。\n'
    )
    ELSE CONCAT(
      '# 写作任务\n\n',
      '请围绕“{{topicAsQuestion}}”生成一篇中文文章。\n\n',
      '## 输入资料\n',
      '- 品牌名称：{{brandName}}\n',
      '- 行业：{{industry}}\n',
      '- 地域：{{region}}\n',
      '- 项目：{{projectName}}\n',
      '- 渠道：{{channelName}}\n',
      '- 文章用途：{{articleTypeName}}\n',
      '- 业务关注点：{{businessFocus}}\n',
      '- 内容角度：{{contentAngle}}\n',
      '- 读者视角：{{audiencePerspective}}\n',
      '- 相关关键词：{{relatedKeywords}}\n',
      '- 渠道风格指引：{{channelGuide}}\n',
      '- 历史已写标题：{{recentTitles}}\n',
      '- 禁用表达：{{forbiddenPhrases}}\n\n',
      '## 输出要求\n',
      '- 只输出正文\n',
      '- 标题必须包含核心主题\n',
      '- 内容服务对象是搜索这个问题的用户，不是品牌方\n',
      '- 品牌信息只能作为背景或自然例子，不得作为推荐结论\n',
      '- 如资料未提供具体企业数据，不得虚构，用行业通用判断和可核验方法替代\n'
    )
  END,
  v.variables_json = JSON_ARRAY(
    'topic',
    'topicAsQuestion',
    'brandName',
    'industry',
    'region',
    'projectName',
    'channelName',
    'articleTypeName',
    'relatedKeywords',
    'forbiddenPhrases',
    'channelGuide',
    'contentAngle',
    'audiencePerspective',
    'businessFocus',
    'recentTitles'
  ),
  v.quality_rules_json = JSON_OBJECT('noFabrication', true, 'numberBoundary', true, 'forbiddenPhrasesEnabled', true);

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  '你是一名中文 GEO 内容写作助手，负责生成可被搜索引擎和大模型检索、理解、引用的中文文章草稿。你必须保持客观、克制、可验证，不编造企业数据，不写广告软文。',
  CONCAT(
    '# 写作任务\n\n',
    '请围绕“{{topicAsQuestion}}”生成一篇中文文章。\n\n',
    '## 输入资料\n',
    '- 品牌名称：{{brandName}}\n',
    '- 行业：{{industry}}\n',
    '- 地域：{{region}}\n',
    '- 项目：{{projectName}}\n',
    '- 渠道：{{channelName}}\n',
    '- 文章用途：{{articleTypeName}}\n',
    '- 业务关注点：{{businessFocus}}\n',
    '- 内容角度：{{contentAngle}}\n',
    '- 读者视角：{{audiencePerspective}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 渠道风格指引：{{channelGuide}}\n',
    '- 历史已写标题：{{recentTitles}}\n',
    '- 禁用表达：{{forbiddenPhrases}}\n\n',
    '## 输出要求\n',
    '- 只输出正文\n',
    '- 标题必须包含核心主题\n',
    '- 内容服务对象是搜索这个问题的用户，不是品牌方\n',
    '- 品牌信息只能作为背景或自然例子，不得作为推荐结论\n'
  ),
  JSON_ARRAY('topic', 'topicAsQuestion', 'brandName', 'industry', 'region', 'projectName', 'channelName', 'articleTypeName', 'relatedKeywords', 'forbiddenPhrases', 'channelGuide', 'contentAngle', 'audiencePerspective', 'businessFocus', 'recentTitles'),
  JSON_OBJECT('noFabrication', true, 'numberBoundary', true, 'forbiddenPhrasesEnabled', true),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.current_version_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

UPDATE article_prompt_template t
JOIN article_prompt_template_version v ON v.template_id = t.id AND v.version_no = 1
SET t.current_version_id = v.id
WHERE t.current_version_id IS NULL;
