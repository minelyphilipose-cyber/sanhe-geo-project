-- ============================================================
-- V47: 16 platform seed data + config columns enhancement
-- ============================================================

-- ─── 1. 补充蓝图要求的字段（幂等） ───

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'provider_name');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN provider_name VARCHAR(128) NULL COMMENT ''provider display name'' AFTER platform_name', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'api_enabled');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN api_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''API engine enabled'' AFTER provider_name', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'app_capture_enabled');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN app_capture_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''App/Web capture engine enabled'' AFTER api_enabled', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'pc_enabled');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN pc_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''PC terminal supported'' AFTER app_capture_enabled', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'mobile_enabled');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN mobile_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''Mobile terminal supported'' AFTER pc_enabled', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'supports_grounding');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN supports_grounding TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''supports grounding/citation'' AFTER mobile_enabled', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'supports_snapshot');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN supports_snapshot TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''supports result snapshot'' AFTER supports_grounding', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'supports_link_extraction');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN supports_link_extraction TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''supports link extraction'' AFTER supports_snapshot', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'supports_contact_extraction');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN supports_contact_extraction TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''supports contact info extraction'' AFTER supports_link_extraction', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'fallback_strategy');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN fallback_strategy VARCHAR(32) NOT NULL DEFAULT ''skip'' COMMENT ''skip/use_backup/manual'' AFTER supports_contact_extraction', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'degraded_reason');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN degraded_reason VARCHAR(500) NULL COMMENT ''degradation reason'' AFTER degraded', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'current_health_status');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN current_health_status VARCHAR(32) NOT NULL DEFAULT ''normal'' COMMENT ''normal/slow/high_failure/degraded/manual_takeover/maintenance'' AFTER degraded_reason', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'last_failure_at');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN last_failure_at DATETIME NULL COMMENT ''last failure timestamp'' AFTER current_health_status', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'primary_key_ref');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN primary_key_ref VARCHAR(128) NULL COMMENT ''primary credential reference'' AFTER api_key', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'backup_key_ref');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN backup_key_ref VARCHAR(128) NULL COMMENT ''backup credential reference'' AFTER primary_key_ref', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'backup_provider_name');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN backup_provider_name VARCHAR(128) NULL COMMENT ''backup provider display name'' AFTER backup_key_ref', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'backup_api_url');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN backup_api_url VARCHAR(255) NULL COMMENT ''backup API base URL'' AFTER backup_provider_name', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'backup_model_id');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN backup_model_id VARCHAR(128) NULL COMMENT ''backup model id'' AFTER backup_api_url', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'rpm_limit');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN rpm_limit INT NULL COMMENT ''requests per minute limit'' AFTER priority_level', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_platform_config' AND COLUMN_NAME = 'tpm_limit');
SET @sql := IF(@col = 0, 'ALTER TABLE ai_platform_config ADD COLUMN tpm_limit INT NULL COMMENT ''tokens per minute limit'' AFTER rpm_limit', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ─── 2. 16 平台 Seed 数据（INSERT IGNORE 幂等） ───

INSERT IGNORE INTO ai_platform_config (platform_code, platform_name, provider_name, priority_level, api_key, api_url, model_id, model_name, enabled, fallback_strategy, current_health_status) VALUES
('doubao',      '豆包',       '字节跳动',   'P0', 'PLACEHOLDER', 'https://ark.cn-beijing.volces.com/api/v3',   'doubao-pro-32k',          '豆包 Pro 32K',        1, 'use_backup', 'normal'),
('deepseek',    'DeepSeek',   '深度求索',   'P0', 'PLACEHOLDER', 'https://api.deepseek.com/v1',                'deepseek-chat',           'DeepSeek Chat',       1, 'use_backup', 'normal'),
('qwen',        '通义千问',    '阿里云',     'P0', 'PLACEHOLDER', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'qwen-plus',       '通义千问 Plus',        1, 'use_backup', 'normal'),
('ernie',       '文心一言',    '百度',       'P0', 'PLACEHOLDER', 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat', 'ernie-4.0-8k', '文心一言 4.0',   1, 'use_backup', 'normal'),
('hunyuan',     '元宝/混元',   '腾讯',       'P0', 'PLACEHOLDER', 'https://hunyuan.tencentcloudapi.com',        'hunyuan-pro',             '混元 Pro',            1, 'use_backup', 'normal'),
('nano_ai',     '纳米 AI',    '纳米 AI',    'P1', 'PLACEHOLDER', 'https://api.nano-ai.com/v1',                 'nano-chat',               '纳米 AI Chat',        1, 'skip',       'normal'),
('kimi',        'Kimi',       '月之暗面',   'P0', 'PLACEHOLDER', 'https://api.moonshot.cn/v1',                 'moonshot-v1-128k',        'Kimi 128K',           1, 'use_backup', 'normal'),
('spark',       '讯飞星火',    '科大讯飞',   'P1', 'PLACEHOLDER', 'https://spark-api-open.xf-yun.com/v1',      'generalv3.5',             '星火 V3.5',           1, 'skip',       'normal'),
('zhipu',       '智谱清言',    '智谱 AI',    'P1', 'PLACEHOLDER', 'https://open.bigmodel.cn/api/paas/v4',      'glm-4-plus',              'GLM-4 Plus',          1, 'skip',       'normal'),
('360_brain',   '360 智脑',   '奇虎360',    'P1', 'PLACEHOLDER', 'https://api.360.cn/v1',                     '360gpt-pro',              '360 智脑 Pro',        1, 'skip',       'normal'),
('tiangong',    '天工 AI',    '昆仑万维',   'P2', 'PLACEHOLDER', 'https://sky-api.singularity-ai.com/v1',     'SkyChat-MegaVerse',       '天工万象',            1, 'skip',       'normal'),
('metaso',      '秘塔 AI',    '秘塔科技',   'P2', 'PLACEHOLDER', 'https://api.metaso.cn/v1',                  'metaso-chat',             '秘塔 AI Search',      1, 'skip',       'normal'),
('baichuan',    '百川 AI',    '百川智能',   'P2', 'PLACEHOLDER', 'https://api.baichuan-ai.com/v1',            'Baichuan4',               '百川4',               1, 'skip',       'normal'),
('minimax',     'MiniMax',    'MiniMax',    'P2', 'PLACEHOLDER', 'https://api.minimax.chat/v1/text/chatcompletion_v2', 'abab6.5s-chat', 'MiniMax 6.5s',        1, 'skip',       'normal'),
('stepfun',     '阶跃星辰',   '阶跃星辰',   'P2', 'PLACEHOLDER', 'https://api.stepfun.com/v1',                'step-1-200k',             '阶跃 Step-1 200K',    1, 'skip',       'normal'),
('mimo',        '小米 MiMo',  '小米',       'P2', 'PLACEHOLDER', 'https://api.mimo.xiaomi.com/v1',            'mimo-chat',               'MiMo Chat',           1, 'skip',       'normal');
