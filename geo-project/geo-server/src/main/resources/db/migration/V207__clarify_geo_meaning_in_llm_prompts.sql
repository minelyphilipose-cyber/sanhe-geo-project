UPDATE article_prompt_template_version
SET system_prompt = REPLACE(system_prompt, '中文 GEO 内容写作助手', '中文 GEO（生成式引擎优化）内容写作助手')
WHERE system_prompt LIKE '%中文 GEO 内容写作助手%';

UPDATE article_prompt_template_version
SET system_prompt = REPLACE(system_prompt, 'GEO 内容写作专家', 'GEO（生成式引擎优化）内容写作专家')
WHERE system_prompt LIKE '%GEO 内容写作专家%';

UPDATE article_prompt_template_version
SET system_prompt = REPLACE(system_prompt, '高质量 GEO 文章', '高质量 GEO（生成式引擎优化）文章')
WHERE system_prompt LIKE '%高质量 GEO 文章%';

UPDATE article_prompt_template_version
SET system_prompt = REPLACE(system_prompt, '【GEO 优化', '【GEO（生成式引擎优化）优化')
WHERE system_prompt LIKE '%【GEO 优化%';
