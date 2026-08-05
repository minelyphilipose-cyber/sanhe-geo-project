-- Each successful QUEUED -> RUNNING claim increments this value.
-- Delayed workers from an older attempt can then be rejected by conditional writes.
ALTER TABLE presale_report_version
    ADD COLUMN generation_attempt BIGINT NOT NULL DEFAULT 0
        COMMENT 'Monotonic generation run attempt used to fence delayed workers'
        AFTER generation_stage;
