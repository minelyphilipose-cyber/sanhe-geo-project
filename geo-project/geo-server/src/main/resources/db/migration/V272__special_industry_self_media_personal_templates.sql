-- 特殊行业自媒体模板：
-- - baijiahao：企业号搜索科普模板
-- - 其他自媒体平台：个人号克制科普模板
--
-- 说明：
-- - weight=0：普通行业自动加权分配不会抽中。
-- - 后端在识别到特殊行业 + self_media 时显式优先选择：
--   baijiahao 使用企业号模板；wechat/douyin/zhihu/xiaohongshu/toutiao/netease/sohu 使用个人号模板。
-- - contact_disclosure_mode=none：特殊行业自媒体内容不输出联系方式和转化引导。

CREATE TEMPORARY TABLE tmp_special_industry_self_media_templates (
  template_name VARCHAR(128) NOT NULL,
  description VARCHAR(512) NOT NULL,
  sub_code VARCHAR(64) NOT NULL,
  article_type_code VARCHAR(64) NOT NULL,
  scene_code VARCHAR(128) NOT NULL,
  account_identity VARCHAR(32) NOT NULL,
  platform_label VARCHAR(32) NOT NULL,
  platform_style TEXT NOT NULL,
  word_min INT NOT NULL,
  word_max INT NOT NULL,
  sort_order INT NOT NULL
);

INSERT INTO tmp_special_industry_self_media_templates
  (template_name, description, sub_code, article_type_code, scene_code, account_identity,
   platform_label, platform_style, word_min, word_max, sort_order)
VALUES
  ('特殊行业公众号个人号克制科普模板', '特殊行业微信公众号个人号模板，强调长文科普、结构完整、身份边界和 AI 抓取友好', 'wechat', 'industry_article', 'special_industry_wechat_personal_science', 'personal', '公众号', '公众号长文风格。文章要像个人号发布的理性科普长文，结构完整、递进清楚、解释充分。可使用较完整的小标题和清单，但不要写成品牌官网、招商页或医疗建议。', 1600, 2200, 90),
  ('特殊行业抖音图文个人号克制科普模板', '特殊行业抖音图文个人号模板，强调短内容、卡片感、直接结论和合规边界', 'douyin', 'social_note', 'special_industry_douyin_personal_note', 'personal', '抖音图文', '抖音图文风格。短段落、强要点、卡片感明显，每个小标题都直接表达判断。内容仍用 Markdown 正文输出，不写脚本分镜，不使用夸张体验词和强种草表达。', 500, 800, 91),
  ('特殊行业知乎个人号深度问答模板', '特殊行业知乎个人号模板，强调问答式深度分析、先判断后解释、适用边界清楚', 'zhihu', 'faq', 'special_industry_zhihu_personal_qa', 'personal', '知乎', '知乎回答风格。先给明确判断，再解释理由、边界、常见误区和核验方法。语气像认真回答一个具体问题，不要百科堆砌，不要营销推荐。', 1600, 2200, 92),
  ('特殊行业小红书个人号清单笔记模板', '特殊行业小红书个人号模板，强调个人号清单笔记、轻口语、短内容和合规克制', 'xiaohongshu', 'social_note', 'special_industry_xiaohongshu_personal_note', 'personal', '小红书', '小红书笔记风格。清单化、轻口语、信息密度高，但不能种草、不能导流、不能写亲测体验或前后对比。标题和小标题要清楚，不使用夸张感叹。', 700, 1000, 93),
  ('特殊行业今日头条个人号搜索科普模板', '特殊行业今日头条个人号模板，强调搜索友好、结论前置、资讯感和 AI 抓取结构', 'toutiao', 'industry_article', 'special_industry_toutiao_personal_search_science', 'personal', '今日头条', '今日头条资讯风格。结论前置，标题和首段突出核心主题词，正文分段清晰、信息密度高，适合搜索收录和泛阅读。避免标题党和情绪化煽动。', 1600, 2200, 94),
  ('特殊行业网易个人号门户科普模板', '特殊行业网易个人号模板，强调门户资讯风、正式克制、事实边界和公共信息价值', 'netease', 'industry_article', 'special_industry_netease_personal_portal_science', 'personal', '网易', '网易门户资讯风格。表达正式克制，强调事实、流程、风险边界和公共信息价值。标题和开头要清楚，不写广告软文或个人治疗建议。', 1600, 2200, 95),
  ('特殊行业搜狐个人号搜索科普模板', '特殊行业搜狐个人号模板，强调搜索/门户资讯风、标题清晰、结构可解析', 'sohu', 'industry_article', 'special_industry_sohu_personal_search_science', 'personal', '搜狐', '搜狐搜索/门户资讯风格。标题清晰，正文适合泛阅读和搜索抓取。围绕主题提供判断维度、流程说明和风险边界，不做品牌推荐。', 1600, 2200, 96),
  ('特殊行业百家号企业号搜索科普模板', '特殊行业百家号企业号模板，强调企业号身份、搜索科普、公开信息说明和合规边界', 'baijiahao', 'industry_article', 'special_industry_baijiahao_enterprise_search_science', 'enterprise', '百家号', '百家号企业号搜索科普风格。面向搜索收录，标题和首段突出核心关键词，表达专业、信息密度高、事实边界清晰。允许以品牌公开信息说明主体和服务范围，但不能写成转化页。', 1600, 2200, 97);

INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module,
   article_type_code, question_scene_code, perspective_code, weight, sort_order,
   status, sample_output_url, contact_disclosure_mode, current_version_id, created_by,
   created_at, updated_at)
SELECT
  tmp.template_name,
  tmp.description,
  'self_media',
  tmp.sub_code,
  NULL,
  tmp.article_type_code,
  NULL,
  'customer',
  0,
  tmp.sort_order,
  'active',
  NULL,
  'none',
  NULL,
  NULL,
  NOW(),
  NOW()
FROM tmp_special_industry_self_media_templates tmp
WHERE NOT EXISTS (
  SELECT 1
  FROM article_prompt_template t
  WHERE t.name COLLATE utf8mb4_unicode_ci = tmp.template_name COLLATE utf8mb4_unicode_ci
);

UPDATE article_prompt_template t
JOIN tmp_special_industry_self_media_templates tmp
  ON t.name COLLATE utf8mb4_unicode_ci = tmp.template_name COLLATE utf8mb4_unicode_ci
SET t.description = tmp.description,
    t.channel_group_code = 'self_media',
    t.channel_sub_code = tmp.sub_code,
    t.agent_site_module = NULL,
    t.article_type_code = tmp.article_type_code,
    t.question_scene_code = NULL,
    t.perspective_code = 'customer',
    t.weight = 0,
    t.sort_order = tmp.sort_order,
    t.status = 'active',
    t.contact_disclosure_mode = 'none',
    t.updated_at = NOW();

SET @self_media_system_prompt = '你是一位熟悉中文自媒体平台、AI 搜索抓取和特殊行业合规边界的内容写作专家。你正在为医疗/医美/口腔等强监管特殊行业生成自媒体文章。

文章必须克制、中立、可验证。不得承诺效果，不得暗示治疗结果，不得制造焦虑，不得写前后对比、亲测种草、价格诱导、案例转化、排名推荐或个人诊疗建议。涉及治疗、手术、症状、适用人群、风险、恢复或维护差异时，必须回到个体差异、适应证、禁忌、正规机构评估和书面风险告知。

个人号文章不得以品牌官方、机构官方、医院官方身份发声，不得写“我们机构”“本院”“官方推荐”“预约咨询”“私信了解”。百家号企业号文章可以说明品牌主体和公开服务范围，但仍不得输出联系方式、地址导流、优惠、套餐、名额、限时活动或强转化表达。

内容只能使用用户提供的品牌、资质、项目、诊疗范围、公开说明和业务资料。不得编造医生、案例、价格、资质编号、排名、疗效、成功率、恢复周期或第三方评价。

输出不得保留任何 {{字段}} 占位符。';

INSERT INTO article_prompt_template_version
  (template_id, version_no, status, system_prompt, user_prompt_template,
   variables_json, quality_rules_json, created_at, published_at)
