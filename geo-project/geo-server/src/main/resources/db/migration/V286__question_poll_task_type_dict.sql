INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'dispatch_task_type', 'QUESTION_POLL', '问题池跑批', 10
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'dispatch_task_type' AND dict_key = 'QUESTION_POLL'
);
