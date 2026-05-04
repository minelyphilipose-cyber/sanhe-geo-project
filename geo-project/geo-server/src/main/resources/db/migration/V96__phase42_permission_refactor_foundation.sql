-- ============================================================
-- V96: phase42 permission refactor foundation
-- 1) granular permissions and role grants
-- 2) deprecated legacy permissions
-- 3) discount history and recharge order tables
-- 4) soft-delete columns for core business tables
-- ============================================================

ALTER TABLE sys_role
    ADD COLUMN data_scope VARCHAR(32) NOT NULL DEFAULT 'all' COMMENT 'all|partner_self|custom' AFTER role_type;

UPDATE sys_role
SET data_scope = 'partner_self'
WHERE role_type = 'partner';

UPDATE sys_role
SET data_scope = 'all'
WHERE role_type = 'internal';

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT perm_key, perm_name, module, action, 'active'
FROM (
    SELECT 'brand.read' perm_key, 'Brand Read' perm_name, 'brand' module, 'read' action
    UNION ALL SELECT 'brand.create', 'Brand Create', 'brand', 'create'
    UNION ALL SELECT 'brand.update', 'Brand Update', 'brand', 'update'
    UNION ALL SELECT 'brand.delete', 'Brand Delete', 'brand', 'delete'
    UNION ALL SELECT 'brand.material.upload', 'Brand Material Upload', 'brand_material', 'upload'
    UNION ALL SELECT 'brand.material.delete', 'Brand Material Delete', 'brand_material', 'delete'
    UNION ALL SELECT 'company.create', 'Company Create', 'company', 'create'
    UNION ALL SELECT 'company.update', 'Company Update', 'company', 'update'
    UNION ALL SELECT 'company.delete', 'Company Delete', 'company', 'delete'
    UNION ALL SELECT 'company.account.adjust', 'Company Account Adjust', 'company_account', 'adjust'
    UNION ALL SELECT 'package.read', 'Package Read', 'package', 'read'
    UNION ALL SELECT 'package.manage', 'Package Manage', 'package', 'manage'
    UNION ALL SELECT 'partner.create', 'Partner Create', 'partner', 'create'
    UNION ALL SELECT 'partner.update', 'Partner Update', 'partner', 'update'
    UNION ALL SELECT 'partner.status.update', 'Partner Status Update', 'partner', 'status_update'
    UNION ALL SELECT 'partner.discount.update', 'Partner Discount Update', 'partner', 'discount_update'
    UNION ALL SELECT 'partner.account.read', 'Partner Account Read', 'partner_account', 'read'
    UNION ALL SELECT 'partner.account.recharge.apply', 'Partner Recharge Apply', 'partner_account', 'recharge_apply'
    UNION ALL SELECT 'partner.account.recharge.audit', 'Partner Recharge Audit', 'partner_account', 'recharge_audit'
    UNION ALL SELECT 'partner.account.adjust', 'Partner Account Adjust', 'partner_account', 'adjust'
    UNION ALL SELECT 'partner.account.txn.read', 'Partner Account Transaction Read', 'partner_account', 'txn_read'
    UNION ALL SELECT 'partner.staff.manage', 'Partner Staff Manage', 'partner_staff', 'manage'
    UNION ALL SELECT 'project.create', 'Project Create', 'project', 'create'
    UNION ALL SELECT 'project.update', 'Project Update', 'project', 'update'
    UNION ALL SELECT 'project.start', 'Project Start', 'project', 'start'
    UNION ALL SELECT 'project.pause', 'Project Pause', 'project', 'pause'
    UNION ALL SELECT 'project.terminate', 'Project Terminate', 'project', 'terminate'
    UNION ALL SELECT 'project.delete', 'Project Delete', 'project', 'delete'
    UNION ALL SELECT 'project.report.read', 'Project Report Read', 'project_report', 'read'
    UNION ALL SELECT 'project.report.export', 'Project Report Export', 'project_report', 'export'
    UNION ALL SELECT 'role.manage', 'Role Manage', 'role', 'manage'
    UNION ALL SELECT 'permission.manage', 'Permission Manage', 'permission', 'manage'
    UNION ALL SELECT 'activity_log.read', 'Activity Log Read', 'activity_log', 'read'
    UNION ALL SELECT 'activity_log.finance.read', 'Finance Activity Log Read', 'activity_log', 'finance_read'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.perm_key = seed.perm_key
);

UPDATE sys_permission
SET status = 'deprecated'
WHERE perm_key IN (
    'company.write',
    'project.write',
    'project.status.activate',
    'project.status.close',
    'project.status.update',
    'project.flow.update',
    'project.sign_and_deduct',
    'partner.write'
);

DROP PROCEDURE IF EXISTS v96_assert_grants;

