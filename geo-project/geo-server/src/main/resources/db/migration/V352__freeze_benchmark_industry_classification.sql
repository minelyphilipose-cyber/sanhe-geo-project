-- Freeze the one-time semantic classification used by a report version's benchmark lookup.
-- Existing versions intentionally stay null and continue to resolve by their saved raw industry.
ALTER TABLE presale_report_version
    ADD COLUMN benchmark_industry_key VARCHAR(50) NULL
        COMMENT 'Canonical benchmark industry key; _ALL_ when classification falls back' AFTER represented_brands_snapshot,
    ADD COLUMN industry_classification_source VARCHAR(16) NULL
        COMMENT 'DIRECT/LLM/FALLBACK' AFTER benchmark_industry_key,
    ADD COLUMN industry_classification_confidence VARCHAR(10) NULL
        COMMENT 'HIGH/MEDIUM/LOW' AFTER industry_classification_source,
    ADD COLUMN industry_classifier_model VARCHAR(128) NULL
        COMMENT 'Actual model id for LLM classification' AFTER industry_classification_confidence;
