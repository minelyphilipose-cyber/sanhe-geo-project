-- 文章提示词 V2 人工灰度脚本。
-- 先通过模板预览验收 V319 创建的 published V2 版本，再按下面分组逐段执行。
-- 每段执行后观察生成成功率、提示词 Token、硬错误率、脱敏次数和特殊行业重试率。

-- 1. Agent 官网
UPDATE article_prompt_template t
JOIN (
    SELECT v.template_id, MAX(v.version_no) AS version_no
    FROM article_prompt_template_version v
    WHERE JSON_UNQUOTE(JSON_EXTRACT(v.quality_rules_json, '$.promptContract')) = 'v2'
      AND v.status = 'published'
    GROUP BY v.template_id
) latest ON latest.template_id = t.id
JOIN article_prompt_template_version v
  ON v.template_id = latest.template_id AND v.version_no = latest.version_no
SET t.current_version_id = v.id, t.updated_at = NOW()
WHERE t.status = 'active' AND t.channel_group_code = 'agent_site';

-- 2. 自媒体（保留各模板已有 customer / industry_neutral / review_recommend 视角）
UPDATE article_prompt_template t
JOIN (
    SELECT v.template_id, MAX(v.version_no) AS version_no
    FROM article_prompt_template_version v
    WHERE JSON_UNQUOTE(JSON_EXTRACT(v.quality_rules_json, '$.promptContract')) = 'v2'
      AND v.status = 'published'
    GROUP BY v.template_id
) latest ON latest.template_id = t.id
JOIN article_prompt_template_version v
  ON v.template_id = latest.template_id AND v.version_no = latest.version_no
SET t.current_version_id = v.id, t.updated_at = NOW()
WHERE t.status = 'active' AND t.channel_group_code = 'self_media';

-- 3. 行业资讯站
UPDATE article_prompt_template t
JOIN (
    SELECT v.template_id, MAX(v.version_no) AS version_no
    FROM article_prompt_template_version v
    WHERE JSON_UNQUOTE(JSON_EXTRACT(v.quality_rules_json, '$.promptContract')) = 'v2'
      AND v.status = 'published'
    GROUP BY v.template_id
) latest ON latest.template_id = t.id
JOIN article_prompt_template_version v
  ON v.template_id = latest.template_id AND v.version_no = latest.version_no
SET t.current_version_id = v.id,
    t.perspective_code = 'industry_neutral',
    t.updated_at = NOW()
WHERE t.status = 'active' AND t.channel_group_code = 'industry_site';

-- 4. 合作论坛
UPDATE article_prompt_template t
JOIN (
    SELECT v.template_id, MAX(v.version_no) AS version_no
    FROM article_prompt_template_version v
    WHERE JSON_UNQUOTE(JSON_EXTRACT(v.quality_rules_json, '$.promptContract')) = 'v2'
      AND v.status = 'published'
    GROUP BY v.template_id
) latest ON latest.template_id = t.id
JOIN article_prompt_template_version v
  ON v.template_id = latest.template_id AND v.version_no = latest.version_no
SET t.current_version_id = v.id,
    t.perspective_code = 'review_recommend',
    t.updated_at = NOW()
WHERE t.status = 'active' AND t.channel_group_code = 'forum';

-- 5. 权威媒体
UPDATE article_prompt_template t
JOIN (
    SELECT v.template_id, MAX(v.version_no) AS version_no
    FROM article_prompt_template_version v
    WHERE JSON_UNQUOTE(JSON_EXTRACT(v.quality_rules_json, '$.promptContract')) = 'v2'
      AND v.status = 'published'
    GROUP BY v.template_id
) latest ON latest.template_id = t.id
JOIN article_prompt_template_version v
  ON v.template_id = latest.template_id AND v.version_no = latest.version_no
SET t.current_version_id = v.id,
    t.perspective_code = 'industry_neutral',
    t.updated_at = NOW()
WHERE t.status = 'active' AND t.channel_group_code = 'authority_media';

-- 单模板回滚：先将 :template_id 替换为实际模板 ID，再执行。
UPDATE article_prompt_template t
JOIN article_prompt_template_version current_v ON current_v.id = t.current_version_id
SET t.current_version_id = CAST(JSON_UNQUOTE(JSON_EXTRACT(
        current_v.quality_rules_json, '$.previousCurrentVersionId')) AS UNSIGNED),
    t.perspective_code = COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
        current_v.quality_rules_json, '$.previousPerspectiveCode')), t.perspective_code),
    t.updated_at = NOW()
WHERE t.id = :template_id
  AND JSON_UNQUOTE(JSON_EXTRACT(current_v.quality_rules_json, '$.promptContract')) = 'v2';
