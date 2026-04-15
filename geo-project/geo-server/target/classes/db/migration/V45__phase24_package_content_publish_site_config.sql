-- ============================================================
-- V45: package content config add publish site tier/count
-- ============================================================

ALTER TABLE package_content_config
    ADD COLUMN publish_site_tier VARCHAR(8) NOT NULL DEFAULT 'S1' COMMENT 'publish site tier: S0/S1/S2' AFTER questions_per_article,
    ADD COLUMN publish_site_count INT NOT NULL DEFAULT 1 COMMENT 'publish site count per batch' AFTER publish_site_tier;

