ALTER TABLE ai_platform_config
    ADD COLUMN enabled_for_geo_question TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'enabled for GEO question pool generation' AFTER enabled_for_article;

UPDATE ai_platform_config
SET enabled_for_geo_question = CASE
    WHEN platform_code IN ('qwen', 'deepseek', 'mimo') THEN 1
    ELSE 0
END;
