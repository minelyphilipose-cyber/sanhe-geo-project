-- Remove unused project-level default schedule strategy.
-- Actual auto-schedule strategy is resolved per platform at request/batch time.

ALTER TABLE project_self_media_schedule_config
  DROP COLUMN default_schedule_strategy;
