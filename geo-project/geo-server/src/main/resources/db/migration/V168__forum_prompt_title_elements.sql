-- ============================================================
-- V168: let forum prompt templates use backend title elements
-- ============================================================

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '【输出要求】',
  '{{titleGuide}}\n\n【输出要求】'
)
WHERE t.name IN ('论坛讨论帖模板', '论坛对比推荐模板')
  AND v.status = 'published'
  AND v.user_prompt_template NOT LIKE '%{{titleGuide}}%';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '标题要求：标题使用论坛标签开头，可选 [杂谈]、[分享]、[行业交流]、[推荐]、[讨论]、[避坑]。标题根据 {{contentAngle}}、{{topic}}、{{region}}、{{industry}} 自然生成，不得套用固定示例句式。',
  '标题要求：优先遵循上方【标题生成参考】，根据 {{topic}}、{{region}}、{{industry}}、{{contentAngle}} 和 {{recentTitles}} 自然生成，不得机械堆叠元素，不得套用固定示例句式。'
)
WHERE t.name = '论坛讨论帖模板'
  AND v.status = 'published';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.user_prompt_template = REPLACE(
  v.user_prompt_template,
  '标题要求：用论坛标签开头，围绕 {{region}}、{{topic}}、对比或选型生成标题，不要只套用固定标题。可以参考但不要照抄以下方向：\n- “[对比] 2026 年{{region}}{{topic}}怎么选？几家服务商横向聊聊”\n- “[杂谈] 同样是做{{topic}}，{{brandName}}和同行差在哪”\n- “[分享] 选{{topic}}前我对比了几家，说说我的结论”\n- “[行业交流] {{topic}}选型看哪些维度？顺手对比几家服务商”',
  '标题要求：优先遵循上方【标题生成参考】，围绕 {{region}}、{{topic}}、对比或选型自然生成标题。可以出现 {{brandName}}，但必须服务于语义，不得每篇都机械使用品牌名，不得套用固定示例句式。'
)
WHERE t.name = '论坛对比推荐模板'
  AND v.status = 'published';

UPDATE article_prompt_template_version v
JOIN article_prompt_template t ON t.id = v.template_id
SET v.variables_json = JSON_ARRAY_APPEND(COALESCE(v.variables_json, JSON_ARRAY()), '$', 'titleGuide', '$', 'titleElements')
WHERE t.name IN ('论坛讨论帖模板', '论坛对比推荐模板')
  AND v.status = 'published'
  AND JSON_CONTAINS(COALESCE(v.variables_json, JSON_ARRAY()), JSON_QUOTE('titleGuide')) = 0;
