-- ============================================================
-- V32: dispatch task dictionaries
-- ============================================================

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_type', 'BI_DAILY_POLL', '双日问题池跑批', 10
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_type' AND dict_key = 'BI_DAILY_POLL'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_type', 'BIWEEKLY_REPORT', '双周报', 20
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_type' AND dict_key = 'BIWEEKLY_REPORT'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_type', 'MONTHLY_REPORT', '月报', 30
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_type' AND dict_key = 'MONTHLY_REPORT'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_type', 'QUARTERLY_REPORT', '季报', 40
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_type' AND dict_key = 'QUARTERLY_REPORT'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_type', 'PROJECT_EXPIRE_CHECK', '项目失效检查', 50
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_type' AND dict_key = 'PROJECT_EXPIRE_CHECK'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_type', 'PRESALE_DIAGNOSIS', '售前诊断', 60
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_type' AND dict_key = 'PRESALE_DIAGNOSIS'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_status', 'pending', '待执行', 10
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_status' AND dict_key = 'pending'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_status', 'running', '执行中', 20
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_status' AND dict_key = 'running'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_status', 'retry_pending', '重试待执行', 30
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_status' AND dict_key = 'retry_pending'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_status', 'completed', '已完成', 40
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_status' AND dict_key = 'completed'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_status', 'failed', '失败', 50
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_status' AND dict_key = 'failed'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_status', 'dead_letter', '死信', 60
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_status' AND dict_key = 'dead_letter'
);
