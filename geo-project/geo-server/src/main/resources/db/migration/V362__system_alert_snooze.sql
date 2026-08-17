ALTER TABLE system_alerts
  ADD COLUMN snoozed_until DATETIME NULL AFTER read_at,
  ADD KEY idx_system_alert_snoozed (is_resolved, snoozed_until);
