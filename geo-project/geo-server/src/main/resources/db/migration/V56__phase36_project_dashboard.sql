CREATE TABLE IF NOT EXISTS project_dashboard_share (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    share_code VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    disabled_at DATETIME NULL,
    UNIQUE KEY uk_project_dashboard_share_code (share_code),
    KEY idx_project_dashboard_share_project_status (project_id, status),
    CONSTRAINT fk_project_dashboard_share_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='project dashboard share links';

CREATE TABLE IF NOT EXISTS project_dashboard_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    snapshot_type VARCHAR(32) NOT NULL,
    snapshot_key VARCHAR(100) NULL,
    snapshot_value JSON NOT NULL,
    snapshot_date DATE NULL,
    refreshed_at DATETIME NOT NULL,
    KEY idx_project_dashboard_snapshot_type (project_id, snapshot_type),
    KEY idx_project_dashboard_snapshot_date (project_id, snapshot_type, snapshot_date),
    CONSTRAINT fk_project_dashboard_snapshot_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='project dashboard snapshots';

CREATE INDEX idx_poll_result_project_hit_date ON poll_results (project_id, is_hit, batch_date DESC);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dashboard_platform_jump_url', 'doubao', 'https://www.doubao.com/', 10, 1, 'project dashboard jump url'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dashboard_platform_jump_url' AND dict_key = 'doubao'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dashboard_platform_jump_url', 'deepseek', 'https://chat.deepseek.com/', 20, 1, 'project dashboard jump url'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dashboard_platform_jump_url' AND dict_key = 'deepseek'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dashboard_platform_jump_url', 'qwen', 'https://tongyi.aliyun.com/', 30, 1, 'project dashboard jump url'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dashboard_platform_jump_url' AND dict_key = 'qwen'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dashboard_platform_jump_url', 'ernie', 'https://yiyan.baidu.com/', 40, 1, 'project dashboard jump url'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dashboard_platform_jump_url' AND dict_key = 'ernie'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dashboard_platform_jump_url', 'hunyuan', 'https://yuanbao.tencent.com/', 50, 1, 'project dashboard jump url'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dashboard_platform_jump_url' AND dict_key = 'hunyuan'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dashboard_platform_jump_url', 'nano_ai', 'https://bot.n.cn/', 60, 1, 'project dashboard jump url'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dashboard_platform_jump_url' AND dict_key = 'nano_ai'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dashboard_platform_jump_url', 'kimi', 'https://kimi.moonshot.cn/', 70, 1, 'project dashboard jump url'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dashboard_platform_jump_url' AND dict_key = 'kimi'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dashboard_platform_jump_url', 'spark', 'https://xinghuo.xfyun.cn/', 80, 1, 'project dashboard jump url'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dashboard_platform_jump_url' AND dict_key = 'spark'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dashboard_platform_jump_url', 'zhipu', 'https://chatglm.cn/', 90, 1, 'project dashboard jump url'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dashboard_platform_jump_url' AND dict_key = 'zhipu'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dashboard_platform_jump_url', '360_brain', 'https://chat.360.com/', 100, 1, 'project dashboard jump url'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dashboard_platform_jump_url' AND dict_key = '360_brain'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dashboard_platform_jump_url', 'tiangong', 'https://www.tiangong.cn/', 110, 1, 'project dashboard jump url'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dashboard_platform_jump_url' AND dict_key = 'tiangong'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'dashboard_platform_jump_url', 'metaso', 'https://metaso.cn/', 120, 1, 'project dashboard jump url'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dashboard_platform_jump_url' AND dict_key = 'metaso'
);
