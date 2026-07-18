-- Model capability diagnostic foundation.
-- This migration creates only persistence and RBAC contracts. It does not enable execution.

CREATE TABLE ai_model_diagnostic_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(36) NOT NULL COMMENT 'client-visible UUID normalized by the server',
    operator_id BIGINT NOT NULL COMMENT 'session owner; all reads and appends are owner-scoped',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|CLOSED',
    next_turn_no INT NOT NULL DEFAULT 1 COMMENT 'next audit turn allocated under row lock',
    last_run_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_diag_session_owner_uuid (operator_id, session_id),
    KEY idx_ai_diag_session_owner_updated (operator_id, updated_at),
    KEY idx_ai_diag_session_last_run (last_run_at),
    CONSTRAINT chk_ai_diag_session_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT chk_ai_diag_session_next_turn CHECK (next_turn_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='operator-owned model diagnostic conversations';

CREATE TABLE ai_model_diagnostic_runs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_record_id BIGINT NOT NULL,
    session_id VARCHAR(36) NOT NULL COMMENT 'immutable client-visible session UUID snapshot',
    turn_no INT NOT NULL COMMENT 'audit turn; failed, rejected and abandoned runs also consume a turn',
    operator_id BIGINT NOT NULL,
    client_request_id VARCHAR(36) NOT NULL COMMENT 'operator-scoped idempotency UUID',
    request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,

    platform_config_id BIGINT NOT NULL,
    platform_code VARCHAR(64) NOT NULL,
    channel_code VARCHAR(32) NOT NULL,
    platform_name VARCHAR(128) NOT NULL,
    usage_scene VARCHAR(32) NOT NULL,
    integration_type VARCHAR(48) NOT NULL,
    config_version BIGINT NOT NULL,
    config_snapshot_json JSON NOT NULL COMMENT 'non-secret resolved configuration snapshot',
    config_snapshot_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    endpoint_url VARCHAR(1000) NOT NULL,

    diagnostic_mode VARCHAR(24) NOT NULL COMMENT 'BASIC_CHAT|WEB_SEARCH',
    test_mode VARCHAR(32) NOT NULL COMMENT 'FREE_CHAT|STANDARD_PROBE|PRODUCTION_POLL_TEMPLATE',
    response_mode VARCHAR(16) NOT NULL DEFAULT 'SYNC',
    probe_code VARCHAR(64) NULL,
    probe_version VARCHAR(32) NULL COMMENT 'actual server-resolved version, never trusted from the client',

    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING'
        COMMENT 'RUNNING|SUCCEEDED|FAILED|REJECTED|ABANDONED',
    conclusion VARCHAR(16) NULL COMMENT 'PASS|WARNING|FAIL; execution status remains independent',
    conclusion_reason VARCHAR(1000) NULL,
    authentication_status VARCHAR(16) NULL,
    generation_status VARCHAR(16) NULL,
    web_search_status VARCHAR(16) NULL,
    source_parsing_status VARCHAR(16) NULL,
    citation_parsing_status VARCHAR(16) NULL,
    evaluator_version VARCHAR(64) NULL,

    user_message MEDIUMTEXT NOT NULL COMMENT 'resolved current-turn user input',
    system_prompt MEDIUMTEXT NULL,
    request_messages_json JSON NOT NULL COMMENT 'complete server-built provider context',
    answer MEDIUMTEXT NULL,

    provider_request_id VARCHAR(256) NULL,
    requested_model_id VARCHAR(128) NOT NULL,
    response_model_id VARCHAR(128) NULL,
    http_status INT NULL,

    search_status VARCHAR(32) NULL,
    search_evidence_json JSON NULL,
    sources_json JSON NULL,
    citations_json JSON NULL,
    usage_json JSON NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    total_tokens INT NULL,
    web_search_call_count INT NULL,
    source_count INT NULL,
    valid_source_count INT NULL,
    citation_count INT NULL,
    valid_citation_count INT NULL,

    sanitized_request MEDIUMTEXT NULL,
    sanitized_response MEDIUMTEXT NULL,
    error_category VARCHAR(48) NULL,
    error_code VARCHAR(128) NULL,
    error_message VARCHAR(2000) NULL,

    deadline_at DATETIME NOT NULL COMMENT 'immutable end-to-end deadline calculated at run creation',
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    duration_ms BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'optimistic state version',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_ai_diag_run_owner_client_request (operator_id, client_request_id),
    UNIQUE KEY uk_ai_diag_run_session_turn (operator_id, session_id, turn_no),
    KEY idx_ai_diag_run_session_created (session_record_id, created_at),
    KEY idx_ai_diag_run_config_created (platform_config_id, created_at),
    KEY idx_ai_diag_run_operator_created (operator_id, created_at),
    KEY idx_ai_diag_run_conclusion_created (conclusion, created_at),
    KEY idx_ai_diag_run_status_created (status, created_at),
    KEY idx_ai_diag_run_status_deadline (status, deadline_at),
    KEY idx_ai_diag_run_cleanup (created_at, id),

    CONSTRAINT fk_ai_diag_run_session FOREIGN KEY (session_record_id)
        REFERENCES ai_model_diagnostic_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT chk_ai_diag_run_turn CHECK (turn_no > 0),
    CONSTRAINT chk_ai_diag_run_mode CHECK (diagnostic_mode IN ('BASIC_CHAT', 'WEB_SEARCH')),
    CONSTRAINT chk_ai_diag_run_test_mode CHECK (
        test_mode IN ('FREE_CHAT', 'STANDARD_PROBE', 'PRODUCTION_POLL_TEMPLATE')
    ),
    CONSTRAINT chk_ai_diag_run_response_mode CHECK (response_mode = 'SYNC'),
    CONSTRAINT chk_ai_diag_run_status CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'REJECTED', 'ABANDONED')
    ),
    CONSTRAINT chk_ai_diag_run_status_conclusion CHECK (
        (status = 'SUCCEEDED' AND conclusion IN ('PASS', 'WARNING', 'FAIL'))
        OR (status = 'FAILED' AND conclusion = 'FAIL')
        OR (status IN ('RUNNING', 'REJECTED', 'ABANDONED') AND conclusion IS NULL)
    ),
    CONSTRAINT chk_ai_diag_run_capability_status CHECK (
        (authentication_status IS NULL OR authentication_status IN ('PASS', 'WARNING', 'FAIL', 'NOT_APPLICABLE'))
        AND (generation_status IS NULL OR generation_status IN ('PASS', 'WARNING', 'FAIL', 'NOT_APPLICABLE'))
        AND (web_search_status IS NULL OR web_search_status IN ('PASS', 'WARNING', 'FAIL', 'NOT_APPLICABLE'))
        AND (source_parsing_status IS NULL OR source_parsing_status IN ('PASS', 'WARNING', 'FAIL', 'NOT_APPLICABLE'))
        AND (citation_parsing_status IS NULL OR citation_parsing_status IN ('PASS', 'WARNING', 'FAIL', 'NOT_APPLICABLE'))
    ),
    CONSTRAINT chk_ai_diag_run_metrics CHECK (
        (prompt_tokens IS NULL OR prompt_tokens >= 0)
        AND (completion_tokens IS NULL OR completion_tokens >= 0)
        AND (total_tokens IS NULL OR total_tokens >= 0)
        AND (web_search_call_count IS NULL OR web_search_call_count >= 0)
        AND (source_count IS NULL OR source_count >= 0)
        AND (valid_source_count IS NULL OR valid_source_count >= 0)
        AND (citation_count IS NULL OR citation_count >= 0)
        AND (valid_citation_count IS NULL OR valid_citation_count >= 0)
        AND (duration_ms IS NULL OR duration_ms >= 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='isolated, auditable model capability diagnostic runs';

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'ai.platform.diagnose', 'AI Platform Diagnose', 'ai_platform', 'diagnose', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'ai.platform.diagnose'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key = 'ai.platform.diagnose'
WHERE r.role_key IN ('manager', 'super_admin')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
