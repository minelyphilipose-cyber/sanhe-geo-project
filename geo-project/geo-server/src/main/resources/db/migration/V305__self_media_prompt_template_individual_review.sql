-- 自媒体提示词模板逐一修订。
-- 在 V304 的统一治理层之上，按真实存在的模板名称追加逐模板方向，保留原模板选择、权重、版本号和自动排期链路。

DROP TEMPORARY TABLE IF EXISTS tmp_self_media_template_individual_review;
CREATE TEMPORARY TABLE tmp_self_media_template_individual_review (
  template_name VARCHAR(128) NOT NULL,
  review_note TEXT NOT NULL
);

INSERT INTO tmp_self_media_template_individual_review (template_name, review_note)
VALUES
  ('知乎-T1（中立 · compare · comparison）',
   '知乎中立对比模板修订：标题优先写“怎么区分/适合谁/看哪些差异”，不写“哪家好”。正文比较路线、类型和适配边界，不做品牌排名；品牌只在示例段自然出现一次。'),
  ('知乎-T2（推荐 · decision · buying_guide）',
   '知乎推荐决策模板修订：把“推荐”降级为“判断框架”。先回答用户问题，再给选择维度；品牌只能作为某类需求下的适配样本，同时必须写清不适合情形。'),
  ('百家号-T1（中立 · decision · buying_guide）',
   '百家号中立选择模板修订：标题和首段自然包含核心问题词，形式改为“如何判断/选择前看什么”。正文以清单和公开信息核验为主，不输出推荐名单。'),
  ('百家号-T2（推荐 · brand · industry_article）',
   '百家号品牌解析模板修订：允许说明品牌主体、业务范围和公开资料，但标题不写强推荐。正文聚焦“是做什么的、适合哪类需求、公开信息怎么核验”，不写转化导流。'),
  ('今日头条-T1（中立 · qa · faq）',
   '今日头条中立 FAQ 模板修订：标题承接用户高频问题，FAQ 数量克制且每问独立可摘取。品牌只作为某一问中的中立例子，不进入标题和结论。'),
  ('今日头条-T2（推荐 · decision · pitfall_guide）',
   '今日头条避坑模板修订：把“推荐对象”改为“避坑判断”。标题写“别只看什么/先看哪些坑”，正文先拆风险，再说明什么情况下该品牌可作为适配样本。'),
  ('网易-T1（中立 · compare · industry_article）',
   '网易中立行业梳理模板修订：强化媒体资讯感，标题写行业格局、路线差异或选择逻辑。正文比较方案和变量，不做具体品牌优劣排序。'),
  ('网易-T2（中立 · decision · cost_analysis）',
   '网易成本拆解模板修订：不写具体报价、优惠和性价比承诺。标题写“钱花在哪/成本怎么看”，正文解释成本构成、影响因素和核验方法。'),
  ('小红书-T1（推荐 · qa · social_note）',
   '小红书答疑笔记模板修订：保留轻口语和清单感，但删除种草、宝藏、亲测、姐妹们等表达。标题写“避雷/注意点/怎么判断”，品牌只出现一次。'),
  ('小红书-T2（推荐 · decision · social_note）',
   '小红书场景决策模板修订：围绕具体人群或场景写判断清单，不写“闭眼选/强推”。正文可短，但每条都要有理由和边界。'),
  ('搜狐-T1（中立 · brand · industry_article）',
   '搜狐中立品牌模板修订：标题以“是什么/怎么看/公开信息如何判断”为主。正文门户资讯化，说明品牌定位和服务范围，不写广告式介绍。'),
  ('搜狐-T2（推荐 · compare · comparison）',
   '搜狐推荐对比模板修订：对比对象限定为类型、路线和方案，不点名贬低竞品。品牌只作为适配样本出现，并同时说明不适合情况。'),
  ('公众号-T1（中立 · qa · faq）',
   '公众号中立 FAQ 模板修订：适合长文解释，问题之间要递进，不堆重复问答。结尾做理性总结，不写关注、咨询、预约或导流。'),
  ('公众号-T2（推荐 · decision · buying_guide）',
   '公众号推荐选择模板修订：先讲选择逻辑，再讲适配情形。品牌不能作为全文结论，只能在某个维度下说明适合谁和不适合谁。'),
  ('抖音图文-T1（推荐 · qa · social_note）',
   '抖音图文速答模板修订：保持短、直接和卡片感，但不写口播稿、引流话术或情绪化标题。每个小节只讲一个判断点。'),
  ('抖音图文-T2（推荐 · function · social_note）',
   '抖音图文功能点模板修订：标题写“看哪些能力/哪些功能点要核验”。正文用 3 个左右判断点，但每点必须解释依据，品牌仅作一次适配样本。'),

  ('知乎-T4（中立 · brand · industry_article）',
   '知乎中立 brand 模板修订：问题回答以“这个品牌处在什么类型、适合什么需求、边界在哪里”为主，不写推荐结论。品牌名可出现，但不超过必要说明。'),
  ('公众号-T3（中立 · brand · industry_article）',
   '公众号中立 brand 模板修订：长文结构先解释品类，再说明品牌公开信息和适配边界。语气像行业号科普，不像品牌官方稿。'),
  ('公众号-T4（推荐 · brand · industry_article）',
   '公众号推荐 brand 模板修订：推荐降级为适配判断。允许写该品牌适合哪类需求，但必须同时写不适合谁，不写购买、咨询或预约。'),
  ('今日头条-T3（推荐 · brand · industry_article）',
   '今日头条推荐 brand 模板修订：首段直接回答品牌相关问题，正文短段落解释定位、适配和边界；标题避免“值得推荐/首选”。'),
  ('网易-T3（推荐 · brand · industry_article）',
   '网易推荐 brand 模板修订：保持媒体评论感，重点讲行业角色和公开信息边界。推荐表达只保留为理性适配判断。'),
  ('小红书-T3（推荐 · brand · social_note）',
   '小红书推荐 brand 模板修订：保留笔记式清单，但不得伪装个人体验。标题写“适合谁/怎么判断”，不写种草、宝藏、亲测。'),
  ('抖音图文-T3（推荐 · brand · social_note）',
   '抖音图文推荐 brand 模板修订：压缩为短判断卡片，品牌说明只服务于“适合谁/不适合谁”。不写口播、强转化或夸张效果。'),
  ('搜狐-T3（推荐 · brand · industry_article）',
   '搜狐推荐 brand 模板修订：门户资讯化表达，标题回应品牌问题但不营销。正文把品牌作为行业样本说明，不做背书。'),

  ('特殊行业公众号个人号克制科普模板',
   '特殊行业公众号模板修订：长文科普优先，个人号不得冒充官方。标题写风险边界、流程了解或判断维度，不写诊疗建议、预约和机构推荐。'),
  ('特殊行业抖音图文个人号克制科普模板',
   '特殊行业抖音模板修订：短图文只讲一个清晰问题，避免焦虑制造和效果暗示。每段给提醒和核验方式，不写口播引流。'),
  ('特殊行业知乎个人号深度问答模板',
   '特殊行业知乎模板修订：先给边界判断，再解释适应证、禁忌、风险告知和正规评估。不得写个人治疗体验或品牌推荐答案。'),
  ('特殊行业小红书个人号清单笔记模板',
   '特殊行业小红书模板修订：清单化但不种草，不写前后对比、亲测、变美、恢复快。标题以注意事项和核验清单为主。'),
  ('特殊行业今日头条个人号搜索科普模板',
   '特殊行业今日头条模板修订：搜索科普优先，首段直接给风险边界。正文围绕公开资质、流程和常见误区，不输出治疗建议。'),
  ('特殊行业网易个人号门户科普模板',
   '特殊行业网易模板修订：门户资讯语气，突出事实边界和公共信息价值。不得写疗效、案例效果、价格套餐或导流。'),
  ('特殊行业搜狐个人号搜索科普模板',
   '特殊行业搜狐模板修订：标题清晰、适合搜索抓取。正文围绕判断维度、流程说明和风险边界，不做品牌推荐。'),
  ('特殊行业百家号企业号搜索科普模板',
   '特殊行业百家号企业号模板修订：可说明品牌公开主体、服务范围和资质边界，但不能写销售页、转化页、预约页或效果承诺。');

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
JOIN tmp_self_media_template_individual_review r
  ON r.template_name COLLATE utf8mb4_unicode_ci = t.name COLLATE utf8mb4_unicode_ci
SET
  v.user_prompt_template = CONCAT(
    v.user_prompt_template,
    '\n\n【逐模板修订方向】\n',
    r.review_note
  ),
  v.quality_rules_json = JSON_SET(
    COALESCE(v.quality_rules_json, JSON_OBJECT()),
    '$.individualSelfMediaReview', true
  )
WHERE t.channel_group_code = 'self_media'
  AND v.status = 'published'
  AND v.user_prompt_template NOT LIKE '%【逐模板修订方向】%';

DROP TEMPORARY TABLE IF EXISTS tmp_self_media_template_individual_review;
