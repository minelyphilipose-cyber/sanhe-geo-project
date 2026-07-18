-- Phase A foundation for auditable web-search question polling.
-- All web profiles are seeded disabled and require a successful provider PoC before activation.

ALTER TABLE ai_platform_config
    ADD COLUMN channel_code VARCHAR(32) NULL COMMENT 'stable business channel code' AFTER platform_code,
    ADD COLUMN usage_scene VARCHAR(32) NOT NULL DEFAULT 'STANDARD_CHAT' COMMENT 'STANDARD_CHAT|QUESTION_POLL_WEB' AFTER channel_code,
    ADD COLUMN integration_type VARCHAR(48) NOT NULL DEFAULT 'OPENAI_CHAT' COMMENT 'provider protocol adapter' AFTER usage_scene,
    ADD COLUMN provider_config_json JSON NULL COMMENT 'non-secret provider-specific configuration' AFTER integration_type,
    ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 COMMENT 'monotonic configuration version' AFTER provider_config_json;

UPDATE ai_platform_config
SET channel_code = platform_code
WHERE channel_code IS NULL OR TRIM(channel_code) = '';

ALTER TABLE ai_platform_config
    MODIFY COLUMN channel_code VARCHAR(32) NOT NULL COMMENT 'stable business channel code',
    ADD UNIQUE KEY uk_ai_platform_channel_scene (channel_code, usage_scene),
    ADD KEY idx_ai_platform_poll_scene (usage_scene, enabled, enabled_for_question_poll);

ALTER TABLE project
    ADD COLUMN poll_brand_aliases_json JSON NULL COMMENT 'brand aliases dedicated to question-poll matching',
    ADD COLUMN poll_exclude_keywords_json JSON NULL COMMENT 'same-name subjects excluded from question-poll matching';

ALTER TABLE poll_batches
    ADD COLUMN trigger_type VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED|MANUAL' AFTER question_tier,
    ADD COLUMN created_by BIGINT NULL COMMENT 'manual run creator' AFTER trigger_type;

ALTER TABLE poll_batch_shards
    ADD COLUMN channel_code VARCHAR(32) NULL COMMENT 'stable business channel code' AFTER platform_code;

UPDATE poll_batch_shards
SET channel_code = platform_code
WHERE channel_code IS NULL OR TRIM(channel_code) = '';

ALTER TABLE poll_batch_shards
    MODIFY COLUMN channel_code VARCHAR(32) NOT NULL COMMENT 'stable business channel code',
    ADD KEY idx_poll_shard_channel_date (channel_code, batch_date);

ALTER TABLE poll_results
    ADD COLUMN channel_code VARCHAR(32) NULL COMMENT 'stable business channel code' AFTER platform_code,
    ADD COLUMN trigger_type VARCHAR(24) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED|MANUAL' AFTER question_tier,
    ADD COLUMN latest_attempt_id BIGINT NULL COMMENT 'highest created attempt number' AFTER detail_json,
    ADD COLUMN effective_attempt_id BIGINT NULL COMMENT 'latest complete attempt used by display and statistics' AFTER latest_attempt_id,
    ADD COLUMN latest_attempt_status VARCHAR(24) NULL AFTER effective_attempt_id,
    ADD COLUMN execution_finalized TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'automatic retry chain reached a terminal state' AFTER latest_attempt_status,
    ADD COLUMN retry_chain_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED' AFTER execution_finalized,
    ADD COLUMN result_code VARCHAR(4) NULL COMMENT 'R0|R1|R2|R3|R4|R5' AFTER retry_chain_status,
    ADD COLUMN search_requested TINYINT(1) NOT NULL DEFAULT 0 AFTER result_code,
    ADD COLUMN search_triggered TINYINT(1) NOT NULL DEFAULT 0 AFTER search_requested,
    ADD COLUMN search_status VARCHAR(32) NULL AFTER search_triggered,
    ADD COLUMN brand_in_search TINYINT(1) NOT NULL DEFAULT 0 AFTER search_status,
    ADD COLUMN brand_in_answer TINYINT(1) NOT NULL DEFAULT 0 AFTER brand_in_search,
    ADD COLUMN citation_confidence VARCHAR(16) NOT NULL DEFAULT 'NONE' AFTER brand_in_answer,
    ADD COLUMN confirmed_citation_exposure TINYINT(1) NOT NULL DEFAULT 0 AFTER citation_confidence,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT 'optimistic projection version' AFTER confirmed_citation_exposure,
    ADD COLUMN deleted_at DATETIME NULL COMMENT 'soft deletion time; ordinary business deletion is forbidden' AFTER version,
    ADD COLUMN deleted_by BIGINT NULL AFTER deleted_at,
    ADD COLUMN delete_reason VARCHAR(500) NULL AFTER deleted_by;

