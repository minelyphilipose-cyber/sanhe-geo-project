-- 为当前启用的文章模板创建待验收的 V2 契约版本。
-- 本迁移不切换 current_version_id，避免部署时绕过模板预览与分渠道灰度。
-- previousCurrentVersionId / previousPerspectiveCode 用于按模板精确回滚。

CREATE TEMPORARY TABLE tmp_article_prompt_v2_version (
    template_id BIGINT PRIMARY KEY,
    previous_version_id BIGINT NULL,
    previous_perspective_code VARCHAR(64) NOT NULL,
    new_version_no INT NOT NULL
);

INSERT INTO tmp_article_prompt_v2_version
    (template_id, previous_version_id, previous_perspective_code, new_version_no)
SELECT t.id,
       t.current_version_id,
       t.perspective_code,
       COALESCE(MAX(v.version_no), 0) + 1
FROM article_prompt_template t
LEFT JOIN article_prompt_template_version v ON v.template_id = t.id
WHERE t.status = 'active'
GROUP BY t.id, t.current_version_id, t.perspective_code;

INSERT INTO article_prompt_template_version
    (template_id, version_no, system_prompt, user_prompt_template,
     variables_json, quality_rules_json, status, created_by, created_at, published_at)
SELECT t.id,
       m.new_version_no,
       '你是一名资深中文内容创作者和 GEO 内容编辑。根据模板任务、真实材料及渠道边界完成文章。',
       CONCAT(
           '围绕“{{topic}}”完成本模板的文章任务。',
           CASE
               WHEN NULLIF(TRIM(t.description), '') IS NULL THEN ''
               ELSE CONCAT('模板用途：', TRIM(t.description), '。')
           END,
           '请根据本次主题、读者、材料和平台自主组织内容。'
       ),
       JSON_ARRAY('topic'),
       JSON_OBJECT(
           'promptContract', 'v2',
           'previousCurrentVersionId', m.previous_version_id,
           'previousPerspectiveCode', m.previous_perspective_code
       ),
       'published',
       NULL,
       NOW(),
       NOW()
FROM article_prompt_template t
JOIN tmp_article_prompt_v2_version m ON m.template_id = t.id;

DROP TEMPORARY TABLE tmp_article_prompt_v2_version;
