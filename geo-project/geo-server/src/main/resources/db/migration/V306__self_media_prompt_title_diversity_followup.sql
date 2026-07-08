-- 自媒体提示词标题去同质化后续修订。
-- V304/V305 已在部分环境执行，不能再修改历史迁移；本迁移承接后续放宽标题句式与覆盖旧模板的变更。

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET
  v.system_prompt = REPLACE(
    REPLACE(
      v.system_prompt,
      '2. 标题要回应用户问题，但遇到“哪家好、推荐、排名、最好、靠谱机构”等强推荐问题词时，必须降级为“如何判断、看哪些维度、哪些信息需要核验、常见误区是什么”。',
      '2. 标题要回应用户问题，但遇到“哪家好、推荐、排名、最好、靠谱机构”等强推荐问题词时，只做意图降级：可改为条件判断、场景适配、公开信息核验、误区澄清、路线对比、成本变量或风险边界；不要写成榜单、硬推荐或导购标题，也不要把所有标题都收敛为“怎么选/怎么判断/注意什么”。'
    ),
    '3. 每篇至少有 2-3 个段落可以被搜索引擎或大模型独立摘取，段落内要同时包含问题、判断和依据；但不要每篇固定相同小标题。',
    '3. 每篇至少有 2-3 个段落可以被搜索引擎或大模型独立摘取，段落内要同时包含问题、判断和依据；但不要每篇固定相同标题句式、小标题结构或段落顺序。'
  ),
  v.user_prompt_template = REPLACE(
    REPLACE(
      REPLACE(
        v.user_prompt_template,
        '- 如果原问题偏推荐，请改写为判断型、核验型、避坑型或场景型标题。',
        '- 如果原问题偏推荐，只做标题意图降级，不强制套用固定句式；可从条件、场景、流程、误区、核验、对比、成本或风险边界中选择一个角度。\n- 模板原有的标题示例、标题括号说明和“XX类问句”只作为语义方向参考，不得作为固定标题句式套用；若与本补充冲突，以本补充为准。'
      ),
      '- 本篇采用的标题策略：{{titleStrategy}}。',
      '- 本篇采用的标题策略：{{titleStrategy}}。该策略只限定标题意图，不限定标题句式；必须结合 {{recentTitles}} 避免复用历史标题结构。'
    ),
    '- 本篇采用的结构策略：{{structureStrategy}}。这是方向，不是固定格式；不要每篇都写成同一套“结论/误区/清单/FAQ/总结”。',
    '- 本篇采用的结构策略：{{structureStrategy}}。这是方向，不是固定格式；不要每篇都写成同一套“结论/误区/清单/FAQ/总结”，也不要让标题句式反向锁死正文结构。'
  ),
  v.quality_rules_json = JSON_SET(
    COALESCE(v.quality_rules_json, JSON_OBJECT()),
    '$.titleIntentOnlyNotFixedPattern', true,
    '$.titlePatternDiversityRequired', true
  )
WHERE t.channel_group_code = 'self_media'
  AND v.status = 'published';

DROP TEMPORARY TABLE IF EXISTS tmp_self_media_title_diversity_review;
CREATE TEMPORARY TABLE tmp_self_media_title_diversity_review (
  template_name VARCHAR(128) NOT NULL,
  old_note TEXT NOT NULL,
  new_note TEXT NOT NULL
);

