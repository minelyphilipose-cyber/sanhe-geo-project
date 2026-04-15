-- ============================================================
-- V48: keyword groups and affix word dictionaries
-- ============================================================

CREATE TABLE IF NOT EXISTS keyword_group (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(64) NOT NULL,
    `type`      VARCHAR(16) NOT NULL,
    remark      VARCHAR(255) NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_type_updated (`type`, updated_at),
    KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='keyword groups';

CREATE TABLE IF NOT EXISTS keyword_group_word (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id     BIGINT NOT NULL,
    column_type  VARCHAR(16) NOT NULL,
    word_text    VARCHAR(64) NOT NULL,
    sort_order   INT NOT NULL DEFAULT 100,
    source       VARCHAR(16) NOT NULL DEFAULT 'custom',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_group_column_sort (group_id, column_type, sort_order, id),
    CONSTRAINT fk_keyword_group_word_group FOREIGN KEY (group_id) REFERENCES keyword_group(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='keyword words for each group and column';

CREATE TABLE IF NOT EXISTS keyword_affix_word (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    `type`       VARCHAR(16) NOT NULL,
    affix_kind   VARCHAR(16) NOT NULL,
    word_text    VARCHAR(64) NOT NULL,
    sort_order   INT NOT NULL DEFAULT 100,
    enabled      TINYINT(1) NOT NULL DEFAULT 1,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_type_kind_word (`type`, affix_kind, word_text),
    KEY idx_type_kind_enabled_sort (`type`, affix_kind, enabled, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='affix words dictionary by type';

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'keyword_group.read', 'Keyword Group Read', 'keyword_group', 'read', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'keyword_group.read');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'keyword_group.write', 'Keyword Group Write', 'keyword_group', 'write', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'keyword_group.write');

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'keyword_affix.manage', 'Keyword Affix Manage', 'keyword_affix', 'manage', 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_key = 'keyword_affix.manage');

-- super_admin
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('keyword_group.read', 'keyword_group.write', 'keyword_affix.manage')
WHERE r.role_key = 'super_admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission x
      WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- manager
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('keyword_group.read', 'keyword_group.write', 'keyword_affix.manage')
WHERE r.role_key = 'manager'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission x
      WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- delivery_manager
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('keyword_group.read', 'keyword_group.write')
WHERE r.role_key = 'delivery_manager'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission x
      WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- operator
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('keyword_group.read', 'keyword_group.write')
WHERE r.role_key = 'operator'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission x
      WHERE x.role_id = r.id AND x.permission_id = p.id
  );

-- sales
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key IN ('keyword_group.read')
WHERE r.role_key = 'sales'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission x
      WHERE x.role_id = r.id AND x.permission_id = p.id
  );
