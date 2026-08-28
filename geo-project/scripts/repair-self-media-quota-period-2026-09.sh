#!/usr/bin/env bash
set -euo pipefail

# One-off recovery for the 2026-08-26 self-media quota-period incident.
# Default mode is read-only. The only write mode is an explicit --apply.

MODE="check"
umask 077
BACKUP_DIR="${BACKUP_DIR:-./backups}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-geo-mysql}"
SERVER_CONTAINER="${SERVER_CONTAINER:-geo-server}"
INCIDENT_REASON="INCIDENT-2026-08-SELF-MEDIA-CROSS-MONTH-QUOTA"

case "${1:-}" in
  ""|--check) MODE="check" ;;
  --apply) MODE="apply" ;;
  *)
    echo "Usage: $0 [--check|--apply]" >&2
    exit 2
    ;;
esac

if docker info >/dev/null 2>&1; then
  DOCKER=(docker)
else
  DOCKER=(sudo docker)
fi

mysql_run() {
  "${DOCKER[@]}" exec -i "${MYSQL_CONTAINER}" sh -lc \
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE" --batch --raw'
}

echo "[guard] checking the running geo-server job switch"
job_enabled="$("${DOCKER[@]}" exec "${SERVER_CONTAINER}" sh -lc \
  'printf "%s" "${GEO_SELF_MEDIA_AUTO_SCHEDULE_JOB_ENABLED:-<unset>}"')"
if [[ "${job_enabled}" != "false" ]]; then
  echo "ABORT: ${SERVER_CONTAINER} has GEO_SELF_MEDIA_AUTO_SCHEDULE_JOB_ENABLED=${job_enabled}; expected false." >&2
  exit 1
fi

echo "[check] affected rows and write gates"
mysql_run <<'SQL'
SELECT NOW() AS db_now, @@session.time_zone AS session_timezone;

SELECT
  COUNT(*) AS item_count,
  COUNT(DISTINCT i.batch_id) AS batch_count,
  SUM(i.status <> 'planned') AS bad_item_status,
  SUM(i.quota_reservation_status <> 'reserved') AS bad_quota_status,
  SUM(i.article_id IS NOT NULL OR i.schedule_id IS NOT NULL) AS materialized_items,
  SUM(i.generation_batch_id IS NOT NULL OR i.generation_task_id IS NOT NULL
      OR i.generation_claim_token IS NOT NULL OR i.generation_claimed_at IS NOT NULL) AS generation_started
FROM project_self_media_schedule_item i
WHERE i.batch_id IN (35,37,38,39,40,41,42,43);

SELECT id, project_id, company_id, brand_id, target_month, trigger_mode, status,
       planned_count, created_count, rejected_count, failure_message
FROM project_self_media_schedule_batch
WHERE id IN (35,37,38,39,40,41,42,43)
ORDER BY id;

SELECT
  COUNT(*) AS ledger_count,
  COALESCE(SUM(l.delta_count), 0) AS reserved_count,
  SUM(l.biz_type <> 'project_self_media_plan') AS bad_biz_type,
  SUM(l.status <> 'reserved') AS bad_ledger_status,
  SUM(CAST(l.period_key AS BINARY) <> CAST('2026-08' AS BINARY)) AS bad_period
FROM project_self_media_schedule_item i
JOIN company_channel_quota_ledger l
  ON l.biz_id = CAST(i.id AS CHAR)
WHERE i.batch_id IN (35,37,38,39,40,41,42,43)
  AND l.biz_type = 'project_self_media_plan';

SELECT l.company_id, l.channel_code, l.period_type, l.period_key,
       SUM(l.delta_count) AS refund_count,
       u.used_count,
       u.used_count - SUM(l.delta_count) AS used_count_after
FROM project_self_media_schedule_item i
JOIN company_channel_quota_ledger l
  ON l.biz_type = 'project_self_media_plan'
 AND l.biz_id = CAST(i.id AS CHAR)
LEFT JOIN company_channel_quota_usage u
  ON u.company_id = l.company_id
 AND u.channel_code = l.channel_code
 AND u.period_type = l.period_type
 AND u.period_key = l.period_key
