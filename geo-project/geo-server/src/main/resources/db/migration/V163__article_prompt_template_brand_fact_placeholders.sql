-- ============================================================
-- V163: expose structured brand fact placeholders to article prompt templates
-- ============================================================

UPDATE article_prompt_template_version v
SET
  v.user_prompt_template = CONCAT(
    v.user_prompt_template,
    '\n\n【品牌事实素材】\n',
    '以下素材来自品牌信息配置，只能按需引用，不要求全部写入文章。\n',
    '公司全称：{{companyFullName}}\n',
    '品牌简称：{{brandShortName}}\n',
    '品牌定位：{{brandPositioning}}\n',
    '主营业务：{{mainBusiness}}\n',
    '核心产品：{{coreProducts}}\n',
    '服务区域：{{serviceArea}}\n',
    '基本信息介绍：{{brandIntro}}\n\n',
    '【资质素材】\n',
    '{{brandQualificationDescription}}\n\n',
    '【案例素材】\n',
    '{{brandCaseDescription}}\n\n',
    '【品牌事实使用规则】\n',
    '1. 公司概况、主体登场、核心信息概览等段落优先使用公司全称、品牌简称、品牌定位、主营业务、核心产品、服务区域和基本信息介绍。\n',
    '2. 资质背书段只能引用“资质素材”中已经提供的认证、证书、标准、专利、荣誉、检测报告或能力证明；如果资质素材为空或为“-”，不得编造，改写为“建议核验资质证书、检测报告或执行标准”等通用判断。\n',
    '3. 案例段只能引用“案例素材”中已经提供的客户类型、项目背景、服务内容、项目规模、交付周期或合作结果；如果案例素材为空或为“-”，不得编造具名客户、项目金额或效果数据，改写为应用场景或选型建议。\n',
    '4. 服务对象、应用场景、业务模式、价值主张可以根据行业、主题、主营业务、核心产品和品牌定位生成大纲式内容，但不能生成具名客户、认证编号、专利数、合同金额、成立年份、市场份额等事实。\n'
  ),
  v.variables_json = JSON_ARRAY(
    'topic',
    'topicAsQuestion',
    'brandName',
    'companyFullName',
    'brandShortName',
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
    'mainBusiness',
    'coreProducts',
    'brandPositioning',
    'serviceArea',
    'brandIntro',
    'brandQualificationDescription',
    'brandCaseDescription',
    'recentTitles',
    'contactBlock'
  ),
  v.quality_rules_json = JSON_SET(
    COALESCE(v.quality_rules_json, JSON_OBJECT()),
    '$.brandFactPlaceholders',
    true
  )
WHERE v.id IS NOT NULL;
