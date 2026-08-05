-- Optional upstream brands represented by dealer/agent presale customers.
-- Target brand remains presale_report.brand_name; these values are context only.

ALTER TABLE presale_report
    ADD COLUMN represented_brands JSON NULL
        COMMENT 'Agent/dealer represented brand names; context only, not target-brand mentions'
        AFTER industry_role;
