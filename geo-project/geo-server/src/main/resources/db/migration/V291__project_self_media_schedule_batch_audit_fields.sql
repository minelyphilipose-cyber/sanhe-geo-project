ALTER TABLE project_self_media_schedule_batch
  ADD COLUMN requested_count INT NULL AFTER rejected_count,
  ADD COLUMN deficit_count INT NULL AFTER requested_count,
  ADD COLUMN carry_over_count INT NULL AFTER deficit_count,
  ADD COLUMN decision_operator_id BIGINT NULL AFTER carry_over_count,
  ADD COLUMN decision_reason VARCHAR(512) NULL AFTER decision_operator_id,
  ADD COLUMN capacity_snapshot_json JSON NULL AFTER decision_reason;
