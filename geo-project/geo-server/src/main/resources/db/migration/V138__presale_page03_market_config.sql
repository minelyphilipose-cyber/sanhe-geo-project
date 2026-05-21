CREATE TABLE IF NOT EXISTS presale_page03_market_config (
  id                              BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  market_label                    VARCHAR(32)     NOT NULL DEFAULT 'AI 搜索流量总览',
  market_source                   VARCHAR(32)     NOT NULL DEFAULT '来源：行业公开数据综合估算',
  app_monthly_active_value        VARCHAR(12)     NOT NULL DEFAULT '8.3',
  app_monthly_active_unit         VARCHAR(8)      NOT NULL DEFAULT '亿',
  daily_active_users_value        VARCHAR(12)     NOT NULL DEFAULT '7.2',
  daily_active_users_unit         VARCHAR(8)      NOT NULL DEFAULT '亿',
  daily_question_total_value      VARCHAR(12)     NOT NULL DEFAULT '12',
  daily_question_total_unit       VARCHAR(8)      NOT NULL DEFAULT '亿次',
  doubao_monthly_usage_value      VARCHAR(12)     NOT NULL DEFAULT '28',
  doubao_monthly_usage_unit       VARCHAR(8)      NOT NULL DEFAULT '次',
  platform_1_name                 VARCHAR(12)     NOT NULL DEFAULT '豆包',
  platform_1_value                VARCHAR(12)     NOT NULL DEFAULT '5.8亿/月活',
  platform_2_name                 VARCHAR(12)     NOT NULL DEFAULT '千问',
  platform_2_value                VARCHAR(12)     NOT NULL DEFAULT '4.2亿/月活',
  platform_3_name                 VARCHAR(12)     NOT NULL DEFAULT 'DeepSeek',
  platform_3_value                VARCHAR(12)     NOT NULL DEFAULT '3.1亿/月活',
  platform_suffix                 VARCHAR(18)     NOT NULL DEFAULT '元宝 / Kimi 等',
  page03_data_source              VARCHAR(30)     NOT NULL DEFAULT '公开口径综合测算',
  footnote                        VARCHAR(150)    NOT NULL DEFAULT '注：以上数据基于行业公开数据与主流AI平台问答量综合估算，存在±20%合理浮动区间，仅作量级参考，不构成精确市场断言。',
  question_count                  INT             NOT NULL DEFAULT 3,
  created_at                      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='presale Page03 AI search market configurable data';

INSERT INTO presale_page03_market_config (id)
SELECT 1
WHERE NOT EXISTS (SELECT 1 FROM presale_page03_market_config WHERE id = 1);
