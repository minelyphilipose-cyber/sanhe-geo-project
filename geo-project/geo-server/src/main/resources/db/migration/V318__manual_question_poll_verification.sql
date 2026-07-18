ALTER TABLE poll_batches
    ADD COLUMN client_request_id VARCHAR(64) NULL
        COMMENT 'manual verification idempotency key'
        AFTER created_by,
    ADD COLUMN request_fingerprint CHAR(64) NULL
        COMMENT 'SHA-256 of normalized manual verification request'
        AFTER client_request_id,
    ADD COLUMN manual_question_limit INT NULL
        COMMENT 'requested question limit for manual verification'
        AFTER request_fingerprint,
    ADD UNIQUE KEY uk_poll_batch_manual_request (created_by, client_request_id);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'dispatch.question_poll.manual', 'Manual Question Poll Verification', 'dispatch', 'question_poll_manual', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'dispatch.question_poll.manual'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key = 'dispatch.question_poll.manual'
WHERE r.role_key IN ('manager', 'super_admin')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
