CREATE TABLE IF NOT EXISTS douyin_webhook_events (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(128) NULL COMMENT '抖音事件唯一标识，平台未返回时为空',
    event_type VARCHAR(128) NULL COMMENT '抖音事件类型',
    challenge VARCHAR(255) NULL COMMENT 'Webhook URL 验证 challenge',
    raw_payload LONGTEXT NOT NULL COMMENT '抖音 Webhook 原始 JSON',
    process_status VARCHAR(32) NOT NULL DEFAULT 'received' COMMENT 'received/processed/ignored/failed',
    process_error VARCHAR(500) NULL COMMENT '后续业务处理错误',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_douyin_webhook_events_event_id (event_id),
    KEY idx_douyin_webhook_events_event_type (event_type),
    KEY idx_douyin_webhook_events_status_received (process_status, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抖音开放平台 Webhook 事件接收记录';
