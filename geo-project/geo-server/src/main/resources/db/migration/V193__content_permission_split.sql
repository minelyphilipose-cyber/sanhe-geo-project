-- ============================================================
-- V193: content permission split
-- Moves content execution/configuration checks off project.update/project.write.
-- Notes:
--   * content execution is granted to operator and delivery_manager, not manager
--   * manager receives project.update only after content write checks have been split
--   * manager receives content template management, which is system/configuration work
--   * legacy project.write remains for frozen partner compatibility but is removed from manager
-- ============================================================

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT perm_key, perm_name, module, action, 'active'
FROM (
    SELECT 'content.read' perm_key, 'Content Read' perm_name, 'content' module, 'read' action
    UNION ALL SELECT 'content.article.write', 'Content Article Write', 'content_article', 'write'
    UNION ALL SELECT 'content.ai.generate', 'Content AI Generate', 'content_ai', 'generate'
    UNION ALL SELECT 'content.distribution.operate', 'Content Distribution Operate', 'content_distribution', 'operate'
    UNION ALL SELECT 'content.distribution.retry', 'Content Distribution Retry', 'content_distribution', 'retry'
    UNION ALL SELECT 'content.publish.operate', 'Content Publish Operate', 'content_publish', 'operate'
    UNION ALL SELECT 'content.prompt_template.manage', 'Content Prompt Template Manage', 'content_template', 'prompt_manage'
    UNION ALL SELECT 'content.wechat_template.manage', 'Content WeChat Template Manage', 'content_template', 'wechat_manage'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.perm_key = seed.perm_key
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'content.read',
    'content.article.write',
    'content.ai.generate',
    'content.distribution.operate',
    'content.distribution.retry',
    'content.publish.operate'
)
WHERE r.role_key = 'operator'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'content.read',
    'content.article.write',
    'content.ai.generate',
    'content.distribution.operate',
    'content.distribution.retry',
    'content.publish.operate'
)
WHERE r.role_key = 'delivery_manager'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'project.update',
    'content.read',
    'content.prompt_template.manage',
    'content.wechat_template.manage'
)
WHERE r.role_key = 'manager'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- manager must not keep legacy delivery/content execution grants.
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key = 'manager'
  AND p.perm_key IN (
      'project.write',
      'content.article.write',
      'content.ai.generate',
      'content.distribution.operate',
      'content.distribution.retry',
      'content.publish.operate'
  );

DROP PROCEDURE IF EXISTS v193_assert_role_permission;

DELIMITER $$
CREATE PROCEDURE v193_assert_role_permission(
    IN role_key_value VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN perm_key_value VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    IN should_have TINYINT
)
BEGIN
    DECLARE actual_count INT DEFAULT 0;
    DECLARE error_message VARCHAR(255);

    SELECT COUNT(*) INTO actual_count
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE CONVERT(r.role_key USING utf8mb4) COLLATE utf8mb4_unicode_ci = role_key_value
      AND CONVERT(p.perm_key USING utf8mb4) COLLATE utf8mb4_unicode_ci = perm_key_value
      AND CONVERT(p.status USING utf8mb4) COLLATE utf8mb4_unicode_ci IN ('active', 'deprecated');

    IF should_have = 1 AND actual_count = 0 THEN
        SET error_message = CONCAT('V193 ASSERT FAILED: ', role_key_value, ' should have ', perm_key_value);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;

    IF should_have = 0 AND actual_count > 0 THEN
        SET error_message = CONCAT('V193 ASSERT FAILED: ', role_key_value, ' should not have ', perm_key_value);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;
END$$
DELIMITER ;

-- operator: content execution within owner scope, no template/system content config.
CALL v193_assert_role_permission('operator', 'content.read', 1);
CALL v193_assert_role_permission('operator', 'content.article.write', 1);
CALL v193_assert_role_permission('operator', 'content.ai.generate', 1);
CALL v193_assert_role_permission('operator', 'content.distribution.operate', 1);
CALL v193_assert_role_permission('operator', 'content.distribution.retry', 1);
CALL v193_assert_role_permission('operator', 'content.publish.operate', 1);
CALL v193_assert_role_permission('operator', 'content.prompt_template.manage', 0);
CALL v193_assert_role_permission('operator', 'content.wechat_template.manage', 0);

-- delivery_manager: global fallback for content execution, not template/system content config.
CALL v193_assert_role_permission('delivery_manager', 'content.read', 1);
CALL v193_assert_role_permission('delivery_manager', 'content.article.write', 1);
CALL v193_assert_role_permission('delivery_manager', 'content.ai.generate', 1);
CALL v193_assert_role_permission('delivery_manager', 'content.distribution.operate', 1);
CALL v193_assert_role_permission('delivery_manager', 'content.distribution.retry', 1);
CALL v193_assert_role_permission('delivery_manager', 'content.publish.operate', 1);
CALL v193_assert_role_permission('delivery_manager', 'content.prompt_template.manage', 0);
CALL v193_assert_role_permission('delivery_manager', 'content.wechat_template.manage', 0);

-- manager: project base correction and content configuration only; no content execution.
CALL v193_assert_role_permission('manager', 'project.update', 1);
CALL v193_assert_role_permission('manager', 'project.write', 0);
CALL v193_assert_role_permission('manager', 'content.read', 1);
CALL v193_assert_role_permission('manager', 'content.prompt_template.manage', 1);
CALL v193_assert_role_permission('manager', 'content.wechat_template.manage', 1);
CALL v193_assert_role_permission('manager', 'content.article.write', 0);
CALL v193_assert_role_permission('manager', 'content.ai.generate', 0);
CALL v193_assert_role_permission('manager', 'content.distribution.operate', 0);
CALL v193_assert_role_permission('manager', 'content.distribution.retry', 0);
CALL v193_assert_role_permission('manager', 'content.publish.operate', 0);
CALL v193_assert_role_permission('manager', 'project.start', 0);
CALL v193_assert_role_permission('manager', 'project.pause', 0);
CALL v193_assert_role_permission('manager', 'project.terminate', 0);

DROP PROCEDURE IF EXISTS v193_assert_role_permission;
