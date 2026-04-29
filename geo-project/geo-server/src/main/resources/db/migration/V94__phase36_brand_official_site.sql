-- ============================================================
-- V94: brand_official_site + publish_sites is_framework
-- Phase 1 of multi-channel content publishing system
-- ============================================================

CREATE TABLE brand_official_site (
  id                       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  brand_id                 BIGINT          NOT NULL COMMENT '所属品牌',
  site_name                VARCHAR(128)    NOT NULL COMMENT '官网展示名',
  site_domain              VARCHAR(255)    NULL     COMMENT '官网域名(展示)',
  cms_framework_code       VARCHAR(64)     NOT NULL COMMENT 'CMS 框架标识,关联 publish_sites 中 is_framework=1 行的 site_name 或专用 code',
  tenant_key               VARCHAR(255)    NOT NULL COMMENT 'CMS 框架中识别本官网的 key',
  api_endpoint             VARCHAR(500)    NOT NULL,
  auth_type                VARCHAR(32)     NOT NULL DEFAULT 'bearer_token',
  credentials_cipher       VARCHAR(2000)   NOT NULL COMMENT 'ENC: 前缀加密的凭证',
  status                   VARCHAR(32)     NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
  last_check_at            DATETIME        NULL,
  last_check_result        VARCHAR(32)     NULL,
  remark                   VARCHAR(500)    NULL,
  created_by               BIGINT          NOT NULL,
  created_at               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at               DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_brand_official_site (brand_id, cms_framework_code, tenant_key),
  KEY idx_brand_official_site_brand (brand_id, status),
  CONSTRAINT fk_brand_official_site_brand FOREIGN KEY (brand_id) REFERENCES brand(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='C1 brand-level official site (multichannel publishing)';

ALTER TABLE publish_sites
  ADD COLUMN is_framework TINYINT(1) NOT NULL DEFAULT 0
     COMMENT '1=framework metadata row (not a publish target), 0=normal client site',
  ADD KEY idx_publish_sites_is_framework (is_framework, status);

INSERT INTO publish_sites (
  site_name, domain, industry_tags, tier, status, integration_method, is_framework,
  api_endpoint, http_method, request_body_template, content_constraints
) SELECT
  'Official CMS Framework v1', 'official-cms.framework.local',
  JSON_ARRAY('general'),  -- 框架行用 general 行业,不参与行业匹配
  'S0', 'active', 'official_cms', 1,
  'https://placeholder.invalid/api', 'POST',
  '{"site_id":"{{tenantKey}}","title":"{{title}}","type":"{{articleType}}","content":"{{content}}"}',
  '{"maxTitleLength":200,"maxBodyLength":100000}'
WHERE NOT EXISTS (
  SELECT 1 FROM publish_sites
   WHERE integration_method='official_cms' AND is_framework=1
);

ALTER TABLE distribution_tasks
  ADD CONSTRAINT fk_distribution_brand_official_site
  FOREIGN KEY (brand_official_site_id) REFERENCES brand_official_site(id);

-- ============================================================
-- 回滚指引
-- 1. ALTER TABLE distribution_tasks DROP FOREIGN KEY fk_distribution_brand_official_site
-- 2. DELETE FROM publish_sites WHERE integration_method='official_cms' AND is_framework=1
-- 3. ALTER TABLE publish_sites DROP INDEX idx_publish_sites_is_framework
-- 4. ALTER TABLE publish_sites DROP COLUMN is_framework
-- 5. DROP TABLE brand_official_site
-- ============================================================
