-- Sohu account onboarding is independent of automated publishing verification.
INSERT IGNORE INTO self_media_schedule_capability (
    platform, verification_status, supports_schedule, v1_strategy, notes
) VALUES (
    'sohu', 'unverified', 0, 'pending',
    '账号与环境已开放配置；自动发文需完成实际账号测试后验证启用。'
);

-- Existing package templates receive the same entitlement as Toutiao.
-- Quota consumption and ledgers are independent of these configuration row IDs.
DELETE sohu
FROM package_channel_quota_config sohu
JOIN package_channel_quota_config toutiao
  ON toutiao.package_plan_id = sohu.package_plan_id
 AND toutiao.channel_code = 'self_media:toutiao'
WHERE sohu.channel_code = 'self_media:sohu';

INSERT INTO package_channel_quota_config (
    package_plan_id, channel_code, period_type, quota_limit, enabled
)
SELECT package_plan_id, 'self_media:sohu', period_type, quota_limit, enabled
FROM package_channel_quota_config
WHERE channel_code = 'self_media:toutiao';

-- Copy the customer's SIGNED Toutiao entitlement, which may differ from today's
-- package template. Only legacy snapshots without Toutiao use the template.
CREATE TEMPORARY TABLE sohu_quota_backfill (
    binding_id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    sohu_quota JSON NOT NULL
);

INSERT INTO sohu_quota_backfill (binding_id, company_id, sohu_quota)
SELECT b.id, b.company_id,
       JSON_SET(COALESCE(
           JSON_EXTRACT(b.channel_quota_snapshot,
               REPLACE(JSON_UNQUOTE(JSON_SEARCH(b.channel_quota_snapshot,
                   'one', 'self_media:toutiao', NULL, '$[*].channelCode')),
                   '.channelCode', '')),
           JSON_OBJECT('channelCode', 'self_media:toutiao',
               'periodType', toutiao.period_type,
               'quotaLimit', toutiao.quota_limit,
               'enabled', IF(toutiao.enabled = 1, TRUE, FALSE))
       ), '$.channelCode', 'self_media:sohu')
FROM company_package_binding b
LEFT JOIN package_channel_quota_config toutiao
  ON toutiao.package_plan_id = b.package_plan_id
 AND toutiao.channel_code = 'self_media:toutiao'
WHERE b.status = 'active' AND b.active_flag = 1
  AND (JSON_SEARCH(b.channel_quota_snapshot, 'one', 'self_media:toutiao',
          NULL, '$[*].channelCode') IS NOT NULL OR toutiao.id IS NOT NULL);

-- Update only Sohu in each snapshot; preserve other locked customer rights,
-- partner visibility, metadata, NULL snapshots and historical inactive bindings.
UPDATE company_package_binding b
JOIN sohu_quota_backfill q ON q.binding_id = b.id
SET b.channel_quota_snapshot = CASE
        WHEN JSON_SEARCH(b.channel_quota_snapshot, 'one', 'self_media:sohu',
                 NULL, '$[*].channelCode') IS NULL
        THEN JSON_ARRAY_APPEND(b.channel_quota_snapshot, '$', q.sohu_quota)
        ELSE JSON_SET(b.channel_quota_snapshot,
            REPLACE(JSON_UNQUOTE(JSON_SEARCH(b.channel_quota_snapshot,
                'one', 'self_media:sohu', NULL, '$[*].channelCode')),
                '.channelCode', ''), q.sohu_quota)
    END,
    b.package_snapshot_json = CASE
        WHEN b.package_snapshot_json IS NULL THEN NULL
        WHEN JSON_SEARCH(b.package_snapshot_json, 'one', 'self_media:sohu',
                 NULL, '$.channelQuotaSnapshot[*].channelCode') IS NULL
        THEN JSON_SET(b.package_snapshot_json, '$.channelQuotaSnapshot',
            JSON_ARRAY_APPEND(COALESCE(JSON_EXTRACT(b.package_snapshot_json,
                '$.channelQuotaSnapshot'), JSON_ARRAY()), '$', q.sohu_quota))
        ELSE JSON_SET(b.package_snapshot_json,
            REPLACE(JSON_UNQUOTE(JSON_SEARCH(b.package_snapshot_json,
                'one', 'self_media:sohu', NULL, '$.channelQuotaSnapshot[*].channelCode')),
                '.channelCode', ''), q.sohu_quota)
    END,
    b.partner_visible_snapshot_json = CASE
        WHEN b.partner_visible_snapshot_json IS NULL THEN NULL
        WHEN JSON_SEARCH(b.partner_visible_snapshot_json, 'one', 'self_media:sohu',
                 NULL, '$.channelQuotaSnapshot[*].channelCode') IS NULL
        THEN JSON_SET(b.partner_visible_snapshot_json, '$.channelQuotaSnapshot',
            JSON_ARRAY_APPEND(COALESCE(JSON_EXTRACT(b.partner_visible_snapshot_json,
                '$.channelQuotaSnapshot'), JSON_ARRAY()), '$', q.sohu_quota))
        ELSE JSON_SET(b.partner_visible_snapshot_json,
            REPLACE(JSON_UNQUOTE(JSON_SEARCH(b.partner_visible_snapshot_json,
                'one', 'self_media:sohu', NULL, '$.channelQuotaSnapshot[*].channelCode')),
                '.channelCode', ''), q.sohu_quota)
    END,
    b.internal_delivery_snapshot_json = CASE
        WHEN b.internal_delivery_snapshot_json IS NULL THEN NULL
        WHEN JSON_SEARCH(b.internal_delivery_snapshot_json, 'one', 'self_media:sohu',
                 NULL, '$.channelQuotaSnapshot[*].channelCode') IS NULL
        THEN JSON_SET(b.internal_delivery_snapshot_json, '$.channelQuotaSnapshot',
            JSON_ARRAY_APPEND(COALESCE(JSON_EXTRACT(b.internal_delivery_snapshot_json,
                '$.channelQuotaSnapshot'), JSON_ARRAY()), '$', q.sohu_quota))
        ELSE JSON_SET(b.internal_delivery_snapshot_json,
            REPLACE(JSON_UNQUOTE(JSON_SEARCH(b.internal_delivery_snapshot_json,
                'one', 'self_media:sohu', NULL, '$.channelQuotaSnapshot[*].channelCode')),
                '.channelCode', ''), q.sohu_quota)
    END;

-- Refresh the current limit without resetting usage or touching old periods.
-- Business dates use UTC+8, matching QuotaPeriodResolver.BUSINESS_ZONE.
UPDATE company_channel_quota_usage u
JOIN sohu_quota_backfill q ON q.company_id = u.company_id
SET u.quota_limit = CAST(JSON_UNQUOTE(JSON_EXTRACT(q.sohu_quota, '$.quotaLimit')) AS UNSIGNED)
WHERE u.channel_code = 'self_media:sohu'
  AND u.period_type = JSON_UNQUOTE(JSON_EXTRACT(q.sohu_quota, '$.periodType'))
  AND u.period_key = CASE u.period_type
      WHEN 'day' THEN DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y-%m-%d')
      WHEN 'week' THEN DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%x-W%v')
      WHEN 'month' THEN DATE_FORMAT(UTC_TIMESTAMP() + INTERVAL 8 HOUR, '%Y-%m')
      WHEN 'total' THEN 'TOTAL'
      ELSE NULL
  END;

DROP TEMPORARY TABLE sohu_quota_backfill;
