UPDATE poll_batch_shards s
JOIN dispatch_task t ON t.id = s.dispatch_task_id
SET s.status = 'failed',
    s.finished_at = COALESCE(s.finished_at, t.finished_at, NOW()),
    s.last_error = COALESCE(s.last_error, 'task execution timeout'),
    s.updated_at = NOW()
WHERE s.status = 'running'
  AND t.status = 'dead_letter'
  AND t.task_type = 'BI_DAILY_POLL'
  AND t.last_error = 'task execution timeout';

UPDATE poll_batches b
JOIN (
    SELECT batch_id,
           SUM(CASE WHEN status IN ('completed', 'failed') THEN 1 ELSE 0 END) AS terminal_count
    FROM poll_batch_shards
    GROUP BY batch_id
) s ON s.batch_id = b.id
SET b.completed_shard_count = s.terminal_count,
    b.updated_at = NOW()
WHERE b.finished_at IS NULL
  AND b.total_shard_count > 0
  AND b.completed_shard_count <> s.terminal_count;
