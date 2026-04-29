-- ============================================================
-- V93: distribution_tasks 多渠道目标扩展（C1 官网 / C2 自媒体 + C3/C4 占位）
-- 要求 MySQL 8.0.13+（函数索引）+ 8.0.16+（CHECK 约束强制）
-- 生产 MySQL 版本：8.0.45（已确认）
-- ============================================================

-- 0) site_id 需可为 NULL：非 site 类 target 在 CHECK 中要求 site_id IS NULL（V41 原为 NOT NULL）
ALTER TABLE distribution_tasks
  MODIFY COLUMN site_id BIGINT UNSIGNED NULL
    COMMENT 'C0: publish_sites; NULL when target_kind != site';

-- 1) 目标判别列 + 5 个目标 ID 列
ALTER TABLE distribution_tasks
  ADD COLUMN target_kind            VARCHAR(32)     NOT NULL DEFAULT 'site'
             COMMENT 'site/mp_account/brand_official_site/industry_site/authority_media' AFTER site_id,
  ADD COLUMN mp_account_id          BIGINT UNSIGNED NULL COMMENT 'C2 自媒体账号' AFTER target_kind,
  ADD COLUMN brand_official_site_id BIGINT UNSIGNED NULL COMMENT 'C1 品牌官网' AFTER mp_account_id,
  ADD COLUMN industry_site_id       BIGINT UNSIGNED NULL COMMENT 'C3 占位（暂不实现）' AFTER brand_official_site_id,
  ADD COLUMN authority_media_id     BIGINT UNSIGNED NULL COMMENT 'C4 占位（暂不实现）' AFTER industry_site_id;

-- 2) 任务编排控制列（v1 §4.2 已规划，统一在 V93 一次到位，避免 Phase 1+ 再 ALTER）
ALTER TABLE distribution_tasks
  ADD COLUMN failure_kind        VARCHAR(32)  NULL
             COMMENT 'retryable/rate_limited/auth_expired/content_violation/platform_down/unknown' AFTER error_message,
  ADD COLUMN next_retry_at       DATETIME     NULL COMMENT '下次重试时间' AFTER failure_kind,
  ADD COLUMN locked_until        DATETIME     NULL COMMENT 'worker 抢占锁过期' AFTER next_retry_at,
  ADD COLUMN platform_article_id VARCHAR(128) NULL COMMENT '平台返回的文章ID' AFTER published_url;

-- 3) 替换唯一键
--    旧键: uk_distribution_article_site_attempt (article_id, site_id, attempt_no)
--    新键: 用 COALESCE 把活跃的目标 ID 折叠成单值参与唯一性比较
--    依赖下方 CHECK：每行有且仅有一个 target_id 列非 NULL
ALTER TABLE distribution_tasks DROP INDEX uk_distribution_article_site_attempt;

ALTER TABLE distribution_tasks
  ADD UNIQUE KEY uk_distribution_article_target_attempt (
    article_id,
    target_kind,
    (COALESCE(`site_id`, `mp_account_id`, `brand_official_site_id`, `industry_site_id`, `authority_media_id`)),
    attempt_no
  );

-- 4) CHECK 约束：target_kind 与对应 ID 列必须一致
ALTER TABLE distribution_tasks
  ADD CONSTRAINT chk_distribution_target_consistency CHECK (
       (target_kind = 'site'
        AND site_id IS NOT NULL AND mp_account_id IS NULL AND brand_official_site_id IS NULL
        AND industry_site_id IS NULL AND authority_media_id IS NULL)
    OR (target_kind = 'mp_account'
        AND site_id IS NULL AND mp_account_id IS NOT NULL AND brand_official_site_id IS NULL
        AND industry_site_id IS NULL AND authority_media_id IS NULL)
    OR (target_kind = 'brand_official_site'
        AND site_id IS NULL AND mp_account_id IS NULL AND brand_official_site_id IS NOT NULL
        AND industry_site_id IS NULL AND authority_media_id IS NULL)
    OR (target_kind = 'industry_site'
        AND site_id IS NULL AND mp_account_id IS NULL AND brand_official_site_id IS NULL
        AND industry_site_id IS NOT NULL AND authority_media_id IS NULL)
    OR (target_kind = 'authority_media'
        AND site_id IS NULL AND mp_account_id IS NULL AND brand_official_site_id IS NULL
        AND industry_site_id IS NULL AND authority_media_id IS NOT NULL)
  );

-- 5) 为 Phase 1+ 查询预留索引
ALTER TABLE distribution_tasks
  ADD KEY idx_distribution_mp_account_status (mp_account_id, status),
  ADD KEY idx_distribution_brand_site_status (brand_official_site_id, status),
  ADD KEY idx_distribution_status_next_retry (status, next_retry_at),
  ADD KEY idx_distribution_locked_until (locked_until);

-- 6) FK 约束按阶段添加（V93 不动）
--    - V94（Phase 1）创建 brand_official_site 表后再加 fk_distribution_brand_official_site
--    - V95（Phase 2A）创建 mp_account 表后再加 fk_distribution_mp_account
--    这样 V93 可独立 migrate，不依赖未来表

-- ============================================================
-- 回滚指引（Flyway 不支持运行时回滚，仅供运维参考）
-- 1. DROP CONSTRAINT chk_distribution_target_consistency
-- 2. DROP INDEX uk_distribution_article_target_attempt + 4 个 idx_*
-- 3. ADD UNIQUE KEY uk_distribution_article_site_attempt (article_id, site_id, attempt_no)
-- 4. DROP COLUMN target_kind, mp_account_id, brand_official_site_id, industry_site_id, authority_media_id
-- 5. DROP COLUMN failure_kind, next_retry_at, locked_until, platform_article_id
-- 6. MODIFY site_id BIGINT UNSIGNED NOT NULL（并确保无 NULL 行后再加回 NOT NULL）
-- ============================================================