DELIMITER $$
CREATE PROCEDURE v96_assert_grants(
    IN role_key_value VARCHAR(64),
    IN actual_count INT,
    IN expected_min INT
)
BEGIN
    DECLARE error_message VARCHAR(255);

    IF actual_count < expected_min THEN
        SET error_message = CONCAT(
            'V96 GRANTS FAILED: ',
            role_key_value,
            ' has ',
            actual_count,
            ' active grants, expected at least ',
            expected_min
        );
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = error_message;
    END IF;
END$$
DELIMITER ;

-- Keep existing bindings in place, but add explicit granular grants.
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'user.manage', 'role.manage', 'permission.manage',
    'partner.read', 'partner.create', 'partner.update', 'partner.status.update', 'partner.discount.update',
    'partner.account.read', 'partner.account.recharge.audit', 'partner.account.adjust', 'partner.account.txn.read',
    'company.read', 'company.create', 'company.update', 'company.delete', 'company.account.adjust',
    'brand.read', 'brand.create', 'brand.update', 'brand.delete', 'brand.material.upload', 'brand.material.delete', 'brand.statement.lock',
    'project.read', 'project.create', 'project.update', 'project.start', 'project.pause', 'project.terminate', 'project.delete',
    'project.report.read', 'project.report.export',
    'package.read', 'package.manage',
    'question_pool.core.confirm', 'question_pool.core.delete', 'report.review',
    'dispatch.alert.resolve', 'dispatch.task.replay.dead_letter',
    'keyword_group.read', 'keyword_group.write', 'keyword_affix.manage',
    'activity_log.read', 'activity_log.finance.read'
)
WHERE r.role_key IN ('super_admin', 'manager')
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

SET @super_admin_grants = (
    SELECT COUNT(*)
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'super_admin' AND p.status = 'active'
);
SELECT IF(
    @super_admin_grants >= 45,
    'ok',
    CONCAT('V96 GRANTS FAILED: super_admin has ', @super_admin_grants, ' active grants, expected at least 45')
) AS check_super_admin_grants;
CALL v96_assert_grants('super_admin', @super_admin_grants, 45);

SET @manager_grants = (
    SELECT COUNT(*)
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'manager' AND p.status = 'active'
);
SELECT IF(
    @manager_grants >= 45,
    'ok',
    CONCAT('V96 GRANTS FAILED: manager has ', @manager_grants, ' active grants, expected at least 45')
) AS check_manager_grants;
CALL v96_assert_grants('manager', @manager_grants, 45);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'partner.read',
    'company.read', 'company.create', 'company.update',
    'brand.read', 'brand.create', 'brand.update', 'brand.material.upload', 'brand.material.delete',
    'project.read', 'project.create', 'project.update', 'project.start', 'project.pause', 'project.terminate',
    'project.report.read', 'project.report.export',
    'keyword_group.read', 'keyword_group.write',
    'package.read',
    'activity_log.read'
)
WHERE r.role_key IN ('delivery_manager', 'operator')
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

SET @operator_grants = (
    SELECT COUNT(*)
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'operator' AND p.status = 'active'
);
SELECT IF(
    @operator_grants >= 21,
    'ok',
    CONCAT('V96 GRANTS FAILED: operator has ', @operator_grants, ' active grants, expected at least 21')
) AS check_operator_grants;
CALL v96_assert_grants('operator', @operator_grants, 21);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'brand.statement.lock',
    'question_pool.core.confirm', 'question_pool.core.delete', 'report.review',
    'dispatch.alert.resolve'
)
WHERE r.role_key = 'delivery_manager'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

SET @delivery_manager_grants = (
    SELECT COUNT(*)
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'delivery_manager' AND p.status = 'active'
);
SELECT IF(
    @delivery_manager_grants >= 26,
    'ok',
    CONCAT('V96 GRANTS FAILED: delivery_manager has ', @delivery_manager_grants, ' active grants, expected at least 26')
) AS check_delivery_manager_grants;
CALL v96_assert_grants('delivery_manager', @delivery_manager_grants, 26);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'company.read', 'brand.read', 'project.read', 'project.report.read', 'keyword_group.read'
)
WHERE r.role_key = 'sales'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

SET @sales_grants = (
    SELECT COUNT(*)
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'sales' AND p.status = 'active'
);
SELECT IF(
    @sales_grants >= 5,
    'ok',
    CONCAT('V96 GRANTS FAILED: sales has ', @sales_grants, ' active grants, expected at least 5')
) AS check_sales_grants;
CALL v96_assert_grants('sales', @sales_grants, 5);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'partner.read', 'partner.account.read', 'partner.account.recharge.apply', 'partner.account.txn.read', 'partner.staff.manage',
    'company.read', 'company.create', 'company.update', 'company.delete',
    'brand.read', 'brand.create', 'brand.update', 'brand.delete', 'brand.material.upload', 'brand.material.delete',
    'project.read', 'project.create', 'project.update', 'project.start',
    'project.report.read', 'project.report.export',
    'package.read'
)
WHERE r.role_key = 'partner'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