WHERE i.batch_id IN (35,37,38,39,40,41,42,43)
GROUP BY l.company_id, l.channel_code, l.period_type, l.period_key, u.used_count
ORDER BY l.company_id, l.channel_code;

SELECT id, project_id, target_month, trigger_mode, status, retry_count,
       planned_execute_at, next_attempt_at, failure_message
FROM project_self_media_schedule_plan
WHERE id IN (25,27,28,29,30,31,32)
ORDER BY id;
SQL

if [[ "${MODE}" == "check" ]]; then
  echo "Read-only check finished. Review every result before running --apply."
  exit 0
fi

mkdir -p "${BACKUP_DIR}"
timestamp="$(date +%Y%m%d-%H%M%S)"
backup_file="${BACKUP_DIR}/self-media-quota-period-2026-09-${timestamp}.sql"
echo "[backup] writing ${backup_file}"
"${DOCKER[@]}" exec "${MYSQL_CONTAINER}" sh -lc \
  'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --default-character-set=utf8mb4 --single-transaction --skip-lock-tables --no-tablespaces -u "$MYSQL_USER" "$MYSQL_DATABASE" company_channel_quota_usage company_channel_quota_ledger project_self_media_schedule_plan project_self_media_schedule_batch project_self_media_schedule_item' \
  > "${backup_file}"
test -s "${backup_file}"

echo "[apply] starting guarded transaction"
mysql_run <<SQL
SET @incident_reason = '${INCIDENT_REASON}';
SET @expected_items = 412;
SET @expected_batches = 8;
SET @expected_plans = 7;

CREATE TEMPORARY TABLE repair_guard (
  ok TINYINT NOT NULL,
  CONSTRAINT chk_repair_guard CHECK (ok = 1)
);

START TRANSACTION;

-- Lock the explicit whitelist before evaluating the write gates.
SELECT id
FROM project_self_media_schedule_batch
WHERE id IN (35,37,38,39,40,41,42,43)
ORDER BY id
FOR UPDATE;

SELECT id
FROM project_self_media_schedule_item
WHERE batch_id IN (35,37,38,39,40,41,42,43)
ORDER BY id
FOR UPDATE;

SELECT id
FROM project_self_media_schedule_plan
WHERE id IN (25,27,28,29,30,31,32)
ORDER BY id
FOR UPDATE;

-- Any failed INSERT violates the CHECK constraint. The mysql client exits and
-- closes this connection, which rolls the open transaction back.
INSERT INTO repair_guard
SELECT COUNT(*) = @expected_batches
   AND SUM(CAST(target_month AS BINARY) <> CAST('2026-09' AS BINARY)) = 0
   AND SUM(status <> 'planned') = 0
   AND SUM(
     (id = 35 AND project_id = 2) OR
     (id = 37 AND project_id = 4) OR
     (id = 38 AND project_id = 5) OR
     (id = 39 AND project_id = 7) OR
     (id = 40 AND project_id = 8) OR
     (id = 41 AND project_id = 11) OR
     (id = 42 AND project_id = 12) OR
     (id = 43 AND project_id = 22)
   ) = @expected_batches
FROM project_self_media_schedule_batch
WHERE id IN (35,37,38,39,40,41,42,43);

INSERT INTO repair_guard
SELECT COUNT(*) = @expected_items
   AND COUNT(DISTINCT batch_id) = @expected_batches
   AND SUM(status <> 'planned') = 0
   AND SUM(quota_reservation_status <> 'reserved') = 0
   AND SUM(article_id IS NOT NULL OR schedule_id IS NOT NULL) = 0
   AND SUM(generation_batch_id IS NOT NULL OR generation_task_id IS NOT NULL
           OR generation_claim_token IS NOT NULL OR generation_claimed_at IS NOT NULL) = 0
FROM project_self_media_schedule_item
WHERE batch_id IN (35,37,38,39,40,41,42,43);

INSERT INTO repair_guard
SELECT COUNT(*) = @expected_items
   AND COALESCE(SUM(l.delta_count), 0) = @expected_items
   AND SUM(l.status <> 'reserved') = 0
   AND SUM(CAST(l.period_key AS BINARY) <> CAST('2026-08' AS BINARY)) = 0
