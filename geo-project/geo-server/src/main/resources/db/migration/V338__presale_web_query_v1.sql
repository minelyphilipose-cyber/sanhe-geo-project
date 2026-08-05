-- Presale report QUERY-only web-search contract (V1).
-- Existing versions remain legacy/offline and all counters are backward compatible.

ALTER TABLE presale_report_version
    ADD COLUMN query_web_mode VARCHAR(16) NOT NULL DEFAULT 'OFF'
        COMMENT 'OFF|SHADOW|REQUIRED; fixed for one generation run' AFTER generation_stage,
    ADD COLUMN planned_query_count INT NOT NULL DEFAULT 0 AFTER extracted_competitor_count,
    ADD COLUMN web_valid_query_count INT NOT NULL DEFAULT 0 AFTER planned_query_count,
    ADD COLUMN effective_sample_count INT NOT NULL DEFAULT 0 AFTER web_valid_query_count,
    ADD COLUMN query_failed_count INT NOT NULL DEFAULT 0 AFTER effective_sample_count,
    ADD COLUMN analyze_failed_count INT NOT NULL DEFAULT 0 AFTER query_failed_count,
    ADD COLUMN skipped_query_count INT NOT NULL DEFAULT 0 AFTER analyze_failed_count,
    ADD COLUMN degraded_excluded_sample_count INT NOT NULL DEFAULT 0 AFTER skipped_query_count,
    ADD COLUMN main_web_failure_code VARCHAR(64) NULL AFTER degraded_excluded_sample_count;

UPDATE presale_report_version
SET query_web_mode = 'OFF'
WHERE query_web_mode IS NULL OR query_web_mode = '';

ALTER TABLE presale_ai_call
    ADD COLUMN query_contract_version VARCHAR(32) NULL
        COMMENT 'WEB_SEARCH_V1 for REQUIRED QUERY calls' AFTER request_prompt_content,
    ADD COLUMN search_evidence_json MEDIUMTEXT NULL
        COMMENT 'bounded web-search execution summary without credentials' AFTER query_contract_version;

CREATE INDEX idx_presale_ai_call_web_contract
    ON presale_ai_call (version_id, batch_no, stage, query_contract_version, call_status);

ALTER TABLE presale_ai_prompt_result
    ADD COLUMN effective_sample TINYINT(1) NOT NULL DEFAULT 1
        COMMENT 'Unified aggregation input filter; REQUIRED runs recompute this by ReuseKey' AFTER analyze_call_id,
    ADD KEY idx_presale_prompt_result_effective (version_id, effective_sample, batch_no, platform_code);
