-- Freeze dealer attribution at report/version level and persist answer attribution facts.
ALTER TABLE presale_report
    ADD COLUMN attribution_mode VARCHAR(16) NOT NULL DEFAULT 'STANDARD'
        COMMENT 'STANDARD/DEALER, frozen when report is created' AFTER represented_brands,
    ADD COLUMN matched_role_name VARCHAR(100) NULL
        COMMENT 'Chinese role display name frozen at creation' AFTER attribution_mode;

ALTER TABLE presale_report_version
    ADD COLUMN attribution_mode VARCHAR(16) NULL
        COMMENT 'STANDARD/DEALER frozen for this version' AFTER query_web_mode,
    ADD COLUMN matched_role_name VARCHAR(100) NULL
        COMMENT 'Chinese role display name frozen for this version' AFTER attribution_mode,
    ADD COLUMN represented_brands_snapshot JSON NULL
        COMMENT 'represented brands frozen for this version' AFTER matched_role_name;

ALTER TABLE presale_ai_prompt_result
    ADD COLUMN target_entity_hit TINYINT(1) NULL AFTER is_mentioned,
    ADD COLUMN represented_brand_hit TINYINT(1) NULL AFTER target_entity_hit,
    ADD COLUMN target_brand_relation_hit TINYINT(1) NULL AFTER represented_brand_hit,
    ADD COLUMN attribution_type VARCHAR(16) NULL
        COMMENT 'DIRECT/LINKED/BRAND_ONLY/NONE; null for STANDARD reports'
        AFTER target_brand_relation_hit;

-- Initial manually managed benchmark versions. Existing manual rows win.
INSERT IGNORE INTO presale_benchmark (
    industry, industry_role,
    avg_overall, avg_mention, avg_ranking, avg_sentiment, avg_coverage,
    top1_overall, top1_mention, top1_ranking, top1_sentiment, top1_coverage,
    top10_score, confidence_level, source, sample_size, enabled, effective_from, remark
) VALUES
('retail','_ALL_',56,61,52,63,46,82,88,86,79,74,72,'MEDIUM','MANUAL',120,1,'2020-01-01','Imported from benchmarks/v1.json'),
('finance','_ALL_',62,67,58,71,53,86,91,88,86,82,78,'HIGH','MANUAL',80,1,'2020-01-01','Imported from benchmarks/v1.json'),
('education','_ALL_',51,56,44,62,41,77,82,79,76,71,68,'MEDIUM','MANUAL',90,1,'2020-01-01','Imported from benchmarks/v1.json'),
('restaurant','_ALL_',61,66,57,68,52,86,88,84,82,78,78,'MEDIUM','MANUAL',500,1,'2020-01-01','Imported from benchmarks/v1.json'),
('automotive','_ALL_',61,66,54,68,52,84,88,87,82,78,75,'MEDIUM','MANUAL',500,1,'2020-01-01','Imported from benchmarks/v1.json'),
('tourism','_ALL_',60,65,55,68,50,85,88,85,82,78,75,'MEDIUM','MANUAL',500,1,'2020-01-01','Imported from benchmarks/v1.json'),
('medical_beauty','_ALL_',60,65,55,68,50,85,88,85,82,78,75,'MEDIUM','MANUAL',500,1,'2020-01-01','Imported from benchmarks/v1.json'),
('tech_software','_ALL_',57,56,61,54,58,78,74,81,72,79,74,'HIGH','MANUAL',150,1,'2020-01-01','Imported from benchmarks/v1.json'),
('_ALL_','_ALL_',47,49,45,44,48,79,82,81,75,82,71,'MEDIUM','MANUAL',500,1,'2020-01-01','Required global fallback');
