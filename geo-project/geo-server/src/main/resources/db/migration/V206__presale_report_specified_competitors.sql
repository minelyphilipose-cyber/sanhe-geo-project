ALTER TABLE presale_report
    ADD COLUMN specified_competitors JSON NULL COMMENT 'customer specified competitor names, exactly 3 when provided' AFTER user_type;