FROM project_self_media_schedule_item i
JOIN company_channel_quota_ledger l
  ON l.biz_type = 'project_self_media_plan'
 AND l.biz_id = CAST(i.id AS CHAR)
WHERE i.batch_id IN (35,37,38,39,40,41,42,43);

INSERT INTO repair_guard
SELECT COUNT(*) = @expected_plans
   AND SUM(CAST(target_month AS BINARY) <> CAST('2026-09' AS BINARY)) = 0
   AND SUM(status <> 'completed') = 0
   AND SUM(
     (id = 25 AND project_id = 2) OR
     (id = 27 AND project_id = 4) OR
     (id = 28 AND project_id = 5) OR
     (id = 29 AND project_id = 7) OR
     (id = 30 AND project_id = 8) OR
     (id = 31 AND project_id = 11) OR
     (id = 32 AND project_id = 12)
   ) = @expected_plans
FROM project_self_media_schedule_plan
WHERE id IN (25,27,28,29,30,31,32);

INSERT INTO repair_guard
SELECT COUNT(*) = 0
FROM (
  SELECT l.company_id, l.channel_code, l.period_type, l.period_key,
         SUM(l.delta_count) AS refund_count
  FROM project_self_media_schedule_item i
  JOIN company_channel_quota_ledger l
    ON l.biz_type = 'project_self_media_plan'
   AND l.biz_id = CAST(i.id AS CHAR)
  WHERE i.batch_id IN (35,37,38,39,40,41,42,43)
    AND l.status = 'reserved'
    AND CAST(l.period_key AS BINARY) = CAST('2026-08' AS BINARY)
  GROUP BY l.company_id, l.channel_code, l.period_type, l.period_key
) affected
LEFT JOIN company_channel_quota_usage u
  ON u.company_id = affected.company_id
 AND u.channel_code = affected.channel_code
 AND u.period_type = affected.period_type
 AND u.period_key = affected.period_key
WHERE u.id IS NULL OR u.used_count < affected.refund_count;

SET @usage_groups = (
  SELECT COUNT(*)
  FROM (
    SELECT l.company_id, l.channel_code, l.period_type, l.period_key
    FROM project_self_media_schedule_item i
    JOIN company_channel_quota_ledger l
      ON l.biz_type = 'project_self_media_plan'
     AND l.biz_id = CAST(i.id AS CHAR)
    WHERE i.batch_id IN (35,37,38,39,40,41,42,43)
      AND l.status = 'reserved'
      AND CAST(l.period_key AS BINARY) = CAST('2026-08' AS BINARY)
    GROUP BY l.company_id, l.channel_code, l.period_type, l.period_key
  ) groups_to_refund
);

UPDATE company_channel_quota_usage u
JOIN (
  SELECT l.company_id, l.channel_code, l.period_type, l.period_key,
         SUM(l.delta_count) AS refund_count
  FROM project_self_media_schedule_item i
  JOIN company_channel_quota_ledger l
    ON l.biz_type = 'project_self_media_plan'
   AND l.biz_id = CAST(i.id AS CHAR)
  WHERE i.batch_id IN (35,37,38,39,40,41,42,43)
    AND l.status = 'reserved'
    AND CAST(l.period_key AS BINARY) = CAST('2026-08' AS BINARY)
  GROUP BY l.company_id, l.channel_code, l.period_type, l.period_key
) affected
  ON u.company_id = affected.company_id
 AND u.channel_code = affected.channel_code
 AND u.period_type = affected.period_type
 AND u.period_key = affected.period_key
SET u.used_count = u.used_count - affected.refund_count,
    u.updated_at = NOW()
WHERE u.used_count >= affected.refund_count;
SET @updated_usage_groups = ROW_COUNT();
INSERT INTO repair_guard SELECT @updated_usage_groups = @usage_groups;

UPDATE company_channel_quota_ledger l
JOIN project_self_media_schedule_item i
  ON l.biz_type = 'project_self_media_plan'
 AND l.biz_id = CAST(i.id AS CHAR)
SET l.status = 'refunded',
    l.refunded_at = NOW(),
    l.updated_at = NOW()
