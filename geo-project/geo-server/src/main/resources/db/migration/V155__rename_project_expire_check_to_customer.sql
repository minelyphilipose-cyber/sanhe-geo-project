-- ============================================================
-- V155: rename project expire check task type to customer expire check
-- ============================================================

UPDATE sys_dict_item
SET dict_key = 'CUSTOMER_EXPIRE_CHECK',
    dict_value = '客户套餐到期检查'
WHERE dict_type = 'dispatch_task_type'
  AND dict_key = 'PROJECT_EXPIRE_CHECK'
  AND NOT EXISTS (
      SELECT 1 FROM (
          SELECT id FROM sys_dict_item
          WHERE dict_type = 'dispatch_task_type'
            AND dict_key = 'CUSTOMER_EXPIRE_CHECK'
      ) t
  );

UPDATE sys_dict_item
SET dict_value = '客户套餐到期检查'
WHERE dict_type = 'dispatch_task_type'
  AND dict_key = 'CUSTOMER_EXPIRE_CHECK';

DELETE FROM sys_dict_item
WHERE dict_type = 'dispatch_task_type'
  AND dict_key = 'PROJECT_EXPIRE_CHECK'
  AND EXISTS (
      SELECT 1 FROM (
          SELECT id FROM sys_dict_item
          WHERE dict_type = 'dispatch_task_type'
            AND dict_key = 'CUSTOMER_EXPIRE_CHECK'
      ) t
  );
