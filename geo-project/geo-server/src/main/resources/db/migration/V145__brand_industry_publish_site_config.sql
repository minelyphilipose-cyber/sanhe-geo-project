-- ============================================================
-- V145: brand industry publish site preference
-- ============================================================

ALTER TABLE brand
  ADD COLUMN industry_site_name VARCHAR(128) NULL COMMENT 'preferred industry publish site name' AFTER geo_site_status,
  ADD COLUMN industry_site_code VARCHAR(128) NULL COMMENT 'preferred industry publish site code' AFTER industry_site_name;

CREATE INDEX idx_brand_industry_site_code ON brand (industry_site_code);
