-- ============================================================
-- V142: add stable publish site code
-- ============================================================

ALTER TABLE publish_sites
  ADD COLUMN site_code VARCHAR(128) NULL COMMENT 'stable publish target code / siteCode' AFTER site_name;

UPDATE publish_sites
SET site_code = CONCAT('publish_site_', id)
WHERE site_code IS NULL;

ALTER TABLE publish_sites
  MODIFY site_code VARCHAR(128) NOT NULL COMMENT 'stable publish target code / siteCode',
  ADD UNIQUE KEY uk_publish_sites_site_code (site_code);
