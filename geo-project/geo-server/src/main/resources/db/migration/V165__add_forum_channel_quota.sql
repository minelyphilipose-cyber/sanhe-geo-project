-- ============================================================
-- V165: add independent forum distribution quota
-- ============================================================

INSERT INTO package_channel_quota_config
  (package_plan_id, channel_code, period_type, quota_limit, enabled)
SELECT p.id, 'forum', 'week', 1, 1
FROM package_plan p
WHERE NOT EXISTS (
  SELECT 1
  FROM package_channel_quota_config q
  WHERE q.package_plan_id = p.id
    AND q.channel_code = 'forum'
);

UPDATE company_package_binding
SET channel_quota_snapshot = JSON_ARRAY_APPEND(
  channel_quota_snapshot,
  '$',
  JSON_OBJECT(
    'channelCode', 'forum',
    'periodType', 'week',
    'quotaLimit', 1,
    'enabled', true
  )
)
WHERE JSON_CONTAINS(channel_quota_snapshot, JSON_OBJECT('channelCode', 'forum'), '$') = 0;
