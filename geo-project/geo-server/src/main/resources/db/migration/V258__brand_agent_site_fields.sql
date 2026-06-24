-- ============================================================
-- V258: brand-owned Agent official site configuration
-- ============================================================

ALTER TABLE brand
  ADD COLUMN geo_site_name VARCHAR(128) NULL
    COMMENT 'Agent official site display name maintained on brand profile' AFTER geo_site_status,
  ADD COLUMN geo_site_domain VARCHAR(255) NULL
    COMMENT 'Agent official site domain, e.g. www.example.com' AFTER geo_site_name,
  ADD COLUMN active_geo_site_domain_flag TINYINT GENERATED ALWAYS AS (
    IF(deleted_at IS NULL, 1, NULL)
  ) STORED COMMENT 'active-only uniqueness flag for Agent official site domain' AFTER active_geo_site_flag,
  ADD UNIQUE KEY uk_brand_geo_site_domain_active (geo_site_domain, active_geo_site_domain_flag);