UPDATE poll_results
SET channel_code = platform_code
WHERE channel_code IS NULL OR TRIM(channel_code) = '';

ALTER TABLE poll_results
    MODIFY COLUMN channel_code VARCHAR(32) NOT NULL COMMENT 'stable business channel code',
    ADD KEY idx_poll_result_channel_date (channel_code, batch_date),
    ADD KEY idx_poll_result_latest_attempt (latest_attempt_id),
    ADD KEY idx_poll_result_effective_attempt (effective_attempt_id),
    ADD KEY idx_poll_result_active (deleted_at, project_id, batch_date);

ALTER TABLE poll_daily_stats
    ADD COLUMN channel_code VARCHAR(32) NULL COMMENT 'stable business channel code' AFTER platform_code,
    ADD COLUMN search_confirmed_count INT NOT NULL DEFAULT 0 AFTER contact_mention_count,
    ADD COLUMN brand_search_count INT NOT NULL DEFAULT 0 AFTER search_confirmed_count,
    ADD COLUMN brand_answer_count INT NOT NULL DEFAULT 0 AFTER brand_search_count,
    ADD COLUMN confirmed_citation_exposure_count INT NOT NULL DEFAULT 0 AFTER brand_answer_count,
    ADD COLUMN confirmed_citation_exposure_rate DECIMAL(8,4) NOT NULL DEFAULT 0 AFTER confirmed_citation_exposure_count;

UPDATE poll_daily_stats
SET channel_code = platform_code
WHERE channel_code IS NULL OR TRIM(channel_code) = '';

ALTER TABLE poll_daily_stats
    MODIFY COLUMN channel_code VARCHAR(32) NOT NULL COMMENT 'stable business channel code',
    ADD KEY idx_poll_stats_channel_date (channel_code, batch_date);

