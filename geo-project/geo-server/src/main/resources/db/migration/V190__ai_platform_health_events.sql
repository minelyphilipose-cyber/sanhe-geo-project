-- ============================================================
-- V100: real AI platform health event stream
-- ============================================================

CREATE TABLE IF NOT EXISTS ai_platform_health_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    platform_code VARCHAR(64) NOT NULL COMMENT 'AI platform code',
    feature VARCHAR(64) NULL COMMENT 'LLM feature or call stage',
    event_type VARCHAR(32) NOT NULL COMMENT 'success/failure/rate_limited/permit_busy/circuit_open/slow_response',
    duration_ms BIGINT NULL COMMENT 'call duration in milliseconds',
    error_message VARCHAR(500) NULL COMMENT 'failure or health signal detail',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'event occurrence time',
    KEY idx_ai_platform_health_event_platform_time (platform_code, occurred_at),
    KEY idx_ai_platform_health_event_time (occurred_at),
    KEY idx_ai_platform_health_event_type_time (event_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='real AI platform health events';
