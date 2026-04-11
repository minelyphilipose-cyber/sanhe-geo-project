-- ============================================================
-- V2: partner and virtual account base
-- ============================================================


CREATE TABLE IF NOT EXISTS partner (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    partner_code    VARCHAR(32) NOT NULL,
    partner_name    VARCHAR(128) NOT NULL,
    partner_level   VARCHAR(32) NOT NULL COMMENT 'level_29800|level_59800|level_99800',
    discount_rate   DECIMAL(5,4) NOT NULL COMMENT '0.3000|0.2500|0.2000',
    status          VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'pending|active|paused|closed',
    contact_name    VARCHAR(64) NULL,
    contact_phone   VARCHAR(20) NULL,
    city            VARCHAR(64) NULL,
    remark          VARCHAR(500) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_partner_code (partner_code),
    KEY idx_partner_level (partner_level),
    KEY idx_partner_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='city partner';

CREATE TABLE IF NOT EXISTS partner_account (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    partner_id      BIGINT NOT NULL,
    current_balance BIGINT NOT NULL DEFAULT 0 COMMENT 'cent',
    total_recharge  BIGINT NOT NULL DEFAULT 0 COMMENT 'cent',
    total_deduction BIGINT NOT NULL DEFAULT 0 COMMENT 'cent',
    currency        VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status          VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|frozen|closed',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_partner_account_partner_id (partner_id),
    KEY idx_partner_account_status (status),
    CONSTRAINT fk_partner_account_partner FOREIGN KEY (partner_id) REFERENCES partner(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='partner virtual account';

CREATE TABLE IF NOT EXISTS partner_account_txn (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    partner_id        BIGINT NOT NULL,
    account_id        BIGINT NOT NULL,
    txn_no            VARCHAR(64) NOT NULL,
    txn_type          VARCHAR(32) NOT NULL COMMENT 'recharge|deduction|manual_adjust',
    biz_type          VARCHAR(32) NOT NULL COMMENT 'partner_prepaid|project_signing|finance_adjust',
    amount            BIGINT NOT NULL,
    balance_before    BIGINT NOT NULL,
    balance_after     BIGINT NOT NULL,
    related_project_id BIGINT NULL,
    operator_user_id  BIGINT NOT NULL,
    offline_reference VARCHAR(128) NULL,
    remark            VARCHAR(500) NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_partner_account_txn_no (txn_no),
    KEY idx_pat_partner_id (partner_id),
    KEY idx_pat_account_id (account_id),
    KEY idx_pat_project_id (related_project_id),
    KEY idx_pat_created_at (created_at),
    CONSTRAINT fk_pat_partner FOREIGN KEY (partner_id) REFERENCES partner(id),
    CONSTRAINT fk_pat_account FOREIGN KEY (account_id) REFERENCES partner_account(id),
    CONSTRAINT fk_pat_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='partner account transactions';
