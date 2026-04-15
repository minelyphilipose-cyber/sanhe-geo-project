-- 机构名称后缀字典组
-- dict_type: org_suffix

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'org_suffix', 'company', '公司', 10, 1, '机构名称后缀'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'org_suffix' AND dict_key = 'company'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'org_suffix', 'organization', '机构', 20, 1, '机构名称后缀'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'org_suffix' AND dict_key = 'organization'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'org_suffix', 'platform', '平台', 30, 1, '机构名称后缀'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'org_suffix' AND dict_key = 'platform'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'org_suffix', 'service_provider', '服务商', 40, 1, '机构名称后缀'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'org_suffix' AND dict_key = 'service_provider'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'org_suffix', 'service', '服务', 50, 1, '机构名称后缀'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item WHERE dict_type = 'org_suffix' AND dict_key = 'service'
);
