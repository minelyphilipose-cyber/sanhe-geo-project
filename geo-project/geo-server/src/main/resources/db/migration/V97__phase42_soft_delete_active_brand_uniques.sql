-- ============================================================
-- V97: phase42 soft-delete active-only brand unique keys
-- ============================================================

-- Precheck output for dirty historical data. Existing unique keys should already prevent these
-- rows, but these result sets make a failed migration easier to diagnose before the ADD UNIQUE KEY
-- statement reports a generic duplicate-entry error.
SELECT
    company_id,
    brand_slug,
    COUNT(*) AS active_duplicate_count,
    GROUP_CONCAT(id ORDER BY id) AS active_brand_ids
FROM brand
WHERE deleted_at IS NULL
  AND brand_slug IS NOT NULL
GROUP BY company_id, brand_slug
HAVING COUNT(*) > 1;

SELECT
    geo_site_code,
    COUNT(*) AS active_duplicate_count,
    GROUP_CONCAT(id ORDER BY id) AS active_brand_ids
FROM brand
WHERE deleted_at IS NULL
  AND geo_site_code IS NOT NULL
GROUP BY geo_site_code
HAVING COUNT(*) > 1;

-- Existing unique keys already prevent duplicate slug/site-code values in normal data. The new
-- active-only unique keys below remain the enforcement point and will fail migration if active
-- duplicates exist. Soft-deleted rows are allowed to reuse slug/site codes because NULL does not
-- collide in MySQL unique indexes.
ALTER TABLE brand
    DROP KEY uk_brand_company_slug,
    DROP KEY uk_brand_geo_site_code,
    ADD COLUMN active_slug_flag TINYINT GENERATED ALWAYS AS (
        IF(deleted_at IS NULL, 1, NULL)
    ) STORED COMMENT 'active-only uniqueness flag for company slug' AFTER deleted_by,
    ADD COLUMN active_geo_site_flag TINYINT GENERATED ALWAYS AS (
        IF(deleted_at IS NULL, 1, NULL)
    ) STORED COMMENT 'active-only uniqueness flag for geo site code' AFTER active_slug_flag,
    ADD UNIQUE KEY uk_brand_company_slug_active (company_id, brand_slug, active_slug_flag),
    ADD UNIQUE KEY uk_brand_geo_site_code_active (geo_site_code, active_geo_site_flag);
