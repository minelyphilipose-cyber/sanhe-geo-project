-- ============================================================
-- V105: Self-media account abstraction for WeChat MP and future platforms
-- ============================================================
-- WARNING:
-- This migration contains multiple MySQL DDL operations. MySQL DDL is
-- auto-committed and cannot be fully rolled back by Flyway transactions.
-- Run a mysqldump backup before applying this migration. If it fails midway,
-- inspect the schema state manually and restore from backup when needed.

-- 1) Create generic self-media account table. Keep old mp_account table
--    as read-only migration reference; application code switches to this table.
--    The old mp_account / mp_material_mapping tables remain for inspection.
--    Starting from Step 2.3, application code must no longer read or write them.
--    Drop them only in a later dedicated migration after Douyin Stage A acceptance.
CREATE TABLE self_media_account (
  id                       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  brand_id                 BIGINT       NOT NULL COMMENT '所属品牌',
  platform                 VARCHAR(32)  NOT NULL COMMENT 'wechat_mp/douyin_image_text/... ',
  platform_account_id      VARCHAR(128) NOT NULL COMMENT '平台侧账号唯一标识，如微信 authorizer_appid、抖音 open_id',
  account_name             VARCHAR(128) NOT NULL COMMENT '账号展示名称',
  status                   VARCHAR(32)  NOT NULL DEFAULT 'active'
    COMMENT 'active/expired/revoked/disabled',
  scope_json               JSON         NULL COMMENT '平台授权 scope/权限集',
  access_token_cipher      VARCHAR(4000) NULL COMMENT 'ENC: access token，仅 DB 持久化',
  refresh_token_cipher     VARCHAR(4000) NULL COMMENT 'ENC: refresh token，仅 DB 持久化',
  credential_key_version   VARCHAR(16)  NOT NULL DEFAULT 'v1',
  access_token_expires_at  DATETIME     NULL,
  refresh_token_expires_at DATETIME     NULL,
  avatar_url               VARCHAR(512) NULL COMMENT '平台账号头像',
  qrcode_url               VARCHAR(512) NULL COMMENT '平台账号二维码',
  last_auth_checked_at     DATETIME     NULL,
  last_auth_error          VARCHAR(512) NULL,
  extra_json               JSON         NULL COMMENT '平台特有字段与迁移元数据',
  created_by               BIGINT       NULL,
  created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_self_media_account_platform_account (platform, platform_account_id),
  KEY idx_self_media_account_brand (brand_id, platform, status),
  CONSTRAINT fk_self_media_account_brand FOREIGN KEY (brand_id) REFERENCES brand(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Self-media account authorized through external open platforms';

INSERT INTO self_media_account (
  id,
  brand_id,
  platform,
  platform_account_id,
  account_name,
  status,
  scope_json,
  access_token_cipher,
  refresh_token_cipher,
  credential_key_version,
  access_token_expires_at,
  refresh_token_expires_at,
  avatar_url,
  qrcode_url,
  last_auth_checked_at,
  last_auth_error,
  extra_json,
  created_by,
  created_at,
  updated_at
)
-- Column mapping highlights:
-- authorizer_appid -> platform_account_id,
-- authorizer_refresh_token_cipher -> refresh_token_cipher,
-- func_info_json -> scope_json only when JSON_VALID(func_info_json).
SELECT
  id,
  brand_id,
  platform,
  authorizer_appid,
  account_name,
  status,
  CASE
    WHEN func_info_json IS NOT NULL AND JSON_VALID(func_info_json) THEN CAST(func_info_json AS JSON)
    ELSE NULL
  END,
  NULL,
  authorizer_refresh_token_cipher,
  credential_key_version,
  NULL,
  NULL,
  head_img,
  qrcode_url,
  last_auth_checked_at,
  last_auth_error,
  JSON_OBJECT(
    'legacy_table', 'mp_account',
    'legacy_id', id,
    'wechat_authorizer_appid', authorizer_appid
  ),
  created_by,
  created_at,
  updated_at
FROM mp_account;

-- Keep AUTO_INCREMENT ahead of copied legacy ids.
SET @self_media_account_next_id := (SELECT IFNULL(MAX(id), 0) + 1 FROM self_media_account);
SET @self_media_account_auto_increment_sql := CONCAT(
  'ALTER TABLE self_media_account AUTO_INCREMENT = ',
  @self_media_account_next_id
);
PREPARE self_media_account_auto_increment_stmt FROM @self_media_account_auto_increment_sql;
EXECUTE self_media_account_auto_increment_stmt;
DEALLOCATE PREPARE self_media_account_auto_increment_stmt;

-- 2) Create generic material mapping table and migrate existing WeChat mappings.
CREATE TABLE self_media_material_mapping (
  id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  self_media_account_id BIGINT UNSIGNED NOT NULL,
  brand_material_id     BIGINT          NULL,
  content_hash          VARCHAR(64)     NOT NULL COMMENT 'source asset content hash',
  media_type            VARCHAR(32)     NOT NULL COMMENT 'thumb/content_image/douyin_image/...',
  platform_media_id     VARCHAR(128)    NULL COMMENT '平台素材 ID',
  platform_url          VARCHAR(512)    NULL COMMENT '平台素材 URL',
  extra_json            JSON            NULL COMMENT '平台特有素材字段与迁移元数据',
  created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_self_media_material_mapping (
    self_media_account_id, brand_material_id, content_hash, media_type
  ),
  KEY idx_self_media_material_brand_material (brand_material_id),
  CONSTRAINT fk_self_media_material_mapping_account
    FOREIGN KEY (self_media_account_id) REFERENCES self_media_account(id),
  CONSTRAINT fk_self_media_material_mapping_brand_material
    FOREIGN KEY (brand_material_id) REFERENCES brand_material(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Self-media material reuse mapping for brand assets';

INSERT INTO self_media_material_mapping (
  id,
  self_media_account_id,
  brand_material_id,
  content_hash,
  media_type,
  platform_media_id,
  platform_url,
  extra_json,
  created_at,
  updated_at
)
SELECT
  id,
  mp_account_id,
  brand_material_id,
  content_hash,
  media_type,
  media_id,
  wechat_url,
  JSON_OBJECT('legacy_table', 'mp_material_mapping'),
  created_at,
  updated_at
FROM mp_material_mapping;

-- Keep AUTO_INCREMENT ahead of copied legacy ids.
SET @self_media_material_next_id := (SELECT IFNULL(MAX(id), 0) + 1 FROM self_media_material_mapping);
SET @self_media_material_auto_increment_sql := CONCAT(
  'ALTER TABLE self_media_material_mapping AUTO_INCREMENT = ',
  @self_media_material_next_id
);
PREPARE self_media_material_auto_increment_stmt FROM @self_media_material_auto_increment_sql;
EXECUTE self_media_material_auto_increment_stmt;
DEALLOCATE PREPARE self_media_material_auto_increment_stmt;

-- 3) Move distribution_tasks from mp_account_id to self_media_account_id.
ALTER TABLE distribution_tasks
  ADD COLUMN self_media_account_id BIGINT UNSIGNED NULL
    COMMENT 'C2 自媒体账号，引用 self_media_account' AFTER target_kind;

UPDATE distribution_tasks
SET self_media_account_id = mp_account_id
WHERE target_kind = 'mp_account';

-- Drop objects that reference mp_account_id before dropping the old column.
ALTER TABLE distribution_tasks
  DROP CONSTRAINT chk_distribution_target_consistency;

ALTER TABLE distribution_tasks
  DROP INDEX uk_distribution_article_target_attempt;

ALTER TABLE distribution_tasks
  DROP FOREIGN KEY fk_distribution_mp_account;

ALTER TABLE distribution_tasks
  DROP INDEX idx_distribution_mp_account_status;

ALTER TABLE distribution_tasks
  DROP COLUMN mp_account_id;

-- Recreate the functional unique key with the same V93/V95 structure, replacing only mp_account_id.
ALTER TABLE distribution_tasks
  ADD UNIQUE KEY uk_distribution_article_target_attempt (
    article_id,
    target_kind,
    (COALESCE(`site_id`, `self_media_account_id`, `brand_official_site_id`, `industry_site_id`, `authority_media_id`)),
    attempt_no
  );

ALTER TABLE distribution_tasks
  ADD KEY idx_distribution_self_media_account_status (self_media_account_id, status),
  ADD CONSTRAINT fk_distribution_self_media_account
    FOREIGN KEY (self_media_account_id) REFERENCES self_media_account(id);

-- Recreate the V95 CHECK constraint with the same target_kind values; only the account id column changes.
ALTER TABLE distribution_tasks
  ADD CONSTRAINT chk_distribution_target_consistency CHECK (
       (target_kind = 'site'
        AND site_id IS NOT NULL AND self_media_account_id IS NULL AND brand_official_site_id IS NULL
        AND target_brand_id IS NULL AND industry_site_id IS NULL AND authority_media_id IS NULL)
    OR (target_kind = 'mp_account'
        AND site_id IS NULL AND self_media_account_id IS NOT NULL AND brand_official_site_id IS NULL
        AND target_brand_id IS NULL AND industry_site_id IS NULL AND authority_media_id IS NULL)
    OR (target_kind = 'brand_official_site'
        AND site_id IS NULL AND self_media_account_id IS NULL AND brand_official_site_id IS NOT NULL
        AND target_brand_id IS NULL AND industry_site_id IS NULL AND authority_media_id IS NULL)
    OR (target_kind = 'brand_geo_site'
        AND site_id IS NULL AND self_media_account_id IS NULL AND brand_official_site_id IS NULL
        AND target_brand_id IS NOT NULL AND industry_site_id IS NULL AND authority_media_id IS NULL)
    OR (target_kind = 'industry_site'
        AND site_id IS NULL AND self_media_account_id IS NULL AND brand_official_site_id IS NULL
        AND target_brand_id IS NULL AND industry_site_id IS NOT NULL AND authority_media_id IS NULL)
    OR (target_kind = 'authority_media'
        AND site_id IS NULL AND self_media_account_id IS NULL AND brand_official_site_id IS NULL
        AND target_brand_id IS NULL AND industry_site_id IS NULL AND authority_media_id IS NOT NULL)
  );
