-- Keep published_url_pending inside the active uniqueness window. A schedule in
-- this state has completed publishing but still owns its idempotency/task binding
-- while the public URL is being collected.
ALTER TABLE self_media_publish_schedule
  MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'pending'
    COMMENT 'pending/filling/filled_verified/scheduling/scheduled/publish_due/checking_publish_result/published_confirmed/published_url_pending/publish_unknown/schedule_failed/publish_failed/cancelled/cancel_pending_platform/manual_required/routed_to_semi_auto',
  MODIFY COLUMN active_unique_key TINYINT GENERATED ALWAYS AS (
    IF(status IN (
      'pending',
      'filling',
      'filled_verified',
      'scheduling',
      'scheduled',
      'publish_due',
      'checking_publish_result',
      'published_url_pending',
      'publish_unknown',
      'cancel_pending_platform'
    ), 1, NULL)
  ) STORED COMMENT 'active-only uniqueness flag',
  MODIFY COLUMN active_distribution_task_id BIGINT UNSIGNED GENERATED ALWAYS AS (
    IF(status IN (
      'pending',
      'filling',
      'filled_verified',
      'scheduling',
      'scheduled',
      'publish_due',
      'checking_publish_result',
      'published_url_pending',
      'publish_unknown',
      'cancel_pending_platform'
    ), distribution_task_id, NULL)
  ) STORED COMMENT 'active-only distribution task binding';
