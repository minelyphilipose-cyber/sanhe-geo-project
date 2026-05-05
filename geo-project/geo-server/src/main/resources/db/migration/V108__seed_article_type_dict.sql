INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'article_type', 'faq', 'FAQ', 10, 1, '问答式短文'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'article_type' AND dict_key = 'faq');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'article_type', 'scenario_content', '场景内容', 20, 1, '使用场景介绍'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'article_type' AND dict_key = 'scenario_content');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'article_type', 'industry_article', '行业文章', 30, 1, '行业深度解读'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'article_type' AND dict_key = 'industry_article');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'article_type', 'stage_advice', '阶段建议', 40, 1, '分阶段方案建议'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'article_type' AND dict_key = 'stage_advice');
