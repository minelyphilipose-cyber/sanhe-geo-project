-- ============================================================
-- V229: report period freeze snapshots and guard locks
-- ============================================================

CREATE TABLE IF NOT EXISTS report_period_freeze_guard (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id          BIGINT       NOT NULL,
  report_type         VARCHAR(32)  NOT NULL COMMENT 'report type consuming poll detail, v1 enables quarterly only',
  period_key          VARCHAR(16)  NOT NULL COMMENT 'calendar period key, e.g. 2026Q1',
  lock_owner          VARCHAR(64)  NULL,
  lock_expires_at     DATETIME     NULL,
  acquired_at         DATETIME     NULL,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_report_freeze_guard_period (project_id, report_type, period_key),
  KEY idx_report_freeze_guard_expires (lock_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='guard rows for report period freeze acquire/TTL/takeover';

CREATE TABLE IF NOT EXISTS report_period_freeze (
  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  project_id          BIGINT       NOT NULL,
  report_type         VARCHAR(32)  NOT NULL COMMENT 'report type consuming poll detail, v1 enables quarterly only',
  period_key          VARCHAR(16)  NOT NULL COMMENT 'calendar period key, e.g. 2026Q1',
  period_start        DATE         NOT NULL,
  period_end          DATE         NOT NULL,
  version_no          INT          NOT NULL,
  status              VARCHAR(24)  NOT NULL COMMENT 'CREATING/FROZEN/FAILED',
  source_checksum     CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 checksum of ordered frozen poll detail rows',
  snapshot_object_key VARCHAR(512) NULL COMMENT 'logical object key only; no bucket/endpoint/url',
  object_checksum     CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'SHA-256 checksum of uploaded freeze object bytes',
  object_size_bytes   BIGINT       NULL,
  source_row_count    INT          NOT NULL DEFAULT 0,
  metrics_json        JSON         NULL,
  lock_owner          VARCHAR(64)  NULL,
  lock_expires_at     DATETIME     NULL,
  freeze_started_at   DATETIME     NULL,
  frozen_at           DATETIME     NULL,
  failed_at           DATETIME     NULL,
  error_message       VARCHAR(2000) NULL,
  created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_report_period_freeze_version (project_id, report_type, period_key, version_no),
  KEY idx_report_period_freeze_lookup (project_id, report_type, period_key, status, version_no),
  KEY idx_report_period_freeze_status (status, updated_at),
  KEY idx_report_period_freeze_object (snapshot_object_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='immutable frozen poll detail snapshots for report periods';
