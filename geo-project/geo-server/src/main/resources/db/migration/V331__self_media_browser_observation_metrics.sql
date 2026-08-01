-- V2 phase 1 stores helper-level browser observation aggregates only.
-- Individual browser targets remain in the local helper registry.

ALTER TABLE local_agent_runtime_status
  ADD COLUMN runtime_state VARCHAR(32) NULL AFTER capabilities_json,
  ADD COLUMN resource_metrics_json JSON NULL AFTER runtime_state,
  ADD COLUMN last_cleanup_at DATETIME NULL AFTER resource_metrics_json,
  ADD COLUMN helper_boot_id VARCHAR(64) NULL AFTER last_cleanup_at,
  ADD COLUMN policy_version BIGINT NULL AFTER helper_boot_id;

CREATE INDEX idx_local_agent_runtime_helper_boot
  ON local_agent_runtime_status (helper_boot_id, last_seen_at);
