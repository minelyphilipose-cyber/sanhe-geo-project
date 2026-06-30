-- 自媒体账号主体与特殊行业模板路由配置。
-- account_identity:
-- - personal：个人号
-- - enterprise：企业号
--
-- 历史数据按当前特殊行业运营策略初始化：
-- - baijiahao 默认为 enterprise
-- - 其他自媒体平台默认为 personal

SET @add_account_identity_sql := IF(
  (SELECT COUNT(1)
   FROM information_schema.COLUMNS
   WHERE table_schema = DATABASE()
     AND table_name = 'self_media_account'
     AND column_name = 'account_identity') = 0,
  'ALTER TABLE self_media_account ADD COLUMN account_identity VARCHAR(32) NOT NULL DEFAULT ''personal'' COMMENT ''账号主体：personal/enterprise'' AFTER account_name',
  'SELECT 1'
);
PREPARE add_account_identity_stmt FROM @add_account_identity_sql;
EXECUTE add_account_identity_stmt;
DEALLOCATE PREPARE add_account_identity_stmt;

UPDATE self_media_account
SET account_identity = CASE
  WHEN platform = 'baijiahao' THEN 'enterprise'
  WHEN account_identity IS NULL OR account_identity = '' THEN 'personal'
  ELSE account_identity
END;

SET @add_account_identity_index_sql := IF(
  (SELECT COUNT(1)
   FROM information_schema.STATISTICS
   WHERE table_schema = DATABASE()
     AND table_name = 'self_media_account'
     AND index_name = 'idx_self_media_account_identity') = 0,
  'ALTER TABLE self_media_account ADD KEY idx_self_media_account_identity (platform, account_identity, status)',
  'SELECT 1'
);
PREPARE add_account_identity_index_stmt FROM @add_account_identity_index_sql;
EXECUTE add_account_identity_index_stmt;
DEALLOCATE PREPARE add_account_identity_index_stmt;

CREATE TABLE IF NOT EXISTS special_industry_template_route (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  industry_code VARCHAR(64) NOT NULL DEFAULT '*' COMMENT '特殊行业编码，* 表示通用',
  channel_group_code VARCHAR(64) NOT NULL COMMENT '渠道组',
  channel_sub_code VARCHAR(64) NULL COMMENT '渠道子平台，NULL 表示组内通用',
  account_identity VARCHAR(32) NULL COMMENT '账号主体：personal/enterprise，NULL 表示不限',
  template_name VARCHAR(128) NOT NULL COMMENT '文章模板名称',
  priority INT NOT NULL DEFAULT 0 COMMENT '同等匹配下优先级',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_special_template_route (industry_code, channel_group_code, channel_sub_code, account_identity),
  KEY idx_special_template_route_lookup (channel_group_code, channel_sub_code, account_identity, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='特殊行业文章模板路由';

INSERT INTO special_industry_template_route
  (industry_code, channel_group_code, channel_sub_code, account_identity, template_name, priority, enabled, created_at, updated_at)
VALUES
  ('*', 'forum', NULL, NULL, '特殊行业论坛理性讨论模板', 100, 1, NOW(), NOW()),
  ('*', 'industry_site', NULL, NULL, '特殊行业行业资讯站科普模板', 100, 1, NOW(), NOW()),
  ('*', 'agent_site', NULL, NULL, '特殊行业 Agent 官网合规科普模板', 100, 1, NOW(), NOW()),
  ('*', 'self_media', 'wechat', 'personal', '特殊行业公众号个人号克制科普模板', 100, 1, NOW(), NOW()),
  ('*', 'self_media', 'douyin', 'personal', '特殊行业抖音图文个人号克制科普模板', 100, 1, NOW(), NOW()),
  ('*', 'self_media', 'zhihu', 'personal', '特殊行业知乎个人号深度问答模板', 100, 1, NOW(), NOW()),
  ('*', 'self_media', 'xiaohongshu', 'personal', '特殊行业小红书个人号清单笔记模板', 100, 1, NOW(), NOW()),
  ('*', 'self_media', 'toutiao', 'personal', '特殊行业今日头条个人号搜索科普模板', 100, 1, NOW(), NOW()),
  ('*', 'self_media', 'netease', 'personal', '特殊行业网易个人号门户科普模板', 100, 1, NOW(), NOW()),
  ('*', 'self_media', 'sohu', 'personal', '特殊行业搜狐个人号搜索科普模板', 100, 1, NOW(), NOW()),
  ('*', 'self_media', 'baijiahao', 'enterprise', '特殊行业百家号企业号搜索科普模板', 100, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  template_name = VALUES(template_name),
  priority = VALUES(priority),
  enabled = VALUES(enabled),
  updated_at = NOW();
