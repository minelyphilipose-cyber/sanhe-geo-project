-- ============================================================
-- V1: 系统用户表 + 默认管理员
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- 默认管理员  密码: admin123
-- BCrypt hash 通过 new BCryptPasswordEncoder().encode("admin123") 生成
INSERT INTO sys_user (username, password_hash, display_name, role) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '系统管理员', 'super_admin');
