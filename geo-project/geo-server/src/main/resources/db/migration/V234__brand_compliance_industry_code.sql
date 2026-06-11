ALTER TABLE brand
  ADD COLUMN compliance_industry_code VARCHAR(32) NULL COMMENT 'special compliance industry code from sys_dict_item: compliance_industry' AFTER industry;

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'compliance_industry', 'medical_beauty', '医美', 10, 1, '医疗美容行业，启用医疗合规生成链路'
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item WHERE dict_type = 'compliance_industry' AND dict_key = 'medical_beauty'
);

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark)
SELECT 'compliance_industry', 'oral', '口腔医疗', 20, 1, '口腔/牙科行业，启用医疗合规生成链路'
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item WHERE dict_type = 'compliance_industry' AND dict_key = 'oral'
);
