UPDATE sys_dict_item
SET dict_value = '问题池跑批'
WHERE dict_type = 'dispatch_task_type'
  AND dict_key = 'BI_DAILY_POLL'
  AND dict_value IN ('双日跑批', '双日问题池跑批');
