-- ============================================================
-- V5: phase2 RBAC and user-role relations
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_role (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_key      VARCHAR(64)  NOT NULL UNIQUE,
    role_name     VARCHAR(128) NOT NULL,
    role_type     VARCHAR(16)  NOT NULL COMMENT 'internal|partner',
    status        VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active|inactive',
    is_builtin    TINYINT(1)   NOT NULL DEFAULT 1,
    sort_order    INT          NOT NULL DEFAULT 100,
    remark        VARCHAR(500) NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_sys_role_type (role_type),
    KEY idx_sys_role_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system role';

CREATE TABLE IF NOT EXISTS sys_permission (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    perm_key      VARCHAR(64)  NOT NULL UNIQUE,
    perm_name     VARCHAR(128) NOT NULL,
    module        VARCHAR(64)  NOT NULL,
    action        VARCHAR(32)  NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'active' COMMENT 'active|inactive',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_sys_permission_module (module),
    KEY idx_sys_permission_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system permission';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    role_id       BIGINT       NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_role (user_id, role_id),
    KEY idx_sys_user_role_user (user_id),
    KEY idx_sys_user_role_role (role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user role relation';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id       BIGINT       NOT NULL,
    permission_id BIGINT       NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
    KEY idx_sys_role_permission_role (role_id),
    KEY idx_sys_role_permission_perm (permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_sys_role_permission_perm FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='role permission relation';

INSERT INTO sys_role (role_key, role_name, role_type, status, is_builtin, sort_order)
SELECT 'super_admin', 'Super Admin', 'internal', 'active', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'super_admin');

INSERT INTO sys_role (role_key, role_name, role_type, status, is_builtin, sort_order)
SELECT 'manager', 'Manager', 'internal', 'active', 1, 10
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'manager');

INSERT INTO sys_role (role_key, role_name, role_type, status, is_builtin, sort_order)
SELECT 'delivery_manager', 'Delivery Manager', 'internal', 'active', 1, 20
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'delivery_manager');

INSERT INTO sys_role (role_key, role_name, role_type, status, is_builtin, sort_order)
SELECT 'operator', 'Operator', 'internal', 'active', 1, 30
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'operator');

INSERT INTO sys_role (role_key, role_name, role_type, status, is_builtin, sort_order)
SELECT 'sales', 'Sales', 'internal', 'active', 1, 40
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'sales');

INSERT INTO sys_role (role_key, role_name, role_type, status, is_builtin, sort_order)
SELECT 'partner', 'Partner Owner', 'partner', 'active', 1, 100
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'partner');

INSERT INTO sys_role (role_key, role_name, role_type, status, is_builtin, sort_order)
SELECT 'partner_staff', 'Partner Staff', 'partner', 'active', 1, 110
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'partner_staff');

INSERT INTO sys_role (role_key, role_name, role_type, status, is_builtin, sort_order)
SELECT 'partner_viewer', 'Partner Viewer', 'partner', 'active', 1, 120
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'partner_viewer');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'user.manage', 'User Manage', 'user', 'manage', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'user.manage');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'partner.read', 'Partner Read', 'partner', 'read', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'partner.read');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'partner.write', 'Partner Write', 'partner', 'write', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'partner.write');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'company.read', 'Company Read', 'company', 'read', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'company.read');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'company.write', 'Company Write', 'company', 'write', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'company.write');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'project.read', 'Project Read', 'project', 'read', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'project.read');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'project.write', 'Project Write', 'project', 'write', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'project.write');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key = 'user.manage'
WHERE r.role_key IN ('super_admin', 'manager')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('partner.read', 'partner.write', 'company.read', 'company.write', 'project.read', 'project.write')
WHERE r.role_key = 'super_admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('partner.read', 'partner.write', 'company.read', 'company.write', 'project.read', 'project.write')
WHERE r.role_key = 'manager'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('partner.read', 'company.read', 'company.write', 'project.read', 'project.write')
WHERE r.role_key IN ('delivery_manager', 'operator')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('company.read', 'company.write', 'project.read')
WHERE r.role_key = 'sales'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('partner.read', 'company.read', 'project.read')
WHERE r.role_key IN ('partner', 'partner_staff', 'partner_viewer')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_key = u.role
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user_role ur
    WHERE ur.user_id = u.id AND ur.role_id = r.id
);