SET @partner_grants = (
    SELECT COUNT(*)
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'partner' AND p.status = 'active'
);
SELECT IF(
    @partner_grants >= 22,
    'ok',
    CONCAT('V96 GRANTS FAILED: partner has ', @partner_grants, ' active grants, expected at least 22')
) AS check_partner_grants;
CALL v96_assert_grants('partner', @partner_grants, 22);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'partner.read',
    'company.read', 'company.create', 'company.update',
    'brand.read', 'brand.create', 'brand.update', 'brand.material.upload', 'brand.material.delete',
    'project.read', 'project.create', 'project.update',
    'project.report.read',
    'package.read'
)
WHERE r.role_key = 'partner_staff'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN (
    'partner.read', 'company.read', 'brand.read', 'project.read', 'project.report.read', 'package.read'
)
WHERE r.role_key = 'partner_viewer'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

SET @partner_viewer_grants = (
    SELECT COUNT(*)
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'partner_viewer' AND p.status = 'active'
);
SELECT IF(
    @partner_viewer_grants >= 6,
    'ok',
    CONCAT('V96 GRANTS FAILED: partner_viewer has ', @partner_viewer_grants, ' active grants, expected at least 6')
) AS check_partner_viewer_grants;
CALL v96_assert_grants('partner_viewer', @partner_viewer_grants, 6);

-- Explicitly revoke money/start/staff-management grants from partner_staff if any historical seed added them.
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_key = 'partner_staff'
  AND (
      p.perm_key = 'project.start'
      OR p.perm_key LIKE 'partner.account.%'
      OR p.perm_key = 'partner.staff.manage'
  );

SET @partner_staff_grants = (
    SELECT COUNT(*)
    FROM sys_role_permission rp
    JOIN sys_role r ON r.id = rp.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE r.role_key = 'partner_staff' AND p.status = 'active'
);
SELECT IF(
    @partner_staff_grants >= 14,
    'ok',
    CONCAT('V96 GRANTS FAILED: partner_staff has ', @partner_staff_grants, ' active grants, expected at least 14')
) AS check_partner_staff_grants;
CALL v96_assert_grants('partner_staff', @partner_staff_grants, 14);

DROP PROCEDURE IF EXISTS v96_assert_grants;

CREATE TABLE IF NOT EXISTS partner_discount_history (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    partner_id         BIGINT        NOT NULL,
    old_discount_rate  DECIMAL(10,4) NULL,
    new_discount_rate  DECIMAL(10,4) NOT NULL,
    operator_user_id   BIGINT        NOT NULL,
    reason             VARCHAR(500)  NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_partner_discount_history_partner (partner_id),
    KEY idx_partner_discount_history_operator (operator_user_id),
    CONSTRAINT fk_partner_discount_history_partner FOREIGN KEY (partner_id) REFERENCES partner(id),
    CONSTRAINT fk_partner_discount_history_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='partner discount change history';

CREATE TABLE IF NOT EXISTS partner_recharge_order (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no           VARCHAR(64)   NOT NULL UNIQUE,
    partner_id         BIGINT        NOT NULL,
    amount             DECIMAL(18,2) NOT NULL,
    status             VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending|cancelled|approved|rejected|expired',
    offline_reference  VARCHAR(128)  NULL,
    apply_remark       VARCHAR(500)  NULL,
    reject_reason      VARCHAR(500)  NULL,
    applicant_user_id  BIGINT        NOT NULL,
    audited_by         BIGINT        NULL,
    audited_at         DATETIME      NULL,
    account_txn_id     BIGINT        NULL,
    expires_at         DATETIME      NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_partner_recharge_order_partner (partner_id, status),
    KEY idx_partner_recharge_order_applicant (applicant_user_id),
    KEY idx_partner_recharge_order_auditor (audited_by),
    CONSTRAINT fk_partner_recharge_order_partner FOREIGN KEY (partner_id) REFERENCES partner(id),
    CONSTRAINT fk_partner_recharge_order_applicant FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_partner_recharge_order_auditor FOREIGN KEY (audited_by) REFERENCES sys_user(id),
    CONSTRAINT fk_partner_recharge_order_txn FOREIGN KEY (account_txn_id) REFERENCES partner_account_txn(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='partner recharge application order';

ALTER TABLE company
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at,
    ADD COLUMN deleted_by BIGINT NULL AFTER deleted_at,
    ADD KEY idx_company_deleted_at (deleted_at);

ALTER TABLE brand
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at,
    ADD COLUMN deleted_by BIGINT NULL AFTER deleted_at,
    ADD KEY idx_brand_deleted_at (deleted_at);

ALTER TABLE project
    ADD COLUMN deleted_at DATETIME NULL AFTER updated_at,
    ADD COLUMN deleted_by BIGINT NULL AFTER deleted_at,
    ADD KEY idx_project_deleted_at (deleted_at);