WHERE i.batch_id IN (35,37,38,39,40,41,42,43)
  AND l.status = 'reserved'
  AND CAST(l.period_key AS BINARY) = CAST('2026-08' AS BINARY);
SET @updated_ledgers = ROW_COUNT();
INSERT INTO repair_guard SELECT @updated_ledgers = @expected_items;

UPDATE project_self_media_schedule_item
SET status = 'cancelled',
    quota_reservation_status = 'refunded',
    cancelled_at = NOW(),
    cancellation_reason = @incident_reason,
    updated_at = NOW()
WHERE batch_id IN (35,37,38,39,40,41,42,43)
  AND status = 'planned'
  AND quota_reservation_status = 'reserved';
SET @updated_items = ROW_COUNT();
INSERT INTO repair_guard SELECT @updated_items = @expected_items;

UPDATE project_self_media_schedule_batch
SET status = 'cancelled',
    failure_message = @incident_reason,
    updated_at = NOW()
WHERE id IN (35,37,38,39,40,41,42,43)
  AND status = 'planned';
SET @updated_batches = ROW_COUNT();
INSERT INTO repair_guard SELECT @updated_batches = @expected_batches;

UPDATE project_self_media_schedule_plan
SET status = 'pending',
    retry_count = 0,
    planned_execute_at = NOW(),
    next_attempt_at = NOW(),
    failure_message = NULL,
    updated_at = NOW()
WHERE id IN (25,27,28,29,30,31,32)
  AND status = 'completed';
SET @updated_plans = ROW_COUNT();
INSERT INTO repair_guard SELECT @updated_plans = @expected_plans;

-- Final in-transaction invariants.
INSERT INTO repair_guard
SELECT COUNT(*) = @expected_items AND SUM(status <> 'refunded') = 0
FROM company_channel_quota_ledger
WHERE biz_type = 'project_self_media_plan'
  AND biz_id IN (
    SELECT CAST(id AS CHAR)
    FROM project_self_media_schedule_item
    WHERE batch_id IN (35,37,38,39,40,41,42,43)
  );

INSERT INTO repair_guard
SELECT COUNT(*) = 0
FROM company_channel_quota_usage u
JOIN (
  SELECT DISTINCT l.company_id, l.channel_code, l.period_type, l.period_key
  FROM project_self_media_schedule_item i
  JOIN company_channel_quota_ledger l
    ON l.biz_type = 'project_self_media_plan'
   AND l.biz_id = CAST(i.id AS CHAR)
  WHERE i.batch_id IN (35,37,38,39,40,41,42,43)
) affected
  ON u.company_id = affected.company_id
 AND u.channel_code = affected.channel_code
 AND u.period_type = affected.period_type
 AND u.period_key = affected.period_key
WHERE u.used_count < 0;

COMMIT;

SELECT @updated_usage_groups AS updated_usage_groups,
       @updated_ledgers AS refunded_ledgers,
       @updated_items AS cancelled_items,
       @updated_batches AS cancelled_batches,
       @updated_plans AS reset_plans;
SQL

echo "[verify] post-transaction state"
mysql_run <<'SQL'
SELECT i.status, i.quota_reservation_status, COUNT(*) AS item_count
FROM project_self_media_schedule_item i
WHERE i.batch_id IN (35,37,38,39,40,41,42,43)
GROUP BY i.status, i.quota_reservation_status;

SELECT l.period_key, l.status, COUNT(*) AS ledger_count, SUM(l.delta_count) AS quota_count
FROM project_self_media_schedule_item i
JOIN company_channel_quota_ledger l
  ON l.biz_type = 'project_self_media_plan'
 AND l.biz_id = CAST(i.id AS CHAR)
WHERE i.batch_id IN (35,37,38,39,40,41,42,43)
GROUP BY l.period_key, l.status;

SELECT id, project_id, status, retry_count, planned_execute_at, next_attempt_at
FROM project_self_media_schedule_plan
WHERE id IN (25,27,28,29,30,31,32)
ORDER BY id;
SQL

echo "Recovery transaction committed. Backup: ${backup_file}"
echo "Keep the job disabled, deploy the fixed server, then follow the runbook for batch 43 rebuild and final acceptance."
