ALTER TABLE poll_keyword_daily_summary
    ADD COLUMN search_confirmed_count INT NOT NULL DEFAULT 0
        COMMENT 'effective finalized results where web search actually executed'
        AFTER contact_mention_total,
    ADD COLUMN brand_search_count INT NOT NULL DEFAULT 0
        COMMENT 'effective finalized results where brand appeared in search evidence'
        AFTER search_confirmed_count,
    ADD COLUMN brand_answer_count INT NOT NULL DEFAULT 0
        COMMENT 'effective finalized results where brand appeared in final answer'
        AFTER brand_search_count,
    ADD COLUMN confirmed_citation_exposure_count INT NOT NULL DEFAULT 0
        COMMENT 'effective finalized R5 confirmed citation exposure count'
        AFTER brand_answer_count,
    ADD COLUMN confirmed_citation_exposure_rate DECIMAL(8,4) NOT NULL DEFAULT 0.0000
        COMMENT 'confirmed citation exposure count divided by completed result count'
        AFTER confirmed_citation_exposure_count;

ALTER TABLE poll_platform_daily_summary
    ADD COLUMN channel_code VARCHAR(32) NOT NULL DEFAULT ''
        COMMENT 'stable business channel code'
        AFTER platform_code,
    ADD COLUMN search_confirmed_count INT NOT NULL DEFAULT 0
        COMMENT 'effective finalized results where web search actually executed'
        AFTER contact_mention_total,
    ADD COLUMN brand_search_count INT NOT NULL DEFAULT 0
        COMMENT 'effective finalized results where brand appeared in search evidence'
        AFTER search_confirmed_count,
    ADD COLUMN brand_answer_count INT NOT NULL DEFAULT 0
        COMMENT 'effective finalized results where brand appeared in final answer'
        AFTER brand_search_count,
    ADD COLUMN confirmed_citation_exposure_count INT NOT NULL DEFAULT 0
        COMMENT 'effective finalized R5 confirmed citation exposure count'
        AFTER brand_answer_count,
    ADD COLUMN confirmed_citation_exposure_rate DECIMAL(8,4) NOT NULL DEFAULT 0.0000
        COMMENT 'confirmed citation exposure count divided by completed result count'
        AFTER confirmed_citation_exposure_count;
