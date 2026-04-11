-- ============================================================
-- V1: system users and default admin
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(64)  NOT NULL,
    role            VARCHAR(32)  NOT NULL COMMENT 'super_admin|sales|operator|delivery_manager|manager|partner|partner_staff|partner_viewer',
    partner_id      BIGINT       NULL,
    phone           VARCHAR(20)  NULL,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    last_login_at   DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_role (role),
    INDEX idx_partner (partner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system user';

-- default admin / admin123
-- INSERT INTO sys_user (username, password_hash, display_name, role)
-- SELECT 'admin', '$2a$10$tEiif0.NbEDSEiD3jXCFr.GZRSE/pavA4B7uNnT9La3is4aH1oDwm', 'System Admin', 'super_admin'
-- WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');
