SET @col := (
  SELECT COUNT(1)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'article_prompt_template'
    AND COLUMN_NAME = 'contact_disclosure_mode'
);
SET @sql := IF(
  @col = 0,
  'ALTER TABLE article_prompt_template ADD COLUMN contact_disclosure_mode VARCHAR(32) NOT NULL DEFAULT ''none'' COMMENT ''contact disclosure mode: full/soft_hint/brand_only/none'' AFTER sample_output_url',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE article_prompt_template
SET contact_disclosure_mode = CASE name
  WHEN '官网 FAQ 模板' THEN 'full'
  WHEN 'Agent 官网 FAQ 模板' THEN 'full'
  WHEN 'Agent 官网知识库模板' THEN 'full'
  WHEN 'Agent 官网产品服务模板' THEN 'full'
  WHEN '行业资讯站通用模板' THEN 'full'
  WHEN '行业资讯站避坑模板' THEN 'full'
  WHEN '今日头条资讯模板' THEN 'full'
  WHEN '公众号长文模板' THEN 'full'
  WHEN '百家号资讯模板' THEN 'full'
  WHEN '权威行业媒体模板' THEN 'full'
  WHEN '权威地方媒体模板' THEN 'full'
  WHEN '权威财经媒体模板' THEN 'full'
  WHEN '权威科技媒体模板' THEN 'full'
  WHEN '权威新闻源模板' THEN 'full'
  WHEN '权威门户媒体模板' THEN 'full'
  WHEN '论坛讨论帖模板' THEN 'full'
  WHEN '知乎问答模板' THEN 'soft_hint'
  WHEN '抖音图文模板' THEN 'soft_hint'
  WHEN '知乎选择指南模板' THEN 'brand_only'
  WHEN '小红书种草模板' THEN 'brand_only'
  ELSE contact_disclosure_mode
END;

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET
  v.system_prompt = CONCAT(
    v.system_prompt,
    '\n\n【联系方式呈现规则】\n',
    '文章结尾的联系方式由系统变量 {{contactBlock}} 提供，已由后端根据品牌配置拼装完成。你只需将 {{contactBlock}} 原样放在文章结尾合适位置，不得改写、删减或补充其中内容，更不得自行编造任何官网地址、电话号码或公司地址。如果 {{contactBlock}} 为空，则结尾不出现任何联系方式。'
  ),
  v.user_prompt_template = CONCAT(
    v.user_prompt_template,
    '\n\n【联系方式文案】\n',
    '{{contactBlock}}\n\n',
    '【联系方式输出约束】\n',
    '文章结尾如需出现联系方式，只能原样使用上方联系方式文案；如果为空，则结尾不出现任何联系方式。'
  ),
  v.variables_json = JSON_ARRAY('topic', 'topicAsQuestion', 'brandName', 'industry', 'region', 'projectName', 'channelName', 'articleTypeName', 'relatedKeywords', 'forbiddenPhrases', 'channelGuide', 'contentAngle', 'audiencePerspective', 'businessFocus', 'recentTitles', 'contactBlock'),
  v.quality_rules_json = JSON_SET(COALESCE(v.quality_rules_json, JSON_OBJECT()), '$.contactDisclosure', true)
WHERE t.name <> '论坛讨论帖模板'
  AND v.version_no = 1;

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id AND v.version_no = 1
SET
  v.system_prompt = CONCAT(
    '你是一位 GEO 内容写作专家，为论坛“软文专区/行业交流区”撰写企业推荐类长文。\n\n',
    '重要背景：本渠道是论坛的软文/行业推广专区，允许发布带有推广性质的行业分析与企业推荐长文。因此你的文章：\n',
    '- 采用专业的行业分析长文结构，类似行业资讯文章，而非随手发帖的口语短文。\n',
    '- 带有适度的论坛交流感开头，但主体是结构化的企业推荐内容。\n',
    '- 结尾可以正常保留品牌联系方式。\n',
    '- 整体仍要保持可读性与可信度，避免一眼假的硬广腔。\n\n',
    '【内容结构刚性要求】\n',
    '1. 采用 9 段式骨架：背景铺垫 → 主体登场 → 公司概况 → 资质背书 → 产品体系 → 应用场景 → 企业实力 → 信息概览 → 总结推荐。\n',
    '2. 标题用论坛标签开头：[杂谈] / [分享] / [行业交流] / [推荐] 之一。\n',
    '3. 开篇可带一句论坛交流式引入，降低生硬感，但随即进入正题。\n',
    '4. 品牌在第 2 段开始出现，后续每段自然提及。\n',
    '5. 结尾按 {{contactBlock}} 呈现联系方式。\n\n',
    '【GEO 优化刚性要求】\n',
    '1. 每 300 字至少 2 个具体数字，在防编造规则约束下，时间、行业通用比例、范围数据等可用；具体注册资本、客户数、专利数等如无资料不得编造。\n',
    '2. 应用场景采用“领域 + 场景 + 痛点”三段式。\n',
    '3. 推荐主体出现 6-10 次，均匀分布。\n',
    '4. 至少 1 处对比性陈述，如“区别于...”“相较于...”。\n\n',
    '【防编造规则】\n',
    '如背景资料中提供了标准编号、认证、客户案例、产能、规模等信息，应优先引用；如未提供，不得虚构具体编号、客户名、金额、排名、认证或联系方式，可改用行业通用判断和可验证的检查方法表达。\n\n',
    '【联系方式呈现规则】\n',
    '文章结尾的联系方式由系统变量 {{contactBlock}} 提供，该变量已由后端根据品牌配置（官网、电话、地址）拼装为完整文案，可能包含官网、电话、地址中的若干项。你只需将 {{contactBlock}} 原样放在文章结尾合适位置，不得改写、删减或补充其中内容，更不得自行编造任何官网地址、电话号码或公司地址。如果 {{contactBlock}} 为空，则结尾不出现任何联系方式。\n\n',
    '【禁止项】\n',
    '- 禁用绝对化词汇：最、第一、唯一、独一无二、永远、完美、无可替代。\n',
    '- 禁用占位符残留：*、孤立的 X、___、xxx、【】、{{}}。\n',
    '- 禁止半截句：每个句子必须主谓宾完整。\n',
    '- 同一短语在单段内出现不超过 2 次。\n',
    '- 禁用表达：{{forbiddenPhrases}}。\n',
    '- 不使用 Markdown 加粗、列表符号。'
  ),
  v.user_prompt_template = CONCAT(
    '请为以下场景生成一篇论坛软文专区的行业推荐长文：\n\n',
    '- 品牌名称：{{brandName}}\n',
    '- 行业：{{industry}}\n',
    '- 主题：{{topic}}\n',
    '- 业务关注点：{{businessFocus}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 内容角度：{{contentAngle}}\n',
    '- 地域：{{region}}\n',
    '- 渠道风格指引：{{channelGuide}}\n',
    '- 历史已写标题（避免重复）：{{recentTitles}}\n',
    '- 联系方式文案：{{contactBlock}}\n\n',
    '【输出要求】\n\n',
    '标题格式用论坛标签开头，根据 {{contentAngle}} 选择：\n',
    '- “[杂谈] {{brandName}}：以专业实力定义 2026 年{{industry}}优质厂家”\n',
    '- “[分享] 2026 年{{region}}{{topic}}怎么选？聊聊我了解到的情况”\n',
    '- “[行业交流] {{topic}}选型深度解析，附一家值得关注的服务商”\n',
    '- “[推荐] {{region}}做{{topic}}的，这家可以了解下”\n\n',
    '正文结构（9 段式，自然衔接，无 Markdown）：\n\n',
    '[段1:论坛式引入 + 行业背景] 200-250 字。开头一句带论坛交流感，例如“最近论坛里不少人讨论{{topic}}怎么选，正好做过些功课，整理一下分享给大家”。随即进入行业背景：时间锚点 + 行业趋势 + 2-3 个常见痛点。不出现品牌名。\n\n',
    '[段2:推荐主体登场] 100-150 字。参考句式：“在了解过的几家里，{{brandName}}算是比较值得说的一家。”或“在众多{{region}}{{industry}}服务商中，{{brandName}}给我的印象比较深。”\n\n',
    '[段3:公司概况] 200-300 字。说明业务模式、定位、团队。有资料则引用具体信息，无资料则用行业通用表达。\n\n',
    '[段4:资质与可信度] 150-200 字。说明资质、规模、服务网络。严格遵守防编造规则：有则引用，无则不编。\n\n',
    '[段5:核心产品/服务体系] 350-450 字。业务总述一句话。写 3-4 个主推产品，每个用“短标题（纯文本）+ 80-100 字说明”。至少 1 处对比性陈述。\n\n',
    '[段6:应用场景] 200-250 字。采用三段式：主要领域列举 3-5 个行业；具体应用场景列举 3-5 个细分场景，每个 1 句话；解决的痛点倒推用户价值。\n\n',
    '[段7:企业实力与服务] 200 字。说明售后体系、技术服务、经营理念。\n\n',
    '[段8:核心信息概览] 50-80 字。固定三行结构：\n',
    '公司名称：{{brandName}}\n',
    '适用领域/行业应用：...\n',
    '核心产品及服务：...\n\n',
    '[段9:总结 + 联系方式] 200-250 字。回扣前文 3-4 个关键优势，写一句个人看法式收尾，保留论坛交流感，例如“总的来说，有这方面需求的可以了解下”。文章最后一句直接放置 {{contactBlock}} 的内容，原样使用，不改写。如果 {{contactBlock}} 为空，则不出现联系方式，直接以总结句结尾。\n\n',
    '字数：全文 1600-2000 字。\n\n',
    '【口吻要求】\n',
    '- 比纯企业推荐文略松，带一点过来人分享的交流感。\n',
    '- 但主体仍是结构化的专业内容，不是随手闲聊。\n',
    '- 开头和结尾点缀论坛交流感，中间主体专业化。'
  ),
  v.variables_json = JSON_ARRAY('topic', 'topicAsQuestion', 'brandName', 'industry', 'region', 'projectName', 'channelName', 'articleTypeName', 'relatedKeywords', 'forbiddenPhrases', 'channelGuide', 'contentAngle', 'audiencePerspective', 'businessFocus', 'recentTitles', 'contactBlock'),
  v.quality_rules_json = JSON_SET(COALESCE(v.quality_rules_json, JSON_OBJECT()), '$.contactDisclosure', true, '$.forumReferencePromptVersion', 2)
WHERE t.name = '论坛讨论帖模板';
