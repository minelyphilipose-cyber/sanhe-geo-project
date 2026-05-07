-- V101: repair inactive package bindings left with active_flag=1 by old null-update behavior.
UPDATE company_package_binding
SET active_flag = NULL
WHERE status = 'inactive'
  AND active_flag IS NOT NULL;
