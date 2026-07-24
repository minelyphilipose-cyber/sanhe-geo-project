-- 仅用于测试环境：将所有启用模板一次性切换到各自最新的已发布 V2 版本。
-- 不要将本文件加入 Flyway 自动迁移；生产环境切换应单独确认。

START TRANSACTION;

UPDATE article_prompt_template t
JOIN article_prompt_template_version v
  ON v.template_id = t.id
 AND v.status = 'published'
 AND JSON_UNQUOTE(JSON_EXTRACT(v.quality_rules_json, '$.promptContract')) = 'v2'
LEFT JOIN article_prompt_template_version newer
  ON newer.template_id = v.template_id
 AND newer.status = 'published'
 AND JSON_UNQUOTE(JSON_EXTRACT(newer.quality_rules_json, '$.promptContract')) = 'v2'
 AND (newer.version_no > v.version_no
      OR (newer.version_no = v.version_no AND newer.id > v.id))
SET t.current_version_id = v.id,
    t.updated_at = NOW()
WHERE t.status = 'active'
  AND newer.id IS NULL;

-- 结果必须满足：active_template_count = current_v2_template_count，missing_v2_count = 0。
SELECT COUNT(*) AS active_template_count
FROM article_prompt_template
WHERE status = 'active';

SELECT COUNT(*) AS current_v2_template_count
FROM article_prompt_template t
JOIN article_prompt_template_version v ON v.id = t.current_version_id
WHERE t.status = 'active'
  AND v.status = 'published'
  AND JSON_UNQUOTE(JSON_EXTRACT(v.quality_rules_json, '$.promptContract')) = 'v2';

SELECT COUNT(*) AS missing_v2_count
FROM article_prompt_template t
LEFT JOIN article_prompt_template_version v ON v.id = t.current_version_id
WHERE t.status = 'active'
  AND (v.id IS NULL
       OR v.status <> 'published'
       OR JSON_UNQUOTE(JSON_EXTRACT(v.quality_rules_json, '$.promptContract')) <> 'v2'
       OR JSON_UNQUOTE(JSON_EXTRACT(v.quality_rules_json, '$.promptContract')) IS NULL);

COMMIT;

-- 如需在测试环境整体回滚，单独执行下列事务。
-- previousCurrentVersionId 由 V319 创建 V2 候选版本时写入。
/*
START TRANSACTION;

UPDATE article_prompt_template t
JOIN article_prompt_template_version v ON v.id = t.current_version_id
SET t.current_version_id = CAST(
        JSON_UNQUOTE(JSON_EXTRACT(v.quality_rules_json, '$.previousCurrentVersionId')) AS UNSIGNED
    ),
    t.updated_at = NOW()
WHERE t.status = 'active'
  AND JSON_UNQUOTE(JSON_EXTRACT(v.quality_rules_json, '$.promptContract')) = 'v2'
  AND JSON_EXTRACT(v.quality_rules_json, '$.previousCurrentVersionId') IS NOT NULL
  AND JSON_TYPE(JSON_EXTRACT(v.quality_rules_json, '$.previousCurrentVersionId')) <> 'NULL';

COMMIT;
*/