CREATE TABLE poll_invocation_attempts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    poll_result_id BIGINT NOT NULL,
    shard_item_id BIGINT NULL,
    dispatch_task_id BIGINT NULL,
    attempt_no INT NOT NULL,
    chain_no INT NOT NULL DEFAULT 1,
    root_attempt_id BIGINT NULL,
    retry_of_attempt_id BIGINT NULL,
    trigger_type VARCHAR(24) NOT NULL COMMENT 'SCHEDULED|MANUAL|SEARCH_RETRY|MANUAL_RETRY',
    project_id BIGINT NOT NULL,
    keyword_result_id BIGINT NULL,
    question_snapshot TEXT NOT NULL,
    system_prompt_snapshot TEXT NOT NULL,
    platform_config_id BIGINT NOT NULL,
    platform_code VARCHAR(64) NOT NULL,
    channel_code VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    integration_type VARCHAR(48) NOT NULL,
    requested_model_id VARCHAR(128) NOT NULL,
    response_model_id VARCHAR(128) NULL,
    model_version VARCHAR(128) NULL,
    endpoint_url VARCHAR(1000) NOT NULL,
    endpoint_id VARCHAR(128) NULL,
    config_version BIGINT NOT NULL DEFAULT 1,
    provider_config_snapshot_json JSON NULL,
    provider_config_hash VARCHAR(64) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    call_status VARCHAR(32) NULL,
    search_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIRMED',
    search_requested TINYINT(1) NOT NULL DEFAULT 1,
    search_triggered TINYINT(1) NOT NULL DEFAULT 0,
    generation_skipped TINYINT(1) NOT NULL DEFAULT 0,
    search_evidence_json JSON NULL,
    answer MEDIUMTEXT NULL,
    brand_in_search TINYINT(1) NOT NULL DEFAULT 0,
    brand_in_answer TINYINT(1) NOT NULL DEFAULT 0,
    citation_confidence VARCHAR(16) NOT NULL DEFAULT 'NONE',
    brand_information_valid TINYINT(1) NULL,
    result_code VARCHAR(4) NULL,
    brand_dictionary_version VARCHAR(64) NULL,
    brand_dictionary_snapshot_json JSON NULL,
    usage_json JSON NULL,
    error_category VARCHAR(48) NULL,
    error_code VARCHAR(128) NULL,
    error_message VARCHAR(2000) NULL,
    last_heartbeat_at DATETIME NULL,
    attempt_deadline_at DATETIME NOT NULL,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    finalized_at DATETIME NULL,
    latency_ms BIGINT NULL,
    adapter_version VARCHAR(64) NOT NULL,
    classifier_version VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_poll_attempt_no (poll_result_id, attempt_no),
    KEY idx_poll_attempt_result_created (poll_result_id, created_at),
    KEY idx_poll_attempt_status_heartbeat (status, last_heartbeat_at),
    KEY idx_poll_attempt_deadline (status, attempt_deadline_at),
    KEY idx_poll_attempt_channel_created (channel_code, created_at),
    KEY idx_poll_attempt_root (root_attempt_id),
    KEY idx_poll_attempt_retry_of (retry_of_attempt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='immutable business attempts for web-search question polling';

CREATE TABLE poll_provider_calls (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    call_type VARCHAR(24) NOT NULL COMMENT 'SEARCH|MODEL_RESPONSE|GENERATION',
    sequence_no INT NOT NULL,
    retry_no INT NOT NULL DEFAULT 0,
    retry_of_call_id BIGINT NULL,
    provider VARCHAR(32) NOT NULL,
    endpoint_url VARCHAR(1000) NOT NULL,
    http_method VARCHAR(12) NOT NULL DEFAULT 'POST',
    provider_request_id VARCHAR(256) NULL,
    http_status INT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    error_category VARCHAR(48) NULL,
    error_code VARCHAR(128) NULL,
    error_message VARCHAR(2000) NULL,
    retryable TINYINT(1) NOT NULL DEFAULT 0,
    sanitized_request MEDIUMTEXT NULL,
    sanitized_response MEDIUMTEXT NULL,
    raw_request_encrypted MEDIUMTEXT NULL,
    raw_response_encrypted MEDIUMTEXT NULL,
    payload_key_version VARCHAR(64) NULL,
    raw_payload_purged_at DATETIME NULL,
    usage_json JSON NULL,
    deadline_at DATETIME NOT NULL,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    latency_ms BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_poll_provider_call_sequence (attempt_id, sequence_no),
    KEY idx_poll_provider_call_attempt (attempt_id, created_at),
    KEY idx_poll_provider_call_status_deadline (status, deadline_at),
    KEY idx_poll_provider_call_retry_of (retry_of_call_id),
    CONSTRAINT fk_poll_provider_call_attempt FOREIGN KEY (attempt_id)
        REFERENCES poll_invocation_attempts(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='every physical provider HTTP request';

CREATE TABLE poll_search_sources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    provider_call_id BIGINT NULL,
    search_event_index INT NOT NULL DEFAULT 0,
    rank_no INT NOT NULL DEFAULT 0,
    query_text VARCHAR(1000) NULL,
    title VARCHAR(1000) NULL,
    original_url TEXT NULL,
    normalized_url VARCHAR(2000) NULL,
    domain VARCHAR(255) NULL,
    snippet MEDIUMTEXT NULL,
    publish_time DATETIME NULL,
    brand_matched TINYINT(1) NOT NULL DEFAULT 0,
    brand_match_strength VARCHAR(16) NOT NULL DEFAULT 'NONE',
    matched_keywords_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_poll_source_attempt_event (attempt_id, search_event_index, rank_no),
    KEY idx_poll_source_call (provider_call_id),
    KEY idx_poll_source_domain (domain),
    CONSTRAINT fk_poll_source_attempt FOREIGN KEY (attempt_id)
        REFERENCES poll_invocation_attempts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_poll_source_call FOREIGN KEY (provider_call_id)
        REFERENCES poll_provider_calls(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='source occurrences by search event; cross-event duplicates are retained';

CREATE TABLE poll_citations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    source_id BIGINT NULL,
    citation_index INT NULL,
    answer_start INT NULL,
    answer_end INT NULL,
    citation_text VARCHAR(1000) NULL,
    confidence VARCHAR(16) NOT NULL DEFAULT 'NONE',
    validation_status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_poll_citation_attempt (attempt_id, citation_index),
    KEY idx_poll_citation_source (source_id),
    CONSTRAINT fk_poll_citation_attempt FOREIGN KEY (attempt_id)
        REFERENCES poll_invocation_attempts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_poll_citation_source FOREIGN KEY (source_id)
        REFERENCES poll_search_sources(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='validated answer-to-source citation relationships';

CREATE TABLE poll_audit_purge_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NULL,
    requested_by BIGINT NOT NULL,
    purge_reason VARCHAR(500) NOT NULL,
    scope_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PREPARED' COMMENT 'PREPARED|RUNNING|SUCCEEDED|FAILED',
    affected_rows_json JSON NULL,
    error_message VARCHAR(2000) NULL,
    audit_committed_at DATETIME NOT NULL,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_poll_audit_purge_project (project_id, created_at),
    KEY idx_poll_audit_purge_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='pre-committed audit records for controlled physical purge';

INSERT IGNORE INTO ai_platform_config (
    platform_code, channel_code, usage_scene, integration_type, provider_config_json, config_version,
    platform_name, priority_level, api_key, primary_key_ref, api_url, model_id, model_name,
    concurrency_limit, enabled, enabled_for_presale, presale_evaluate_enabled, enabled_for_article,
    enabled_for_geo_question, enabled_for_question_poll, max_retry, timeout_ms, rate_limit_qps,
    degraded, remark
) VALUES
('doubao_web', 'doubao', 'QUESTION_POLL_WEB', 'VOLCENGINE_RESPONSES_WEB',
 JSON_OBJECT('provider', 'volcengine', 'stream', false), 1,
 '豆包联网回答', 'P0', NULL, 'env://ARK_API_KEY',
 'https://ark.cn-beijing.volces.com/api/v3/responses', 'doubao-seed-2-1-pro-260628', '豆包联网模型',
 2, 0, 0, 0, 0, 0, 0, 1, 120000, 2, 0, 'Disabled until provider PoC succeeds'),
('deepseek_ark_web', 'deepseek', 'QUESTION_POLL_WEB', 'VOLCENGINE_RESPONSES_WEB',
 JSON_OBJECT('provider', 'volcengine', 'stream', false), 1,
 '火山方舟DeepSeek联网回答', 'P0', NULL, 'env://ARK_API_KEY',
 'https://ark.cn-beijing.volces.com/api/v3/responses', 'deepseek-v4-pro-260425', '火山方舟DeepSeek联网模型',
 2, 0, 0, 0, 0, 0, 0, 1, 180000, 2, 0, 'Disabled until DeepSeek Web Search PoC succeeds'),
('qwen_web', 'qwen', 'QUESTION_POLL_WEB', 'DASHSCOPE_NATIVE_WEB',
 JSON_OBJECT('provider', 'aliyun', 'forcedSearch', true, 'enableSource', true,
             'enableCitation', true, 'citationFormat', '[ref_<number>]', 'searchStrategy', 'turbo'), 1,
 '千问联网回答', 'P0', NULL, 'env://DASHSCOPE_API_KEY',
 'https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation',
 'qwen-plus', '千问联网模型',
 2, 0, 0, 0, 0, 0, 0, 1, 120000, 2, 0, 'Disabled until provider PoC succeeds'),
('tencent_search_web', 'yuanbao', 'QUESTION_POLL_WEB', 'TENCENT_TOKENHUB_RESPONSES_WEB',
 JSON_OBJECT('provider', 'tencent-tokenhub', 'stream', false,
             'searchContextSize', 'medium', 'searchSource', 'standard'), 1,
 '腾讯元宝联网回答', 'P0', NULL, 'env://TOKENHUB_API_KEY',
 'https://tokenhub.tencentmaas.com/v1/responses',
 'hy3-preview', '腾讯元宝 Hy3 preview 联网模型',
 2, 0, 0, 0, 0, 0, 0, 1, 150000, 2, 0, 'Disabled until TokenHub Responses Web Search PoC succeeds');
