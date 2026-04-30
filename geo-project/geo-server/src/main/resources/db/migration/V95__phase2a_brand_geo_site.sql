-- ============================================================
-- V95: Phase 2A C1' brand GEO site publishing channel
-- ============================================================

ALTER TABLE brand
  ADD COLUMN geo_site_code VARCHAR(64) NULL
    COMMENT 'GEO site code in owned site network; NULL means not configured' AFTER forbidden_phrases,
  ADD COLUMN geo_site_status VARCHAR(32) NULL DEFAULT 'active'
    COMMENT 'active/disabled; meaningful only when geo_site_code is not NULL' AFTER geo_site_code,
  ADD UNIQUE KEY uk_brand_geo_site_code (geo_site_code);

ALTER TABLE distribution_tasks
  ADD COLUMN target_brand_id BIGINT UNSIGNED NULL
    COMMENT 'Brand id for target_kind=brand_geo_site' AFTER brand_official_site_id,
  ADD KEY idx_distribution_target_brand_status (target_brand_id, status);

-- V93's functional unique index uk_distribution_article_target_attempt covers
-- site/mp_account/brand_official_site/industry_site/authority_media targets.
-- Phase 2A adds brand_geo_site as a new target kind; keep the V93 index intact
-- and add a dedicated business unique key for article + brand + attempt_no.
ALTER TABLE distribution_tasks
  ADD UNIQUE KEY uk_distribution_article_brand_geo_attempt (
    article_id, target_kind, target_brand_id, attempt_no
  );

ALTER TABLE distribution_tasks
  DROP CHECK chk_distribution_target_consistency;

ALTER TABLE distribution_tasks
  ADD CONSTRAINT chk_distribution_target_consistency CHECK (
       (target_kind = 'site'
        AND site_id IS NOT NULL AND mp_account_id IS NULL AND brand_official_site_id IS NULL
        AND target_brand_id IS NULL AND industry_site_id IS NULL AND authority_media_id IS NULL)
    OR (target_kind = 'mp_account'
        AND site_id IS NULL AND mp_account_id IS NOT NULL AND brand_official_site_id IS NULL
        AND target_brand_id IS NULL AND industry_site_id IS NULL AND authority_media_id IS NULL)
    OR (target_kind = 'brand_official_site'
        AND site_id IS NULL AND mp_account_id IS NULL AND brand_official_site_id IS NOT NULL
        AND target_brand_id IS NULL AND industry_site_id IS NULL AND authority_media_id IS NULL)
    OR (target_kind = 'brand_geo_site'
        AND site_id IS NULL AND mp_account_id IS NULL AND brand_official_site_id IS NULL
        AND target_brand_id IS NOT NULL AND industry_site_id IS NULL AND authority_media_id IS NULL)
    OR (target_kind = 'industry_site'
        AND site_id IS NULL AND mp_account_id IS NULL AND brand_official_site_id IS NULL
        AND target_brand_id IS NULL AND industry_site_id IS NOT NULL AND authority_media_id IS NULL)
    OR (target_kind = 'authority_media'
        AND site_id IS NULL AND mp_account_id IS NULL AND brand_official_site_id IS NULL
        AND target_brand_id IS NULL AND industry_site_id IS NULL AND authority_media_id IS NOT NULL)
  );
