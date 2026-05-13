-- ============================================================
-- V136: industry site publishing configuration
-- ============================================================

ALTER TABLE publish_sites
  ADD COLUMN icon_url VARCHAR(1000) NULL COMMENT 'site icon url' AFTER domain;

