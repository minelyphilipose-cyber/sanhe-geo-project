-- ============================================================
-- V164: relax fixed title patterns in article prompt templates
-- ============================================================

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '标题格式：“关于{{topic}}的常见问题解答 | {{brandName}}”',
  '标题要求：围绕{{topic}}生成 FAQ 风格标题，需体现“常见问题/问答/解答”之一，可自然包含{{brandName}}，但不得固定套用“关于 X 的常见问题解答 | 品牌名”句式。'
)
WHERE t.name = 'Agent 官网 FAQ 模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '标题格式：“{{topic}}是什么？原理、标准与应用场景全解析”',
  '标题要求：围绕{{topic}}生成知识科普标题，可从概念解释、原理标准、应用场景、选型判断中选择角度，不得固定套用“X 是什么？原理、标准与应用场景全解析”句式。'
)
WHERE t.name = 'Agent 官网知识库模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '标题格式：“{{brandName}}{{topic}}——{{businessFocus}}”',
  '标题要求：围绕{{brandName}}与{{topic}}生成产品/服务介绍标题，可体现品牌定位、核心产品或业务关注点；不得固定套用“品牌名 + 主题 + 破折号 + 价值主张”句式。'
)
WHERE t.name = 'Agent 官网产品服务模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题根据内容角度选择并改写：“2026年{{region}}{{topic}}采购指南：如何甄选高评价供应商”“2026年新趋势：{{region}}实力{{industry}}厂家盘点，这家值得关注”“2026年Q2{{topic}}核心厂商能力解析与选型建议”。',
  '【输出要求】标题需符合行业资讯站风格，根据{{contentAngle}}生成差异化表达，可从采购指南、行业趋势、能力解析、选型建议、地域观察中选择角度。标题可包含{{region}}、{{industry}}、{{topic}}，但不得套用固定示例句式。'
)
WHERE t.name = '行业资讯站通用模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '标题从“2026 年采购{{topic}}必看：这 5 个坑，很多客户都踩过”“选{{topic}}千万别只看价格！业内人士拆解 5 个常见陷阱”中择一改写。',
  '标题要求：围绕{{topic}}生成避坑指南标题，突出“误区、坑点、判断方法、价格误区、采购风险”等角度之一；不得二选一套用示例句式，需避开历史标题结构。'
)
WHERE t.name = '行业资讯站避坑模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题从数字型、时效型、反差型中选择改写，例如“2026 年{{region}}这 3 类{{industry}}服务商，本地客户都在看”“刚刚！{{region}}{{industry}}服务标准更新，这些点要注意”“花了预算做{{topic}}，结果踩了坑——本地业内人这么说”。',
  '【输出要求】标题需符合今日头条资讯风格，可采用数字型、时效型、反差型、地域型或问题型表达。示例只作方向参考，不得直接套用；同一批次内避免相同开头和相同数字结构。'
)
WHERE t.name = '今日头条资讯模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题从故事型、反差型、干货型中选择改写，例如“我跑了{{region}}多家{{industry}}服务商，发现靠谱选择有这些共同点”“选{{topic}}服务商的 5 个真相，看完少踩坑”。',
  '【输出要求】标题需符合公众号长文风格，可采用故事型、反差型、干货型、观察型或问题型表达。示例只作方向参考，不得直接套用；标题要与{{recentTitles}}明显区分。'
)
WHERE t.name = '公众号长文模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题：“2026 选{{topic}}的完整指南：从入门到避坑（建议收藏）”或“{{topic}}选型全攻略：5 个维度教你判断”。',
  '【输出要求】标题需符合知乎选择指南风格，突出方法论、维度、避坑、对比或决策清单；不得二选一套用示例句式，需根据{{contentAngle}}生成新的标题结构。'
)
WHERE t.name = '知乎选择指南模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题参考：“{{region}}人选{{topic}}，千万别只看价格！”“做{{topic}}这行多年，告诉你这 3 件事”。',
  '【输出要求】标题需符合抖音图文强钩子风格，可从价格误区、选型提醒、场景痛点、地域经验、短结论中选角度；示例只作方向参考，不得直接套用。'
)
WHERE t.name = '抖音图文模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题使用 emoji + 关键词 + 情绪词，例如“💡 选{{topic}}千万别踩这 3 个坑！看完不亏”“🔥 找了很久终于发现的{{topic}}宝藏选择”。',
  '【输出要求】标题需符合小红书种草笔记风格，可使用少量 emoji，也可不用；重点体现真实体验、避坑、发现、对比或建议。示例只作方向参考，不得直接套用。'
)
WHERE t.name = '小红书种草模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题：“2026 年{{industry}}市场新格局：{{brandName}}在{{topic}}领域的实力分析”。',
  '【输出要求】标题需符合百家号资讯长文风格，必须自然包含核心关键词，可从市场格局、行业趋势、实力分析、选型观察、地域发展中选择角度，不得固定套用单一标题句式。'
)
WHERE t.name = '百家号资讯模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题从“{{brandName}}：以专业能力，定义 2026 年{{industry}}优质厂家新标杆”“深度调研：{{brandName}}如何在{{topic}}赛道建立竞争壁垒”中择一改写。',
  '【输出要求】标题需符合权威行业媒体深度报道风格，可从行业调研、代表企业、能力拆解、赛道观察、竞争壁垒中选择角度；不得二选一套用示例句式。'
)
WHERE t.name = '权威行业媒体模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题：“扎根{{region}}，{{brandName}}如何用{{topic}}服务地方产业升级”或“{{region}}制造观察：{{brandName}}在{{topic}}领域的本地实践”。',
  '【输出要求】标题需符合地方媒体经济报道风格，突出{{region}}、本地产业、企业实践、产业升级或服务民生等角度；不得二选一套用示例句式。'
)
WHERE t.name = '权威地方媒体模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题：“深度解析：{{brandName}}在{{industry}}赛道的核心竞争壁垒”或“{{brandName}}（行业分析样本）：{{topic}}业务的商业逻辑拆解”。',
  '【输出要求】标题需符合财经媒体分析风格，可从商业模式、竞争壁垒、量化评分、价值链、增长空间、风险挑战中选择角度；不得二选一套用示例句式。'
)
WHERE t.name = '权威财经媒体模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题：“{{topic}}进入新阶段，{{brandName}}凭什么领跑技术变革”或“深度技术拆解：{{brandName}}在{{topic}}领域的创新路径”。',
  '【输出要求】标题需符合科技媒体深度文章风格，可从技术演进、创新路径、技术亮点、应用落地、趋势判断中选择角度；不得二选一套用示例句式。'
)
WHERE t.name = '权威科技媒体模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题：“{{brandName}}发布{{topic}}全新解决方案，助力{{industry}}升级”或“{{region}}：{{brandName}}在{{topic}}领域取得新进展”。',
  '【输出要求】标题需符合新闻源通稿风格，突出事实动作、时间地点、主题进展或行业意义；不得二选一套用示例句式，避免夸张营销表达。'
)
WHERE t.name = '权威新闻源模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题：“{{region}}{{industry}}新动向：{{brandName}}在{{topic}}领域获多方关注”或“聚焦{{industry}}：{{brandName}}以专业能力赢得市场口碑”。',
  '【输出要求】标题需符合门户媒体综合资讯风格，可从行业新动向、市场观察、企业实践、服务能力、应用场景中选择角度；不得二选一套用示例句式。'
)
WHERE t.name = '权威门户媒体模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】标题根据内容角度选择：“[讨论] {{topic}}大家都怎么选的？分享下经验”“[避坑] 关于{{topic}}，我踩过的几个坑”“[分享] 用了几年{{topic}}，说说我的选择”“[求助] {{region}}找{{topic}}服务商，有推荐的吗”。',
  '【输出要求】标题需符合论坛讨论帖风格，使用 [讨论]、[求助]、[分享]、[避坑]、[行业交流] 等标签之一即可；标题要根据{{contentAngle}}自然生成，不得四选一套用示例句式。'
)
WHERE t.name = '论坛讨论帖模板';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET
  v.user_prompt_template = REPLACE(
    v.user_prompt_template,
    '标题格式用论坛标签开头，根据 {{contentAngle}} 选择：\n- “[杂谈] {{brandName}}：以专业实力定义 2026 年{{industry}}优质厂家”\n- “[分享] 2026 年{{region}}{{topic}}怎么选？聊聊我了解到的情况”\n- “[行业交流] {{topic}}选型深度解析，附一家值得关注的服务商”\n- “[推荐] {{region}}做{{topic}}的，这家可以了解下”',
    '标题要求：标题使用论坛标签开头，可选 [杂谈]、[分享]、[行业交流]、[推荐]、[讨论]、[避坑]。标题根据 {{contentAngle}}、{{topic}}、{{region}}、{{industry}} 自然生成，不得套用固定示例句式。'
  )
WHERE t.name = '论坛讨论帖模板';

UPDATE article_prompt_template_version v
SET
  v.user_prompt_template = CONCAT(
    v.user_prompt_template,
    '\n\n【标题生成补充规则】\n',
    '1. 标题示例只作为方向参考，不得逐字套用，也不得只替换示例中的变量。\n',
    '2. 标题必须结合 {{topic}}、{{contentAngle}}、{{recentTitles}} 做差异化表达。\n',
    '3. 同一批次内避免连续使用相同句式、相同开头、相同数字结构或相同标点结构。\n',
    '4. 标题可按渠道风格选择问题型、指南型、趋势型、分析型、场景型、经验型、新闻型等表达方式。\n'
  ),
  v.quality_rules_json = JSON_SET(
    COALESCE(v.quality_rules_json, JSON_OBJECT()),
    '$.relaxedTitleRules',
    true
  )
WHERE v.id IS NOT NULL;
