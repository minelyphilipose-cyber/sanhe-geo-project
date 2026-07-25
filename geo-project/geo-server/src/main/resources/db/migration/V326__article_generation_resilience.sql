ALTER TABLE batch_article_generation_task
    ADD COLUMN infrastructure_retry_count INT NOT NULL DEFAULT 0 AFTER retry_count,
    ADD COLUMN compliance_retry_count INT NOT NULL DEFAULT 0 AFTER infrastructure_retry_count;

ALTER TABLE llm_call_observation
    ADD KEY idx_llm_call_obs_platform_feature_time
        (platform_code, feature, occurred_at, id);

ALTER TABLE ai_platform_health_event
    ADD KEY idx_ai_platform_health_platform_feature_time
        (platform_code, feature, occurred_at, id);
