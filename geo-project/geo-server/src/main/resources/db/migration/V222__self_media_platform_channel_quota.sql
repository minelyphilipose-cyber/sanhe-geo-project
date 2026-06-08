ALTER TABLE package_channel_quota_config
    MODIFY channel_code VARCHAR(64) NOT NULL;

ALTER TABLE company_channel_quota_usage
    MODIFY channel_code VARCHAR(64) NOT NULL;

ALTER TABLE company_channel_quota_ledger
    MODIFY channel_code VARCHAR(64) NOT NULL;

ALTER TABLE project_channel_allocation
    MODIFY channel_code VARCHAR(64) NOT NULL;

ALTER TABLE project_channel_allocation_audit
    MODIFY channel_code VARCHAR(64) NOT NULL;

INSERT INTO package_channel_quota_config (
    package_plan_id,
    channel_code,
    period_type,
    quota_limit,
    enabled
)
SELECT old.package_plan_id,
       platforms.channel_code,
       old.period_type,
       old.quota_limit,
       old.enabled
FROM package_channel_quota_config old
JOIN (
    SELECT 'self_media:wechat' AS channel_code
    UNION ALL SELECT 'self_media:douyin'
    UNION ALL SELECT 'self_media:baijiahao'
    UNION ALL SELECT 'self_media:zhihu'
    UNION ALL SELECT 'self_media:xiaohongshu'
    UNION ALL SELECT 'self_media:toutiao'
    UNION ALL SELECT 'self_media:netease'
    UNION ALL SELECT 'self_media:sohu'
) platforms
WHERE old.channel_code = 'self_media'
  AND NOT EXISTS (
      SELECT 1
      FROM package_channel_quota_config existed
      WHERE existed.package_plan_id = old.package_plan_id
        AND existed.channel_code = platforms.channel_code
  );

DELETE FROM package_channel_quota_config
WHERE channel_code = 'self_media';

DELETE FROM project_channel_allocation
WHERE channel_code = 'self_media';

DELETE FROM project_channel_allocation_audit
WHERE channel_code = 'self_media';

UPDATE company_package_binding binding
JOIN (
    SELECT b.id,
           JSON_ARRAYAGG(JSON_OBJECT(
               'channelCode', cfg.channel_code,
               'periodType', cfg.period_type,
               'quotaLimit', cfg.quota_limit,
               'enabled', IF(cfg.enabled = 1, TRUE, FALSE)
           )) AS channel_quota_snapshot
    FROM company_package_binding b
    JOIN package_channel_quota_config cfg ON cfg.package_plan_id = b.package_plan_id
    WHERE b.status = 'active'
      AND b.active_flag = 1
      AND cfg.enabled = 1
    GROUP BY b.id
) snapshot ON snapshot.id = binding.id
SET binding.channel_quota_snapshot = snapshot.channel_quota_snapshot
WHERE binding.status = 'active'
  AND binding.active_flag = 1;