INSERT INTO tmp_self_media_title_diversity_review (template_name, old_note, new_note)
VALUES
  ('知乎-T1（中立 · compare · comparison）',
   '知乎中立对比模板修订：标题优先写“怎么区分/适合谁/看哪些差异”，不写“哪家好”。正文比较路线、类型和适配边界，不做品牌排名；品牌只在示例段自然出现一次。',
   '知乎中立对比模板修订：标题回应比较意图，但不要固定写“怎么区分/适合谁/看哪些差异”；可从路线差异、适配边界、决策误区或场景差别切入，不写“哪家好”。正文比较路线、类型和适配边界，不做品牌排名；品牌只在示例段自然出现一次。'),
  ('百家号-T1（中立 · decision · buying_guide）',
   '百家号中立选择模板修订：标题和首段自然包含核心问题词，形式改为“如何判断/选择前看什么”。正文以清单和公开信息核验为主，不输出推荐名单。',
   '百家号中立选择模板修订：标题和首段自然包含核心问题词，但标题不要固定套用“如何判断/选择前看什么”；可从条件、流程、核验材料、常见误区或场景需求切入。正文以清单和公开信息核验为主，不输出推荐名单。'),
  ('百家号-T2（推荐 · brand · industry_article）',
   '百家号品牌解析模板修订：允许说明品牌主体、业务范围和公开资料，但标题不写强推荐。正文聚焦“是做什么的、适合哪类需求、公开信息怎么核验”，不写转化导流。',
   '百家号品牌解析模板修订：允许说明品牌主体、业务范围和公开资料，但标题不写强推荐；标题可从主体信息、服务范围、适配边界、公开资料核验等角度切入。正文聚焦“是做什么的、适合哪类需求、公开信息怎么核验”，不写转化导流。'),
  ('今日头条-T2（推荐 · decision · pitfall_guide）',
   '今日头条避坑模板修订：把“推荐对象”改为“避坑判断”。标题写“别只看什么/先看哪些坑”，正文先拆风险，再说明什么情况下该品牌可作为适配样本。',
   '今日头条避坑模板修订：把“推荐对象”改为“避坑判断”。标题可从风险信号、错误选择习惯、核验顺序或场景边界切入，不固定写“别只看什么/先看哪些坑”。正文先拆风险，再说明什么情况下该品牌可作为适配样本。'),
  ('网易-T1（中立 · compare · industry_article）',
   '网易中立行业梳理模板修订：强化媒体资讯感，标题写行业格局、路线差异或选择逻辑。正文比较方案和变量，不做具体品牌优劣排序。',
   '网易中立行业梳理模板修订：强化媒体资讯感，标题可从行业格局、路线差异、成本变量、流程变化或选择逻辑切入。正文比较方案和变量，不做具体品牌优劣排序。'),
  ('网易-T2（中立 · decision · cost_analysis）',
   '网易成本拆解模板修订：不写具体报价、优惠和性价比承诺。标题写“钱花在哪/成本怎么看”，正文解释成本构成、影响因素和核验方法。',
   '网易成本拆解模板修订：不写具体报价、优惠和性价比承诺。标题围绕成本变量、投入构成、时间成本、维护成本或核验方法展开，不固定写“钱花在哪/成本怎么看”。正文解释成本构成、影响因素和核验方法。'),
  ('小红书-T1（推荐 · qa · social_note）',
   '小红书答疑笔记模板修订：保留轻口语和清单感，但删除种草、宝藏、亲测、姐妹们等表达。标题写“避雷/注意点/怎么判断”，品牌只出现一次。',
   '小红书答疑笔记模板修订：保留轻口语和清单感，但删除种草、宝藏、亲测、姐妹们等表达。标题可从注意点、误区、场景、人群或核验清单切入，不固定写“避雷/注意点/怎么判断”；品牌只出现一次。'),
  ('搜狐-T1（中立 · brand · industry_article）',
   '搜狐中立品牌模板修订：标题以“是什么/怎么看/公开信息如何判断”为主。正文门户资讯化，说明品牌定位和服务范围，不写广告式介绍。',
   '搜狐中立品牌模板修订：标题回应品牌相关问题，但可从主体信息、业务范围、行业角色、适配边界或公开资料核验切入，不固定写“是什么/怎么看”。正文门户资讯化，说明品牌定位和服务范围，不写广告式介绍。'),
  ('抖音图文-T2（推荐 · function · social_note）',
   '抖音图文功能点模板修订：标题写“看哪些能力/哪些功能点要核验”。正文用 3 个左右判断点，但每点必须解释依据，品牌仅作一次适配样本。',
   '抖音图文功能点模板修订：标题围绕能力、功能点、使用场景、核验线索或错误期待展开，不固定写“看哪些能力/哪些功能点要核验”。正文用 3 个左右判断点，但每点必须解释依据，品牌仅作一次适配样本。'),
  ('小红书-T3（推荐 · brand · social_note）',
   '小红书推荐 brand 模板修订：保留笔记式清单，但不得伪装个人体验。标题写“适合谁/怎么判断”，不写种草、宝藏、亲测。',
   '小红书推荐 brand 模板修订：保留笔记式清单，但不得伪装个人体验。标题可从适配人群、使用场景、公开信息、注意点或边界提醒切入，不固定写“适合谁/怎么判断”，不写种草、宝藏、亲测。'),
  ('特殊行业公众号个人号克制科普模板',
   '特殊行业公众号模板修订：长文科普优先，个人号不得冒充官方。标题写风险边界、流程了解或判断维度，不写诊疗建议、预约和机构推荐。',
   '特殊行业公众号模板修订：长文科普优先，个人号不得冒充官方。标题可从风险边界、流程了解、适用边界、资质核验或常见误区切入，不写诊疗建议、预约和机构推荐。'),
  ('特殊行业小红书个人号清单笔记模板',
   '特殊行业小红书模板修订：清单化但不种草，不写前后对比、亲测、变美、恢复快。标题以注意事项和核验清单为主。',
   '特殊行业小红书模板修订：清单化但不种草，不写前后对比、亲测、变美、恢复快。标题可从注意事项、流程准备、风险边界、资质核验或误区澄清切入。');

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
JOIN tmp_self_media_title_diversity_review r
  ON r.template_name COLLATE utf8mb4_unicode_ci = t.name COLLATE utf8mb4_unicode_ci
