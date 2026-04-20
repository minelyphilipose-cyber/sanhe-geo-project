START TRANSACTION;

DELETE FROM report_access_logs
WHERE report_id IN (
    SELECT id FROM reports
    WHERE report_type IN ('presale', 'presale_diagnosis')
);

DELETE FROM reports
WHERE report_type IN ('presale', 'presale_diagnosis');

DELETE FROM sys_dict_item
WHERE (dict_type = 'report_type' AND dict_key = 'presale_diagnosis')
   OR (dict_type = 'dispatch_task_type' AND dict_key = 'PRESALE_DIAGNOSIS');

DELETE FROM sys_role_permission
WHERE permission_id IN (
    SELECT id FROM sys_permission WHERE perm_key = 'dispatch.presale.enqueue'
);

DELETE FROM sys_permission
WHERE perm_key = 'dispatch.presale.enqueue';

COMMIT;

DROP TABLE IF EXISTS presale_report_snapshots;
DROP TABLE IF EXISTS presale_diagnosis_results;
DROP TABLE IF EXISTS presale_diagnosis_batches;
DROP TABLE IF EXISTS presale_question_items;
DROP TABLE IF EXISTS presale_question_sets;
