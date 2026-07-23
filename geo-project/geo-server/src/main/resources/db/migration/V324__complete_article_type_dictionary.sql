INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
VALUES
    ('article_type', 'general_article', '通用文章', 5, 1, '手动导入文章的中性内部类型，不预设内容模板或发布平台'),
    ('article_type', 'faq', '问答文章', 10, 1, '围绕常见问题组织的问答型内容'),
    ('article_type', 'scenario_content', '场景内容', 20, 1, '围绕具体使用或决策场景展开的内容'),
    ('article_type', 'industry_article', '行业文章', 30, 1, '行业趋势、知识与观点类内容'),
    ('article_type', 'stage_advice', '阶段建议', 40, 1, '按用户所处阶段提供建议的内容'),
    ('article_type', 'buying_guide', '选择指南', 50, 1, '帮助用户选择产品或方案的内容'),
    ('article_type', 'comparison', '对比评测', 60, 1, '围绕产品、方案或路径进行对比的内容'),
    ('article_type', 'cost_analysis', '费用解析', 70, 1, '说明价格、费用构成或投入产出的内容'),
    ('article_type', 'pitfall_guide', '避坑指南', 80, 1, '揭示常见风险、误区及规避建议的内容'),
    ('article_type', 'social_note', '经验笔记', 90, 1, '偏个人经验分享和社交传播表达的内容'),
    ('article_type', 'news_brief', '资讯简讯', 100, 1, '简要传递动态、事件或行业资讯的内容'),
    ('article_type', 'forum_discussion', '讨论帖', 110, 1, '适合社区或论坛互动讨论的内容')
ON DUPLICATE KEY UPDATE
    dict_value = VALUES(dict_value),
    sort_order = VALUES(sort_order),
    enabled = VALUES(enabled),
    remark = VALUES(remark);