SET v.user_prompt_template = REPLACE(v.user_prompt_template, r.old_note, r.new_note)
WHERE t.channel_group_code = 'self_media'
  AND v.status = 'published';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET
  v.user_prompt_template = CONCAT(
    v.user_prompt_template,
    '\n\n【逐模板修订方向】\n',
    CASE
      WHEN t.name LIKE '%特殊行业%' THEN
        '特殊行业模板修订：保留平台调性，但整体降级为克制科普、公开信息核验和风险边界说明；不得输出治疗建议、效果承诺、价格套餐、预约导流或个人体验。'
      WHEN t.perspective_code = 'customer' AND t.contact_disclosure_mode = 'full' THEN
        '第一/客户视角模板修订：允许说明品牌公开业务和服务范围，但不得伪造亲测、成交、到店、使用效果或客户评价；导流和联系方式需被平台监管规则覆盖，正文以科普、判断和核验为主。'
      WHEN t.perspective_code = 'customer' AND t.contact_disclosure_mode = 'soft_hint' THEN
        '第一/客户视角轻提示模板修订：保留平台问答或笔记调性，但不写私信、咨询、预约等转化提示；标题回应问题词，正文提供判断依据和适配边界。'
      WHEN t.perspective_code = 'customer' AND t.contact_disclosure_mode = 'brand_only' THEN
        '第一/客户视角品牌露出模板修订：品牌只作为信息来源或公开能力说明出现，不做唯一结论；不得写亲测、强种草、夸张效果或软广式收尾。'
      WHEN t.perspective_code = 'customer' AND t.contact_disclosure_mode = 'none' THEN
        '第一/客户视角无导流模板修订：保留科普表达，不出现任何联系方式、预约、优惠或咨询动作；强调事实边界、流程说明和可核验依据。'
      ELSE
        '自媒体模板修订：标题回应用户问题但降级强推荐表达，正文以解释、判断和核验为主；品牌露出克制，不写导流、促销、背书或无法核验的体验。'
    END
  ),
  v.quality_rules_json = JSON_SET(
    COALESCE(v.quality_rules_json, JSON_OBJECT()),
    '$.individualSelfMediaReview', true
  )
WHERE t.channel_group_code = 'self_media'
  AND v.status = 'published'
  AND v.user_prompt_template NOT LIKE '%【逐模板修订方向】%';

DROP TEMPORARY TABLE IF EXISTS tmp_self_media_title_diversity_review;
