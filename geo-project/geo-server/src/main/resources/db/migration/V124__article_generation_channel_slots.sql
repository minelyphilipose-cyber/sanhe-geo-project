ALTER TABLE dispatch_task
    ADD COLUMN idempotency_key VARCHAR(256) NOT NULL DEFAULT '_legacy' COMMENT 'business idempotency key; content generation uses channel:slot' AFTER task_type,
    ADD COLUMN target_channel VARCHAR(32) NULL COMMENT 'content generation target channel' AFTER idempotency_key,
    ADD COLUMN generation_slot_no INT NULL COMMENT 'content generation slot number in period' AFTER target_channel;

ALTER TABLE dispatch_task
    DROP INDEX uk_dispatch_task_idempotent,
    ADD UNIQUE KEY uk_dispatch_task_idempotent (project_id, task_type, idempotency_key, window_start, window_end),
    ADD KEY idx_dispatch_content_slot (project_id, task_type, target_channel, generation_slot_no, window_start, window_end, status);

ALTER TABLE article_batch
    ADD COLUMN target_channel VARCHAR(32) NULL COMMENT 'redundant content generation target channel' AFTER project_id,
    ADD COLUMN generation_slot_no INT NULL COMMENT 'redundant content generation slot number; article_draft is business truth' AFTER target_channel;

ALTER TABLE article_draft
    ADD COLUMN target_channel VARCHAR(32) NULL COMMENT 'content generation target channel' AFTER project_id,
    ADD COLUMN period_type VARCHAR(16) NULL COMMENT 'quota period type from dispatch window' AFTER target_channel,
    ADD COLUMN period_key VARCHAR(32) NULL COMMENT 'quota period key from dispatch window' AFTER period_type,
    ADD COLUMN generation_slot_no INT NULL COMMENT 'content generation slot number; business truth' AFTER period_key,
    ADD KEY idx_article_draft_generation_quota (project_id, target_channel, period_type, period_key);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_status', 'cancelled', '已取消', 70
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_status' AND dict_key = 'cancelled'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'dispatch.task.release', 'Dispatch Task Release', 'dispatch', 'release', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'dispatch.task.release'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key = 'dispatch.task.release'
WHERE r.role_key = 'super_admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
