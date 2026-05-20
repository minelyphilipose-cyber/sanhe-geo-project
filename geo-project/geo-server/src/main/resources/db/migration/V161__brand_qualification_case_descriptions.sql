-- ============================================================
-- V161: brand qualification and case descriptions
-- ============================================================

ALTER TABLE brand
  ADD COLUMN brand_qualification_description VARCHAR(300) NULL COMMENT 'brand qualification description' AFTER business_intro,
  ADD COLUMN brand_case_description VARCHAR(300) NULL COMMENT 'brand case description' AFTER brand_qualification_description;
