-- =========================================================================
-- V74: add mimo/hailuo/zhinao into presale whitelist
-- 说明:
--   1) 幂等 upsert: 已存在则仅更新 in_whitelist 与 remark
--   2) 若平台行不存在则创建默认售前配置(限流/重试/超时采用系统默认值)
-- =========================================================================

INSERT INTO presale_platform_config (
    platform_code,
    in_whitelist,
    rate_limit_qps,
    max_retry,
    timeout_ms,
    remark
)
VALUES
    ('mimo',   1, 3, 2, 60000, 'PR-3.D3 add whitelist'),
    ('hailuo', 1, 3, 2, 60000, 'PR-3.D3 add whitelist'),
    ('zhinao', 1, 3, 2, 60000, 'PR-3.D3 add whitelist')
ON DUPLICATE KEY UPDATE
    in_whitelist = VALUES(in_whitelist),
    remark = VALUES(remark),
    updated_at = CURRENT_TIMESTAMP;
