-- ============================================================
-- V16: AI platform config management
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_platform_config (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    platform_code    VARCHAR(64) NOT NULL COMMENT 'platform unique code, e.g. doubao/deepseek/qwen',
    platform_name    VARCHAR(128) NOT NULL COMMENT 'platform display name',
    priority_level   VARCHAR(8) NOT NULL COMMENT 'P0|P1|P2',
    api_key          VARCHAR(512) NOT NULL COMMENT 'OpenAI-compatible api key',
    api_url          VARCHAR(255) NOT NULL COMMENT 'OpenAI-compatible base url',
    model_id         VARCHAR(128) NOT NULL COMMENT 'OpenAI-compatible model id',
    model_name       VARCHAR(128) NOT NULL COMMENT 'model display name',
    enabled          TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'enabled status',
    remaining_quota  DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT 'remaining quota',
    used_quota       DECIMAL(18,4) NOT NULL DEFAULT 0.0000 COMMENT 'used quota',
    degraded         TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether degraded fallback is enabled',
    remark           VARCHAR(500) NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_platform_code (platform_code),
    KEY idx_ai_platform_priority (priority_level),
    KEY idx_ai_platform_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI platform configuration';
