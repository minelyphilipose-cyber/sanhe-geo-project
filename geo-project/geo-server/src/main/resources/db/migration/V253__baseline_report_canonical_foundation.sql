-- ============================================================
-- V253: baseline report canonical foundation
-- ============================================================

CREATE TABLE IF NOT EXISTS baseline_snapshot (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id                 BIGINT NOT NULL,
    company_id                 BIGINT NULL COMMENT 'Reserved for white-label tenant scoping',
    brand_id                   BIGINT NULL,
    run_seq                    INT NOT NULL DEFAULT 0 COMMENT 'Baseline run sequence; v1 freezes run_seq=0',
    status                     VARCHAR(24) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|SEALED|RECOMPUTED',
    schema_version             VARCHAR(64) NOT NULL,
    intent_rubric_version      VARCHAR(64) NOT NULL,
    algorithm_versions_json    JSON NOT NULL,
    selected_versions_json     JSON NOT NULL,
    source_poll_batch_id       BIGINT NULL,
    sealed_at                  DATETIME NULL,
    sealed_by                  BIGINT NULL,
    created_by                 BIGINT NOT NULL,
    created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_baseline_snapshot_project (project_id, status, id),
    KEY idx_baseline_snapshot_company (company_id),
    KEY idx_baseline_snapshot_brand (brand_id),
    CONSTRAINT fk_baseline_snapshot_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_baseline_snapshot_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_baseline_snapshot_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
    CONSTRAINT fk_baseline_snapshot_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
    CONSTRAINT fk_baseline_snapshot_sealed_by FOREIGN KEY (sealed_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable baseline report snapshot header';

CREATE TABLE IF NOT EXISTS baseline_question_snapshot (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    baseline_id                BIGINT NOT NULL,
    question_key               VARCHAR(64) NOT NULL COMMENT 'Canonical question id rendered to the frontend',
    source_keyword_result_id   BIGINT NULL,
    question_text              TEXT NOT NULL,
    value_tier                 VARCHAR(16) NOT NULL COMMENT 'HIGH|MID|LOW',
    source_question_tier       VARCHAR(8) NULL COMMENT 'Original A|B|C question tier',
    source_priority            VARCHAR(32) NULL,
    intent_type                VARCHAR(32) NOT NULL COMMENT 'RECOMMENDATION|COMPARISON|PROBLEM|AWARENESS|SCENE',
    scene_code                 VARCHAR(64) NULL,
    sort_order                 INT NOT NULL DEFAULT 0,
    created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_baseline_question_key (baseline_id, question_key),
    UNIQUE KEY uk_baseline_question_source (baseline_id, source_keyword_result_id),
    KEY idx_baseline_question_intent (baseline_id, intent_type, value_tier),
    CONSTRAINT fk_baseline_question_snapshot FOREIGN KEY (baseline_id) REFERENCES baseline_snapshot(id) ON DELETE CASCADE,
    CONSTRAINT fk_baseline_question_source FOREIGN KEY (source_keyword_result_id) REFERENCES keyword_group_result(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Frozen question scope for a baseline report';

CREATE TABLE IF NOT EXISTS baseline_collection_task (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    baseline_id                BIGINT NOT NULL,
    project_id                 BIGINT NOT NULL,
    status                     VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RUNNING|COMPLETED|PARTIAL_FAILED|FAILED|CANCELED',
    selected_platform_codes_json JSON NOT NULL,
    sample_per_cell            INT NOT NULL DEFAULT 3,
    question_count             INT NOT NULL DEFAULT 0,
    platform_count             INT NOT NULL DEFAULT 0,
    total_observation_count    INT NOT NULL DEFAULT 0,
    success_observation_count  INT NOT NULL DEFAULT 0,
    failed_observation_count   INT NOT NULL DEFAULT 0,
    score_count                INT NOT NULL DEFAULT 0,
    competitor_mention_count   INT NOT NULL DEFAULT 0,
    error_message              TEXT NULL,
    created_by                 BIGINT NOT NULL,
    started_at                 DATETIME NULL,
    finished_at                DATETIME NULL,
    created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_baseline_collection_task_snapshot (baseline_id, status, id),
    KEY idx_baseline_collection_task_project (project_id, status, id),
    CONSTRAINT fk_baseline_collection_task_snapshot FOREIGN KEY (baseline_id) REFERENCES baseline_snapshot(id) ON DELETE CASCADE,
    CONSTRAINT fk_baseline_collection_task_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_baseline_collection_task_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Async baseline observation collection progress task';

CREATE TABLE IF NOT EXISTS baseline_observation (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    baseline_id                BIGINT NOT NULL,
    question_snapshot_id       BIGINT NOT NULL,
    platform_code              VARCHAR(64) NOT NULL,
    platform_name              VARCHAR(128) NULL,
    sample_seq                 INT NOT NULL COMMENT '1-based sample sequence; target n=3',
    call_status                VARCHAR(24) NOT NULL COMMENT 'SUCCESS|FAILED',
    raw_response_text          LONGTEXT NULL,
    request_count              INT NULL,
    response_time_ms           BIGINT NULL,
    error_code                 VARCHAR(64) NULL,
    error_message              TEXT NULL,
    model_id                   VARCHAR(128) NULL,
    model_name                 VARCHAR(128) NULL,
    tested_at                  DATETIME NOT NULL,
    created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_baseline_observation_sample (baseline_id, question_snapshot_id, platform_code, sample_seq),
    KEY idx_baseline_observation_question (question_snapshot_id),
    KEY idx_baseline_observation_status (baseline_id, call_status),
    CONSTRAINT fk_baseline_observation_snapshot FOREIGN KEY (baseline_id) REFERENCES baseline_snapshot(id) ON DELETE CASCADE,
    CONSTRAINT fk_baseline_observation_question FOREIGN KEY (question_snapshot_id) REFERENCES baseline_question_snapshot(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable raw observation samples for baseline reports';

CREATE TABLE IF NOT EXISTS baseline_observation_score (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    baseline_id                BIGINT NOT NULL,
    observation_id             BIGINT NOT NULL,
    algorithm_version          VARCHAR(64) NOT NULL COMMENT 'Bundled score version for mentioned/recommended/ranking/sentiment/impression',
    mentioned                  TINYINT(1) NOT NULL DEFAULT 0,
    recommended                TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Recommended means suggested as an option in recommendation intent context',
    ranking_position           INT NULL,
    sentiment                  VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN' COMMENT 'POSITIVE|NEUTRAL|NEGATIVE|UNKNOWN',
    impression_state           VARCHAR(24) NOT NULL DEFAULT 'INFO_MISSING' COMMENT 'POSITIVE|NEUTRAL|NEGATIVE|INFO_MISSING|NO_AWARENESS',
    mention_type               VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'NONE|BRAND_EXACT|BRAND_ALIAS|SITE_ONLY|CONTACT_ONLY|COMPETITOR_ONLY|INVALID',
    judge_evidence             TEXT NULL,
    created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_baseline_score_version (observation_id, algorithm_version),
    KEY idx_baseline_score_snapshot_version (baseline_id, algorithm_version),
    CONSTRAINT fk_baseline_score_snapshot FOREIGN KEY (baseline_id) REFERENCES baseline_snapshot(id) ON DELETE CASCADE,
    CONSTRAINT fk_baseline_score_observation FOREIGN KEY (observation_id) REFERENCES baseline_observation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Versioned bundled interpretation score for each observation';

CREATE TABLE IF NOT EXISTS baseline_highlight_span (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    observation_id             BIGINT NOT NULL,
    algorithm_version          VARCHAR(64) NOT NULL,
    type                       VARCHAR(24) NOT NULL COMMENT 'BRAND|COMPETITOR|NEGATIVE',
    text                       VARCHAR(512) NOT NULL,
    start_offset               INT NOT NULL COMMENT 'Offset in full raw_response_text',
    end_offset                 INT NOT NULL COMMENT 'Offset in full raw_response_text',
    normalized_entity_id       BIGINT NULL,
    created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_baseline_highlight_observation (observation_id, algorithm_version),
    CONSTRAINT fk_baseline_highlight_observation FOREIGN KEY (observation_id) REFERENCES baseline_observation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Versioned evidence spans; excerpt rendering must rebase offsets';

CREATE TABLE IF NOT EXISTS baseline_competitor_source (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    baseline_id                BIGINT NOT NULL,
    competitor_id              BIGINT NULL,
    competitor_name            VARCHAR(128) NOT NULL,
    source_type                VARCHAR(32) NULL,
    source_url                 VARCHAR(512) NULL,
    source_note                VARCHAR(512) NULL,
    review_status             VARCHAR(24) NOT NULL DEFAULT 'UNVERIFIED' COMMENT 'UNVERIFIED|VERIFIED|REJECTED',
    verified_by               BIGINT NULL,
    verified_at               DATETIME NULL,
    created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_baseline_competitor_source (baseline_id, review_status),
    CONSTRAINT fk_baseline_competitor_source_snapshot FOREIGN KEY (baseline_id) REFERENCES baseline_snapshot(id) ON DELETE CASCADE,
    CONSTRAINT fk_baseline_competitor_source_verified_by FOREIGN KEY (verified_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Human-verified competitor attribution source';

CREATE TABLE IF NOT EXISTS baseline_competitor_mention (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT,
    baseline_id                BIGINT NOT NULL,
    observation_id             BIGINT NOT NULL,
    algorithm_version          VARCHAR(64) NOT NULL COMMENT 'Competitor normalization version',
    competitor_id              BIGINT NULL,
    normalized_name            VARCHAR(128) NOT NULL,
    raw_text                   VARCHAR(512) NOT NULL,
    mention_count              INT NOT NULL DEFAULT 1,
    tracked                    TINYINT(1) NOT NULL DEFAULT 0,
    start_offset               INT NULL,
    end_offset                 INT NULL,
    created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_baseline_competitor_version (baseline_id, algorithm_version, tracked),
    KEY idx_baseline_competitor_observation (observation_id, algorithm_version),
    CONSTRAINT fk_baseline_competitor_mention_snapshot FOREIGN KEY (baseline_id) REFERENCES baseline_snapshot(id) ON DELETE CASCADE,
    CONSTRAINT fk_baseline_competitor_mention_observation FOREIGN KEY (observation_id) REFERENCES baseline_observation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Versioned competitor mentions, including untracked mentions';

CREATE TABLE IF NOT EXISTS baseline_metric_snapshot (
    id                                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    baseline_id                         BIGINT NOT NULL,
    canonical_schema_version            VARCHAR(64) NOT NULL,
    score_algorithm_version             VARCHAR(64) NOT NULL,
    highlight_algorithm_version         VARCHAR(64) NOT NULL,
    competitor_normalization_version    VARCHAR(64) NOT NULL,
    canonical_aggregate_version         VARCHAR(64) NOT NULL COMMENT 'Owns coverage and band algorithms',
    canonical_json                      LONGTEXT NOT NULL,
    generated_at                        DATETIME NOT NULL,
    created_at                          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_baseline_metric_versions (baseline_id, score_algorithm_version, highlight_algorithm_version, competitor_normalization_version, canonical_aggregate_version),
    CONSTRAINT fk_baseline_metric_snapshot FOREIGN KEY (baseline_id) REFERENCES baseline_snapshot(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Recomputable materialized canonical report cache';

CREATE TABLE IF NOT EXISTS baseline_report_export (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    baseline_id          BIGINT NOT NULL,
    project_id           BIGINT NOT NULL,
    idempotency_key      VARCHAR(160) NOT NULL,
    export_profile       VARCHAR(64) NOT NULL DEFAULT 'PDF_A4_DPR2',
    file_format          VARCHAR(16) NOT NULL DEFAULT 'PDF',
    status               VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    error_msg            TEXT NULL,
    worker_id            VARCHAR(128) NULL,
    file_key             VARCHAR(512) NULL,
    file_size            BIGINT NULL,
    file_pages           INT NULL,
    snapshot_json        LONGTEXT NULL COMMENT 'Canonical JSON snapshot used for PDF export; no report-side recompute',
    render_token_id      VARCHAR(64) NULL,
    metrics_json         JSON NULL,
    trigger_user_id      BIGINT NOT NULL,
    trigger_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_baseline_export_idempotency (idempotency_key),
    KEY idx_baseline_export_project (project_id, baseline_id, status),
    KEY idx_baseline_export_pending (status, updated_at),
    CONSTRAINT fk_baseline_report_export_snapshot FOREIGN KEY (baseline_id) REFERENCES baseline_snapshot(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Baseline report PDF export task, rendered from canonical through Chromium';
