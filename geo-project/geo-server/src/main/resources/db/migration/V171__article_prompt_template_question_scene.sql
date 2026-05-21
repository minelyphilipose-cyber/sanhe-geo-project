-- ============================================================
-- V171: add question scene binding for article prompt templates
-- ============================================================

ALTER TABLE article_prompt_template
  ADD COLUMN question_scene_code VARCHAR(32) NULL COMMENT 'linked question scene: brand/decision/deal/compare/qa/function' AFTER article_type_code,
  ADD KEY idx_article_prompt_template_question_scene (question_scene_code, channel_group_code, channel_sub_code, status);

UPDATE article_prompt_template
SET question_scene_code = 'brand'
WHERE name = '论坛品牌可信度分析模板';

UPDATE article_prompt_template
SET question_scene_code = 'decision'
WHERE name = '论坛选型决策指南模板';

UPDATE article_prompt_template
SET question_scene_code = 'deal'
WHERE name = '论坛推荐理由答疑模板';

UPDATE article_prompt_template
SET question_scene_code = 'compare'
WHERE name = '论坛对比评测模板';

UPDATE article_prompt_template
SET question_scene_code = 'qa'
WHERE name = '论坛问答答疑模板';

UPDATE article_prompt_template
SET question_scene_code = 'function'
WHERE name = '论坛功能能力解析模板';
