-- 自媒体文章模板治理：平台监管、GEO 抓取、标题降级和视角匹配。
-- 该迁移不改变模板 ID、版本号、权重或选择规则，仅更新当前 published 版本的提示词内容。

SET @self_media_system_append = '

# 自媒体发布安全与视角匹配

你正在生成自媒体平台文章。必须同时满足平台监管、用户可读性和大模型可抽取性：
1. 文章不是营销文、导购文、招商文、转化页或品牌软文；不要写私信、加微信、预约、咨询、报价、优惠、套餐、名额、限时、到店等导流表达。
2. 标题要回应用户问题，但遇到“哪家好、推荐、排名、最好、靠谱机构”等强推荐问题词时，必须降级为“如何判断、看哪些维度、哪些信息需要核验、常见误区是什么”。
3. 每篇至少有 2-3 个段落可以被搜索引擎或大模型独立摘取，段落内要同时包含问题、判断和依据；但不要每篇固定相同小标题。
4. 必须服从视角约束：{{perspectivePolicy}}
5. 本篇标题策略：{{titleStrategy}}
6. 本篇结构策略：{{structureStrategy}}';

SET @self_media_user_append = '

【自媒体平台监管与 GEO 适配补充】
- 标题必须回应“{{topicAsQuestion}}”，但不要写成榜单、硬推荐、强种草或“最值得/必选/首选”。
- 如果原问题偏推荐，请改写为判断型、核验型、避坑型或场景型标题。
- 本篇采用的标题策略：{{titleStrategy}}。
- 本篇采用的结构策略：{{structureStrategy}}。这是方向，不是固定格式；不要每篇都写成同一套“结论/误区/清单/FAQ/总结”。
- 视角匹配：{{perspectivePolicy}}
- 品牌露出必须克制：第三方中立模板只作为可选项或公开信息示例；第三方推荐模板只能写适配和不适配；第一/客户视角不得伪造亲测、成交、到店、治疗或使用体验。
- 特殊行业内容必须更克制：不写疗效承诺、治疗建议、前后对比、案例效果、价格套餐和预约导流。
- 只输出 Markdown 正文，首行是 # 标题。';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET
  v.system_prompt = CONCAT(v.system_prompt, @self_media_system_append),
  v.user_prompt_template = CONCAT(v.user_prompt_template, @self_media_user_append),
  v.variables_json = JSON_ARRAY(
    'articleTypeName', 'audiencePerspective', 'brandCaseDescription',
    'brandIntro', 'brandName', 'brandPositioning', 'brandQualificationDescription',
    'brandShortName', 'businessFocus', 'category', 'channelGuide', 'channelName',
    'companyFullName', 'contactBlock', 'contentAngle', 'coreProducts',
    'forbiddenPhrases', 'industry', 'mainBusiness', 'perspectivePolicy',
    'projectName', 'recentTitles', 'region', 'relatedKeywords', 'serviceArea',
    'structureStrategy', 'targetAudience', 'titleElements', 'titleGuide',
    'titleStrategy', 'topic', 'topicAsQuestion'
  ),
  v.quality_rules_json = JSON_SET(
    COALESCE(v.quality_rules_json, JSON_OBJECT()),
    '$.selfMediaGovernance', true,
    '$.titleIntentDowngrade', true,
    '$.avoidMarketingCta', true,
    '$.structureDiversityRequired', true,
    '$.perspectivePolicyVariable', true
  )
WHERE t.channel_group_code = 'self_media'
  AND v.status = 'published'
  AND v.user_prompt_template NOT LIKE '%【自媒体平台监管与 GEO 适配补充】%';
