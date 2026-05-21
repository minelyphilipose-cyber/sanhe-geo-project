INSERT INTO article_prompt_template
  (name, description, channel_group_code, channel_sub_code, agent_site_module, article_type_code, weight, sort_order, status)
VALUES
  ('Agent 官网 FAQ 模板', '默认官网 FAQ 生成模板', 'agent_site', NULL, 'faq', 'faq', 1, 10, 'active'),
  ('Agent 官网知识库模板', '默认官网知识库生成模板', 'agent_site', NULL, 'knowledge', 'industry_article', 1, 20, 'active'),
  ('Agent 官网产品服务模板', '默认官网产品服务生成模板', 'agent_site', NULL, 'product', 'scenario_content', 1, 30, 'active'),
  ('行业资讯站通用模板', '默认行业资讯站生成模板', 'industry_site', NULL, NULL, 'industry_article', 1, 10, 'active'),
  ('行业资讯站避坑模板', '默认行业资讯站避坑生成模板', 'industry_site', NULL, NULL, 'pitfall_guide', 1, 20, 'active'),
  ('今日头条资讯模板', '默认今日头条生成模板', 'self_media', 'toutiao', NULL, 'industry_article', 1, 10, 'active'),
  ('公众号长文模板', '默认公众号生成模板', 'self_media', 'wechat', NULL, 'industry_article', 1, 10, 'active'),
  ('知乎问答模板', '默认知乎问答生成模板', 'self_media', 'zhihu', NULL, 'faq', 2, 10, 'active'),
  ('知乎选择指南模板', '默认知乎选择指南生成模板', 'self_media', 'zhihu', NULL, 'buying_guide', 1, 20, 'active'),
  ('抖音图文模板', '默认抖音图文生成模板', 'self_media', 'douyin_image_text', NULL, 'social_note', 1, 10, 'active'),
  ('权威行业媒体模板', '默认行业媒体生成模板', 'authority_media', 'industry_media', NULL, 'industry_article', 1, 10, 'active'),
  ('权威地方媒体模板', '默认地方媒体生成模板', 'authority_media', 'local_media', NULL, 'news_brief', 1, 10, 'active'),
  ('权威财经媒体模板', '默认财经媒体生成模板', 'authority_media', 'finance_media', NULL, 'cost_analysis', 1, 10, 'active'),
  ('权威科技媒体模板', '默认科技媒体生成模板', 'authority_media', 'tech_media', NULL, 'industry_article', 1, 10, 'active'),
  ('权威新闻源模板', '默认新闻源媒体生成模板', 'authority_media', 'news_source', NULL, 'news_brief', 1, 10, 'active'),
  ('权威门户媒体模板', '默认门户媒体生成模板', 'authority_media', 'portal_media', NULL, 'industry_article', 1, 10, 'active'),
  ('论坛讨论帖模板', '默认论坛讨论帖生成模板', 'forum', NULL, NULL, 'forum_discussion', 1, 10, 'active');

INSERT INTO article_prompt_template_version
  (template_id, version_no, system_prompt, user_prompt_template, variables_json, quality_rules_json, status, published_at)
SELECT
  t.id,
  1,
  '你是一名中文 GEO 内容写作助手，负责生成可被搜索引擎和大模型检索、理解、引用的中文文章草稿。你必须保持客观、克制、可验证，不编造价格、案例、排名、资质、联系方式，不写广告软文，只输出完整 Markdown 正文。',
  CONCAT(
    '# 写作任务\n\n',
    '请围绕“{{topicAsQuestion}}”生成一篇中文文章。\n\n',
    '## 背景\n',
    '- 品牌：{{brandName}}\n',
    '- 行业：{{industry}}\n',
    '- 项目：{{projectName}}\n',
    '- 渠道：{{channelName}}\n',
    '- 文章用途：{{articleTypeName}}\n',
    '- 相关关键词：{{relatedKeywords}}\n',
    '- 禁用表达：{{forbiddenPhrases}}\n\n',
    '## 写作要求\n',
    '{{channelGuide}}\n\n',
    '## 输出要求\n',
    '- 只输出 Markdown 正文\n',
    '- 第一行必须是一级标题，格式为 “# 标题”\n',
    '- 小标题要具体，不使用空泛标题\n',
    '- 内容服务对象是搜索这个问题的用户，不是品牌方\n',
    '- 品牌信息只能作为背景或自然例子，不得作为推荐结论\n'
  ),
  JSON_ARRAY('topic', 'topicAsQuestion', 'brandName', 'industry', 'projectName', 'channelName', 'articleTypeName', 'relatedKeywords', 'forbiddenPhrases', 'channelGuide'),
  JSON_OBJECT('noMarketingWords', true, 'brandMentionBoundary', true),
  'published',
  NOW()
FROM article_prompt_template t
WHERE t.current_version_id IS NULL;

UPDATE article_prompt_template t
JOIN article_prompt_template_version v ON v.template_id = t.id AND v.version_no = 1
SET t.current_version_id = v.id;