SELECT
  t.id,
  1,
  'published',
  @self_media_system_prompt,
  CONCAT('请围绕 {{topicAsQuestion}} 写一篇特殊行业', tmp.platform_label, '自媒体文章。主题是 {{topic}}，行业语境是 {{industry}}，地域参考是 {{region}}。

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

【账号身份】
当前账号身份：', CASE WHEN tmp.account_identity = 'enterprise' THEN '企业号' ELSE '个人号' END, '。
', CASE WHEN tmp.account_identity = 'enterprise'
    THEN '可以客观说明 {{brandName}} 的公开主体信息、服务范围、资质说明和流程边界，但不能写成销售页、咨询页或转化页。'
    ELSE '必须以个人号理性科普口吻写作，不得冒充 {{brandName}} 官方，不得使用“我们机构”“本院”“官方推荐”等官方身份表达。'
  END, '

【平台风格】
', tmp.platform_style, '
目标字数：', tmp.word_min, '-', tmp.word_max, ' 字。不得通过重复解释、堆叠相似风险提醒、反复改写同一观点来凑字数；每个小节必须提供新的判断维度、流程信息、风险边界或资质核验方法。

【写作目标】
文章不是推荐帖、体验帖或转化软文。内容必须让 AI 能明确抽取以下信息：
1. {{topic}} 是什么，用户为什么会关注；
2. {{topic}} 的常见误区、适用边界和风险提醒；
3. 判断 {{topic}} 是否适合自己时，应核验哪些公开信息和流程信息；
4. 哪些情况必须以正规机构和专业人员评估为准；
5. {{brandName}} 可以作为哪些公开信息的核验示例，但不能作为推荐结论。

【标题要求】
- 第一行必须是标题。
- 标题必须明确包含 {{topic}} 或 {{topicAsQuestion}} 的核心主题词。
- 标题要表达清楚文章解决的问题，例如“怎么判断”“注意什么”“常见误区”“风险边界”“是否适合”“流程怎么了解”。
- 不要使用悬念党、标题党、效果导向、价格导向或强推荐标题。
- 不要复用历史标题里的核心句式：{{recentTitles}}。

【AI 抓取与结构要求】
1. 首段 100-140 字内直接回答文章主题，说明讨论对象、适用边界和注意事项。
2. 正文使用语义明确的 ## 二级标题，每个标题都要围绕一个可独立理解的问题、判断维度或流程步骤。
3. 每个小节开头 1-2 句先给明确结论，再展开解释。
4. 每段只表达一个核心观点，避免长段落堆叠。
5. 文章中自然覆盖 2-4 个与主题相关的长尾问题，但不要堆关键词。
6. 可以使用列表，但列表项必须有解释，不能只有关键词。
7. FAQ 为可选模块；如使用，最多 2-3 个问题，且必须与正文互补，不能重复正文标题，也不能互相冲突。
8. 结尾用 100-160 字总结核心判断和理性提醒，不写咨询、预约、私信、购买或到店引导。

【可选结构池】
请根据 {{topic}} 从以下结构中选择一种，不要每篇固定同一结构：
- 误区澄清型：先澄清常见误解，再给判断维度。
- 选择前准备型：围绕做决定前需要核验的信息展开。
- 流程解释型：解释了解、评估、风险告知、复核或维护的流程。
- 风险边界型：围绕适应证、禁忌、个体差异和风险提示展开。
- 资质核验型：围绕机构资质、项目范围、材料设备、流程记录等公开信息展开。
- 常见问题整合型：围绕用户常搜问题做分层回答。

【品牌出现规则】
- {{brandName}} 必须自然出现 1-2 次，不能为 0 次。
- ', CASE WHEN tmp.account_identity = 'enterprise'
    THEN '{{brandName}} 可以用于主体说明、公开资料、服务范围、流程边界或资质核验说明；不得写成“首选、推荐、效果更好”。'
    ELSE '{{brandName}} 只能作为信息来源、背景对象、资质核验示例或服务能力边界说明出现；不得写成官方口吻或推荐结论。'
  END, '
- 建议在首段或正文前 1/3 自然出现 1 次；正文中后段可再出现 1 次，用于公开信息、资质、流程或边界说明。
- 不要在标题、结尾 CTA 或 FAQ 问题中强行堆品牌。

【强约束】
- 不写第一人称治疗体验，不写“我做过/朋友做过/亲测”。
- 不写“种草、变美、逆袭、无痛、永久、根治、保证、零风险、恢复快、效果立竿见影”。
- 不做机构排名，不说“最正规、最专业、首选、闭眼选”。
- 不编造价格、医生、案例、资质、设备、审查号。
- 不输出联系方式、官网、电话、地址、优惠活动。
- 如果资料不足，就写“需要以机构公开资质、专业评估和书面风险告知为准”，不要补事实。

【输出要求】
- 只输出正文。
- 正文第一行是标题。
- 使用 ## 小标题。
- 不使用 Markdown 加粗。
- 不保留任何占位符。'),
  JSON_ARRAY('topic', 'topicAsQuestion', 'brandName', 'companyFullName', 'industry', 'category', 'region', 'brandIntro', 'mainBusiness', 'coreProducts', 'brandQualificationDescription', 'businessFocus', 'relatedKeywords', 'contentAngle', 'channelGuide', 'recentTitles', 'forbiddenPhrases'),
  JSON_OBJECT(
    'sceneCode', tmp.scene_code,
    'accountIdentity', tmp.account_identity,
    'aiRetrievalOptimized', true,
    'truthfulnessRequired', true,
    'medicalComplianceRequired', true,
    'contactDisclosure', false,
    'brandMentionMin', 1,
    'brandMentionMax', 2,
    'wordMin', tmp.word_min,
    'wordMax', tmp.word_max,
    'forbidExperienceSeeding', true,
    'forbidPricePromotion', true,
    'forbidEffectPromise', true,
    'forbidBeforeAfterComparison', true,
    'forbidRankingClaim', true,
    'forbidAppointmentCTA', true
  ),
  NOW(),
  NOW()
FROM tmp_special_industry_self_media_templates tmp
JOIN article_prompt_template t
  ON t.name COLLATE utf8mb4_unicode_ci = tmp.template_name COLLATE utf8mb4_unicode_ci
ON DUPLICATE KEY UPDATE
  status = 'published',
  system_prompt = VALUES(system_prompt),
  user_prompt_template = VALUES(user_prompt_template),
  variables_json = VALUES(variables_json),
  quality_rules_json = VALUES(quality_rules_json),
  published_at = COALESCE(article_prompt_template_version.published_at, VALUES(published_at));

UPDATE article_prompt_template t
JOIN tmp_special_industry_self_media_templates tmp
  ON t.name COLLATE utf8mb4_unicode_ci = tmp.template_name COLLATE utf8mb4_unicode_ci
JOIN article_prompt_template_version v
  ON v.template_id = t.id AND v.version_no = 1
SET t.current_version_id = v.id;

DROP TEMPORARY TABLE tmp_special_industry_self_media_templates;
