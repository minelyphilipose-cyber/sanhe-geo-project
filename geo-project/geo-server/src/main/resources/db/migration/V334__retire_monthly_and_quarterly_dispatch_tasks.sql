-- Monthly and quarterly postsale reports are no longer part of the product.
-- Cancel only non-terminal work; preserve completed and dead-letter rows as history.
UPDATE dispatch_task
SET status = 'cancelled',
    finished_at = COALESCE(finished_at, CURRENT_TIMESTAMP),
    next_retry_at = NULL,
    timeout_at = NULL,
    last_error = 'monthly and quarterly report tasks are retired by product policy'
WHERE task_type IN ('MONTHLY_REPORT', 'QUARTERLY_REPORT')
  AND status IN ('pending', 'running', 'retry_pending');

-- Close alerts emitted by the obsolete retry path without deleting audit evidence.
UPDATE dispatch_alert alert_row
JOIN dispatch_task task_row ON task_row.id = alert_row.task_id
SET alert_row.status = 'resolved',
    alert_row.resolved_at = COALESCE(alert_row.resolved_at, CURRENT_TIMESTAMP)
WHERE alert_row.status = 'open'
  AND task_row.task_type IN ('MONTHLY_REPORT', 'QUARTERLY_REPORT')
  AND alert_row.title IN (
      'Dispatch task failed and will retry',
      'Dispatch task entered dead letter'
  );
