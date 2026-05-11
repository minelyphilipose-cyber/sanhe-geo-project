CREATE TABLE IF NOT EXISTS authority_media_preview_token (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  article_id BIGINT UNSIGNED NOT NULL,
  token_hash CHAR(64) NOT NULL COMMENT 'SHA-256 hash of the opaque preview token',
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  access_count INT NOT NULL DEFAULT 0,
  last_accessed_at DATETIME NULL,
  last_access_ip VARCHAR(64) NULL,
  last_user_agent VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_authority_media_preview_token_hash (token_hash),
  KEY idx_authority_media_preview_token_order (order_id, revoked_at, expires_at),
  KEY idx_authority_media_preview_token_article (article_id, created_at),
  CONSTRAINT fk_authority_media_preview_token_order FOREIGN KEY (order_id) REFERENCES authority_media_order(id),
  CONSTRAINT fk_authority_media_preview_token_article FOREIGN KEY (article_id) REFERENCES article_draft(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='权威媒体外部审核预览令牌，仅保存 token hash，按订单授权访问文章预览';
