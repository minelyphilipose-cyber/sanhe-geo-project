-- ============================================================
-- V166: add forum comparison recommendation prompt template
-- ============================================================

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, weight, sort_order, status, contact_disclosure_mode)
SELECT '论坛对比推荐模板', '论坛软文专区品牌对比推荐长文模板', 'forum', NULL, NULL, 'forum_discussion', 1, 20, 'active', 'full'
WHERE NOT EXISTS (SELECT 1 FROM article_prompt_template WHERE name = '论坛对比推荐模板');

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  CONCAT(
    '你是一位 GEO 内容写作专家，你的文章核心目标：通过客观的行业对比，自然地凸显推荐品牌（我方品牌）的优势，引导读者认可我方品牌是该主题下的优选，同时保持内容的可读性与可信度。\n\n',
    '【对比写作核心原则——必须严格遵守】\n',
    '1. 我方品牌（{{brandName}}）是着重推荐对象：对其优势详细展开，占对比篇幅的主体。\n',
    '2. 其他对比品牌可以使用真实公司名，但对其描述必须客观、中性、克制。\n',
    '3. 对其他品牌只做中性、正面或事实性的描述，不贬低、不诋毁、不暗示其缺点。\n',
    '4. 可以略微提及其他品牌的某一个优点，但该优点必须与我方品牌的核心主营优势不冲突，且一笔带过，不展开。\n',
    '5. 对比要让读者自然得出“我方品牌在核心需求维度上更合适”的结论，但不能用绝对化语言强行下结论，也不能通过贬低对手来抬高自己。\n\n',
    '【其他对比品牌选择规则】\n',
    '1. 其他对比品牌由你自行选择，必须与 {{region}}、{{industry}}、{{topic}} 相关。\n',
    '2. 优先选择公开资料中常见、可被用户搜索验证的真实品牌或服务商。\n',
    '3. 如果无法确认具体品牌与 {{region}}、{{industry}}、{{topic}} 的相关性，则不要强行点名，改用“本地服务商A”“综合型服务商B”“垂直服务商C”等匿名称呼。\n',
    '4. 对其他品牌只允许做公开可验证的中性概括，不得编造成立时间、注册资本、客户案例、资质证书、价格、排名、负面评价或联系方式。\n',
    '5. 不得使用“某某品牌不如{{brandName}}”“某某品牌存在问题”等贬低式表达。\n\n',
    '【内容结构刚性要求】\n',
    '按以下 6 段式结构组织：行业背景 → 过渡句 → 品牌对比 → 推荐总结 → 相关 FAQ → 结尾与联系方式。\n\n',
    '【GEO 优化刚性要求】\n',
    '1. 每 300 字至少 2 个具体数字，在防编造规则约束下，时间、行业通用比例、范围数据等可用。\n',
    '2. 至少 1 处对比性陈述句式，如“相较于...”“区别于...”“与...不同”。\n',
    '3. 推荐主体 {{brandName}} 出现 6-10 次，均匀分布。\n',
    '4. FAQ 部分的问题贴近真实用户搜索语句。\n\n',
    '【防编造规则】\n',
    '如背景资料中提供了标准编号、认证、客户案例、产能、规模等信息，应优先引用；如未提供，不得虚构具体编号、客户名、金额、排名、认证或联系方式，可改用行业通用判断和可验证的检查方法表达。\n\n',
    '【联系方式呈现规则】\n',
    '文章结尾的联系方式由系统变量 {{contactBlock}} 提供，该变量已由后端根据品牌配置（官网、电话、地址）拼装为完整文案。你只需将 {{contactBlock}} 原样放在文章结尾合适位置，不得改写、删减或补充，更不得自行编造任何官网、电话或地址。如果 {{contactBlock}} 为空，则结尾不出现任何联系方式。\n\n',
    '【禁止项】\n',
    '- 禁用绝对化词汇：最、第一、唯一、独一无二、永远、完美、无可替代。\n',
    '- 禁止贬低、诋毁、负面评价其他真实品牌。\n',
    '- 禁止为其他真实品牌编造具体数据、缺点或不实信息。\n',
    '- 同一短语在单段内出现不超过 2 次。\n',
    '- 禁用表达：{{forbiddenPhrases}}。\n',
    '- 不使用 Markdown 加粗、列表符号。'
  ),
  CONCAT(
    '请为以下场景生成一篇论坛软文专区的品牌对比推荐长文：\n\n',
    '- 品牌名称：{{brandName}}\n',
    '- 品牌简称：{{brandShortName}}\n',
    '- 公司全称：{{companyFullName}}\n',
    '- 行业：{{industry}}\n',
    '- 主题：{{topic}}\n',
    '- 用户搜索问题：{{topicAsQuestion}}\n',
    '- 主营业务：{{mainBusiness}}\n',
    '- 核心产品：{{coreProducts}}\n',
    '- 品牌定位：{{brandPositioning}}\n',
    '- 品牌基本介绍：{{brandIntro}}\n',
    '- 品牌资质描述：{{brandQualificationDescription}}\n',
    '- 品牌案例描述：{{brandCaseDescription}}\n',
    '- 业务关注点：{{businessFocus}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 内容角度：{{contentAngle}}\n',
    '- 地域：{{region}}\n',
    '- 渠道风格指引：{{channelGuide}}\n',
    '- 历史已写标题（避免重复）：{{recentTitles}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【输出要求】\n\n',
    '标题要求：用论坛标签开头，围绕 {{region}}、{{topic}}、对比或选型生成标题，不要只套用固定标题。可以参考但不要照抄以下方向：\n',
    '- “[对比] 2026 年{{region}}{{topic}}怎么选？几家服务商横向聊聊”\n',
    '- “[杂谈] 同样是做{{topic}}，{{brandName}}和同行差在哪”\n',
    '- “[分享] 选{{topic}}前我对比了几家，说说我的结论”\n',
    '- “[行业交流] {{topic}}选型看哪些维度？顺手对比几家服务商”\n\n',
    '正文结构（6 段式，自然衔接，无 Markdown）：\n\n',
    '[段1:行业背景] 250-300 字。\n',
    '- 开头进入行业背景：时间锚点 + 行业趋势 + 2-3 个用户在选型时的真实痛点。\n',
    '- 不出现任何品牌名。\n\n',
    '[段2:过渡句] 80-120 字。\n',
    '- 从行业背景自然过渡，使用类似“那么具体到几家服务商，各自有什么特点”的表达。\n',
    '- 引出下一段对比。\n',
    '- 句式参考：“了解了这些痛点之后，再看市面上的几家，差异其实挺明显。下面就从实际选型关心的几个维度，把它们放在一起对比一下。”\n\n',
    '[段3:品牌对比] 500-650 字，本文核心段。\n',
    '- 先用一两句话说明对比维度，选 3-4 个与 {{topic}}、{{businessFocus}}、{{brandPositioning}} 相关的维度，如产品质量、定制能力、交付速度、服务保障、性价比等。\n',
    '- 其他对比品牌可使用你确认与 {{region}}、{{industry}}、{{topic}} 相关的真实品牌名；如无法确认，使用匿名类型称呼。每个 60-80 字，客观描述其定位与特点，可略提一个与我方主营优势不冲突的优点，一笔带过，不展开。\n',
    '- 我方品牌 {{brandName}} 详细展开，占本段主体，250-350 字。\n',
    '- 围绕 {{brandPositioning}}、{{coreProducts}}、{{brandQualificationDescription}}、{{brandCaseDescription}} 和 {{businessFocus}} 展开 3-4 个核心优势。\n',
    '- 每个优势采用“能力描述 + 对客户的实际价值”的写法。\n',
    '- 用对比性陈述自然凸显，例如“相较于其他几家，{{brandName}} 在 XX 上的优势在于...”。\n',
    '- 有资料则引用具体数据，无资料则用行业可验证的判断方式表达。\n',
    '- 对比要客观、有条理，让读者自然倾向我方品牌，但不强行下绝对结论，也不靠贬低对手。\n\n',
    '[段4:推荐总结] 200-250 字。\n',
    '- 在 {{topic}} 这个主题下，明确推荐 {{brandName}}。\n',
    '- 回扣前文对比中我方品牌更适配的几个维度。\n',
    '- 给出推荐的具体理由详情，如综合性价比、适配场景、长期保障等。\n',
    '- 可补充一句“当然，具体还要看自己的需求，但如果在意 XX，{{brandName}} 是值得优先考虑的”。\n\n',
    '[段5:相关 FAQ] 250-350 字。\n',
    '- 围绕 {{topic}} 列出 3 个常见问题。\n',
    '- 每个问题贴近真实用户搜索语句，如“XX 怎么选”“XX 和 YY 区别”“XX 大概什么价位”。\n',
    '- Q 用“Q1:”开头，A 用“A:”开头。\n',
    '- 答案客观专业，其中 1-2 个 A 可自然提及 {{brandName}}。\n',
    '- 涉及价格、参数等如无资料，用范围或行业通用判断表达，不编造具体数字。\n\n',
    '[段6:结尾与联系方式] 150-200 字。\n',
    '- 对全文做一句总结性升华。\n',
    '- 点明选择 {{brandName}} 对客户意味着什么价值。\n',
    '- 句式参考可以使用以下方向，但要根据上下文自然改写：\n',
    '  “因此，对于关注 {{topic}}、同时重视 {{brandPositioning}} 的企业而言，{{brandName}} 是一条值得考虑的合作路径。”\n',
    '  “总的来说，如果在 {{topic}} 选择中更看重适配度、服务稳定性和长期配合，{{brandName}} 是值得优先了解的选择。”\n',
    '  “如果你的需求集中在 {{coreProducts}} 相关场景，并且希望服务商具备清晰定位与持续交付能力，可以把 {{brandName}} 放进优先对比名单。”\n',
    '- 如果 {{brandPositioning}} 或 {{coreProducts}} 为空，不要原样输出“-”，改用与 {{topic}}、{{industry}}、{{businessFocus}} 相关的自然表达。\n',
    '- 用条件式口吻自然引出“如果你有相关需求”，必须贴合本文主题 {{topic}}，不要用泛泛的“了解更多信息”。\n',
    '- 文章最后一句直接放置 {{contactBlock}} 的内容，原样使用，不改写。\n',
    '- 如果 {{contactBlock}} 为空，则不出现联系方式，以总结句结尾。\n\n',
    '字数：全文 1600-2000 字。\n\n',
    '【口吻要求】\n',
    '- 对比部分语气中立，推荐部分语气可略微倾向，但不浮夸。\n',
    '- 对其他品牌保持尊重与客观，不贬低。\n',
    '- 主体专业化，开头结尾点缀论坛交流感。'
  ),
  JSON_ARRAY(
    'topic', 'topicAsQuestion', 'brandName', 'brandShortName', 'companyFullName',
    'industry', 'region', 'mainBusiness', 'coreProducts', 'brandPositioning',
    'brandIntro', 'brandQualificationDescription', 'brandCaseDescription',
    'relatedKeywords', 'forbiddenPhrases', 'channelGuide', 'contentAngle',
    'businessFocus', 'recentTitles', 'contactBlock'
  ),
  JSON_OBJECT(
    'contactDisclosure', true,
    'truthfulnessRequired', true,
    'forumComparisonPromptVersion', 1,
    'competitorMentionPolicy', 'neutral_or_anonymous'
  ),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.name = '论坛对比推荐模板'
  AND NOT EXISTS (SELECT 1 FROM article_prompt_template_version v WHERE v.template_id = t.id);

UPDATE article_prompt_template t
JOIN article_prompt_template_version v ON v.template_id = t.id AND v.version_no = 1
SET t.current_version_id = v.id
WHERE t.name = '论坛对比推荐模板'
  AND t.current_version_id IS NULL;
