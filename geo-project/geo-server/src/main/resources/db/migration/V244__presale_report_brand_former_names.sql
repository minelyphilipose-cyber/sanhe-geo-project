ALTER TABLE presale_report
    ADD COLUMN brand_former_names JSON NULL COMMENT 'brand former names, max 3' AFTER brand_name;
