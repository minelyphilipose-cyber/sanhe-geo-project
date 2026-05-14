INSERT IGNORE INTO project_keyword_group_rel (project_id, keyword_group_id, created_at)
SELECT DISTINCT w.project_id, w.legacy_keyword_group_id, COALESCE(w.updated_at, NOW())
FROM geo_question_workorder w
JOIN keyword_group kg ON kg.id = w.legacy_keyword_group_id
WHERE w.status = 'committed'
  AND w.project_id IS NOT NULL
  AND w.legacy_keyword_group_id IS NOT NULL
  AND kg.deleted = 0;
