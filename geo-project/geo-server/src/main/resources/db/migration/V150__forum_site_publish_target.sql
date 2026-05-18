-- ============================================================
-- V150: forum site publish target
-- ============================================================

ALTER TABLE distribution_tasks
  DROP CHECK chk_distribution_target_consistency;

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
    OR (target_kind = 'forum_site'
        AND site_id IS NULL AND self_media_account_id IS NULL AND brand_official_site_id IS NULL
        AND target_brand_id IS NULL AND industry_site_id IS NOT NULL AND authority_media_id IS NULL)
    OR (target_kind = 'authority_media'
        AND site_id IS NULL AND self_media_account_id IS NULL AND brand_official_site_id IS NULL
        AND target_brand_id IS NULL AND industry_site_id IS NULL AND authority_media_id IS NOT NULL)
  );
