-- Manual industry input is now classified by the generation worker after report creation,
-- so PENDING is a valid transient source until the frozen result is persisted.
ALTER TABLE presale_report_version
    MODIFY COLUMN industry_classification_source VARCHAR(16) NULL
        COMMENT 'DIRECT/PENDING/LLM/FALLBACK';
