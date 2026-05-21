-- ============================================================
-- V162: brand basic profile fields
-- ============================================================

ALTER TABLE brand
  ADD COLUMN brand_short_name VARCHAR(128) NULL COMMENT 'brand short name' AFTER brand_name,
  ADD COLUMN core_products VARCHAR(500) NULL COMMENT 'core products separated by comma' AFTER main_business,
  ADD COLUMN brand_positioning VARCHAR(255) NULL COMMENT 'brand positioning' AFTER core_products;
