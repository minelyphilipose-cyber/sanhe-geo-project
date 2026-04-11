-- ============================================================
-- V3: company, brand, project and activity log
-- ============================================================

CREATE TABLE IF NOT EXISTS company (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_name    VARCHAR(200) NOT NULL,
    industry        VARCHAR(64) NULL,
    city            VARCHAR(64) NULL,
    owner_type      VARCHAR(16) NOT NULL COMMENT 'direct|partner|joint',
    partner_id      BIGINT NULL,
    sales_owner_id  BIGINT NULL,
    referral_source VARCHAR(128) NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'potential' COMMENT 'potential|signed|inactive',
    remark          VARCHAR(500) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_company_name (company_name),
    KEY idx_company_owner_type (owner_type),
    KEY idx_company_partner_id (partner_id),
    KEY idx_company_sales_owner_id (sales_owner_id),
    CONSTRAINT fk_company_partner FOREIGN KEY (partner_id) REFERENCES partner(id),
    CONSTRAINT fk_company_sales_owner FOREIGN KEY (sales_owner_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='customer company';

CREATE TABLE IF NOT EXISTS brand (
    id                        BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id                BIGINT NOT NULL,
    brand_name                VARCHAR(128) NOT NULL,
    brand_slug                VARCHAR(128) NOT NULL,
    main_business             VARCHAR(255) NULL,
    service_area              VARCHAR(255) NULL,
    website                   VARCHAR(255) NULL,
    phone                     VARCHAR(20) NULL,
    wechat                    VARCHAR(64) NULL,
    description               TEXT NULL,
    standard_brand_statement  TEXT NULL,
    forbidden_phrases         TEXT NULL,
    status                    VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'draft|active|archived',
    created_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_brand_company_slug (company_id, brand_slug),
    KEY idx_brand_company_id (company_id),
    KEY idx_brand_name (brand_name),
    CONSTRAINT fk_brand_company FOREIGN KEY (company_id) REFERENCES company(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='brand profile';

CREATE TABLE IF NOT EXISTS project (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_code    VARCHAR(32) NOT NULL,
    brand_id        BIGINT NOT NULL,
    project_name    VARCHAR(200) NOT NULL,
    package_type    VARCHAR(32) NOT NULL COMMENT 'trial_6980|standard_12800|growth_26800',
    package_price   BIGINT NOT NULL COMMENT 'cent',
    service_months  INT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'draft' COMMENT 'draft|active|paused|dispute|completed|archived',
    stage           VARCHAR(32) NOT NULL DEFAULT 'pending_start',
    owner_type      VARCHAR(16) NOT NULL COMMENT 'direct|partner|joint',
    partner_id      BIGINT NULL,
    delivery_mode   VARCHAR(32) NOT NULL DEFAULT 'managed',
    signed_at       DATETIME NULL,
    start_date      DATE NULL,
    end_date        DATE NULL,
    primary_goal    VARCHAR(500) NULL,
    created_by      BIGINT NOT NULL,
    remark          VARCHAR(500) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_code (project_code),
    KEY idx_project_brand_id (brand_id),
    KEY idx_project_partner_id (partner_id),
    KEY idx_project_status (status),
    KEY idx_project_stage (stage),
    KEY idx_project_signed_at (signed_at),
    CONSTRAINT fk_project_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
    CONSTRAINT fk_project_partner FOREIGN KEY (partner_id) REFERENCES partner(id),
    CONSTRAINT fk_project_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='delivery project';

CREATE TABLE IF NOT EXISTS activity_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NULL,
    action          VARCHAR(64) NOT NULL,
    target_type     VARCHAR(64) NOT NULL,
    target_id       BIGINT NULL,
    detail_json     JSON NULL,
    ip_address      VARCHAR(45) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_activity_user_id (user_id),
    KEY idx_activity_target (target_type, target_id),
    KEY idx_activity_created_at (created_at),
    CONSTRAINT fk_activity_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='activity log';
