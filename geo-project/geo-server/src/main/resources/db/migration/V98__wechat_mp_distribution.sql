-- ============================================================
-- V98: WeChat MP third-party platform distribution foundation
-- ============================================================

CREATE TABLE mp_account (
  id                              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  brand_id                        BIGINT          NOT NULL COMMENT '所属品牌',
  platform                        VARCHAR(32)     NOT NULL DEFAULT 'wechat_mp' COMMENT 'wechat_mp',
  account_name                    VARCHAR(128)    NOT NULL COMMENT '公众号名称',
  authorizer_appid                VARCHAR(64)     NOT NULL COMMENT '授权公众号 appid',
  authorizer_refresh_token_cipher VARCHAR(2000)   NULL COMMENT 'ENC: 授权刷新 token，仅 DB 持久化',
  credential_key_version          VARCHAR(16)     NOT NULL DEFAULT 'v1',
  func_info_json                  TEXT            NULL COMMENT '微信授权权限集',
  head_img                        VARCHAR(512)    NULL COMMENT '公众号头像',
  qrcode_url                      VARCHAR(512)    NULL COMMENT '公众号二维码',
  status                          VARCHAR(32)     NOT NULL DEFAULT 'active'
    COMMENT 'active/expired/revoked/disabled',
  last_auth_checked_at            DATETIME        NULL,
  last_auth_error                 VARCHAR(512)    NULL,
  created_by                      BIGINT          NULL,
  created_at                      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mp_account_authorizer_appid (authorizer_appid),
  KEY idx_mp_account_brand (brand_id, platform, status),
  CONSTRAINT fk_mp_account_brand FOREIGN KEY (brand_id) REFERENCES brand(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='C2 self-media account authorized through WeChat Open Platform';

CREATE TABLE mp_material_mapping (
  id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  mp_account_id     BIGINT UNSIGNED NOT NULL,
  brand_material_id BIGINT          NULL,
  content_hash      VARCHAR(64)     NOT NULL COMMENT 'source asset content hash',
  media_type        VARCHAR(32)     NOT NULL COMMENT 'thumb/content_image',
  media_id          VARCHAR(128)    NULL COMMENT '微信素材 media_id',
  wechat_url        VARCHAR(512)    NULL COMMENT '正文图片 uploadimg 返回 URL',
  created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mp_material_mapping (mp_account_id, brand_material_id, content_hash, media_type),
  KEY idx_mp_material_brand_material (brand_material_id),
  CONSTRAINT fk_mp_material_mapping_account FOREIGN KEY (mp_account_id) REFERENCES mp_account(id),
  CONSTRAINT fk_mp_material_mapping_brand_material FOREIGN KEY (brand_material_id) REFERENCES brand_material(id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='WeChat material reuse mapping for brand assets';

CREATE TABLE wechat_component_ticket (
  id                              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  component_appid                 VARCHAR(64)   NOT NULL,
  component_verify_ticket_cipher  VARCHAR(2000) NOT NULL COMMENT 'ENC: component_verify_ticket',
  received_at                     DATETIME      NOT NULL,
  expires_at                      DATETIME      NULL,
  created_at                      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wechat_component_ticket_appid (component_appid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='DB fallback for WeChat component_verify_ticket';

ALTER TABLE distribution_tasks
  ADD COLUMN request_id VARCHAR(64) NULL COMMENT 'frontend idempotency request id' AFTER operator_id,
  ADD UNIQUE KEY uk_distribution_request_id (request_id),
  ADD CONSTRAINT fk_distribution_mp_account
    FOREIGN KEY (mp_account_id) REFERENCES mp_account(id);
