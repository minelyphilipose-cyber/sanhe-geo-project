CREATE TABLE IF NOT EXISTS special_industry_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  industry_code VARCHAR(32) NOT NULL COMMENT 'special industry code',
  industry_name VARCHAR(64) NOT NULL COMMENT 'display name',
  regulatory_domain VARCHAR(32) NOT NULL DEFAULT 'custom' COMMENT 'medical/finance/education/legal/custom',
  keywords VARCHAR(500) NULL COMMENT 'comma separated aliases used for detection',
  qualification_schema_json LONGTEXT NULL COMMENT 'dynamic qualification fields schema',
  readiness_policy_json LONGTEXT NULL COMMENT 'activation readiness policy',
  prompt_labels_json LONGTEXT NULL COMMENT 'industry-specific prompt/display labels',
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 100,
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_special_industry_profile_code (industry_code),
  KEY idx_special_industry_profile_enabled (enabled, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='special industry profile registry';

INSERT INTO special_industry_profile (
  industry_code,
  industry_name,
  regulatory_domain,
  keywords,
  qualification_schema_json,
  readiness_policy_json,
  prompt_labels_json,
  enabled,
  sort_order,
  remark
)
SELECT seed.industry_code,
       seed.industry_name,
       seed.regulatory_domain,
       seed.keywords,
       seed.qualification_schema_json,
       seed.readiness_policy_json,
       seed.prompt_labels_json,
       1,
       seed.sort_order,
       seed.remark
FROM (
  SELECT 'medical_beauty' AS industry_code,
         '医美' AS industry_name,
         'medical' AS regulatory_domain,
         '医美,医疗美容,medical_beauty' AS keywords,
         '[{"key":"medicalLicense","label":"医疗机构执业许可","required":true},{"key":"diagnosisScope","label":"诊疗科目范围","required":true},{"key":"medicalAdReviewNo","label":"医疗广告审查证明编号","requiredForOfficialSite":true}]' AS qualification_schema_json,
         '{"requireProjectQualification":true,"requireMedicalLicense":true,"requireDiagnosisScope":true,"requireAdReviewNoForOfficialSite":true}' AS readiness_policy_json,
         '{"industryLabel":"医疗行业","qualificationRefLabel":"项目资质引用","licenseLabel":"医疗机构执业许可","scopeLabel":"诊疗科目范围","reviewNoLabel":"医疗广告审查证明编号"}' AS prompt_labels_json,
         10 AS sort_order,
         '兼容历史医美医疗合规链路' AS remark
  UNION ALL
  SELECT 'oral',
         '口腔医疗',
         'medical',
         '口腔,牙科,口腔医疗,oral',
         '[{"key":"medicalLicense","label":"医疗机构执业许可","required":true},{"key":"diagnosisScope","label":"诊疗科目范围","required":true},{"key":"medicalAdReviewNo","label":"医疗广告审查证明编号","requiredForOfficialSite":true}]',
         '{"requireProjectQualification":true,"requireMedicalLicense":true,"requireDiagnosisScope":true,"requireAdReviewNoForOfficialSite":true}',
         '{"industryLabel":"口腔医疗行业","qualificationRefLabel":"项目资质引用","licenseLabel":"医疗机构执业许可","scopeLabel":"诊疗科目范围","reviewNoLabel":"医疗广告审查证明编号"}',
         20,
         '兼容历史口腔医疗合规链路'
) seed
WHERE NOT EXISTS (
  SELECT 1
  FROM special_industry_profile existing
  WHERE existing.industry_code = seed.industry_code COLLATE utf8mb4_unicode_ci
);

INSERT INTO special_industry_profile (
  industry_code,
  industry_name,
  regulatory_domain,
  keywords,
  qualification_schema_json,
  readiness_policy_json,
  prompt_labels_json,
  enabled,
  sort_order,
  remark
)
SELECT item.dict_key,
       item.dict_value,
       'custom',
       CONCAT_WS(',', item.dict_key, item.dict_value, item.remark),
       '[{"key":"brandQualificationDescription","label":"行业资质说明","required":true}]',
       '{"requireProjectQualification":true,"requireBrandQualificationDescription":true,"requireAdReviewNoForOfficialSite":false}',
       '{"industryLabel":"特殊行业","qualificationRefLabel":"项目资质引用","licenseLabel":"行业资质说明","scopeLabel":"业务范围","reviewNoLabel":"审查/备案编号"}',
       item.enabled,
       item.sort_order,
       item.remark
FROM sys_dict_item item
WHERE item.dict_type = 'compliance_industry'
  AND item.dict_key <> 'none'
  AND NOT EXISTS (
    SELECT 1
    FROM special_industry_profile existing
    WHERE existing.industry_code = item.dict_key COLLATE utf8mb4_unicode_ci
  );
