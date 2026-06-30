-- Expand special industry topic-angle category coverage so product setup has
-- enough selectable categories while still mapping every option to compliant topics.

INSERT INTO medical_topic_angle (
  industry_code, industry_name, category_code, category_name, topic_angle,
  recommended_focus, enabled, sort_order
)
SELECT seed.industry_code,
       seed.industry_name,
       seed.category_code,
       seed.category_name,
       seed.topic_angle,
       seed.recommended_focus,
       1,
       seed.sort_order
FROM (
  SELECT 'medical_beauty' AS industry_code, '医美' AS industry_name, 'skin_repair' AS category_code, '皮肤修复类项目' AS category_name,
         '皮肤修复类项目前为什么需要先判断屏障状态和适应条件' AS topic_angle, 'risk' AS recommended_focus, 70 AS sort_order
  UNION ALL
  SELECT 'medical_beauty', '医美', 'skin_repair', '皮肤修复类项目',
         '皮肤修复类项目常见护理边界和禁忌事项怎么理解', 'principle', 80
  UNION ALL
  SELECT 'medical_beauty', '医美', 'hair_transplant', '毛发移植类项目',
         '毛发移植咨询前为什么需要评估脱发类型和供区条件', 'risk', 90
  UNION ALL
  SELECT 'medical_beauty', '医美', 'hair_transplant', '毛发移植类项目',
         '毛发移植项目如何理解恢复周期和个体差异', 'misconception', 100
  UNION ALL
  SELECT 'medical_beauty', '医美', 'body_contouring', '形体轮廓类项目',
         '形体轮廓类项目前为什么需要关注适应证和基础健康条件', 'risk', 110
  UNION ALL
  SELECT 'medical_beauty', '医美', 'body_contouring', '形体轮廓类项目',
         '形体轮廓类项目咨询中哪些效果承诺需要谨慎识别', 'misconception', 120
  UNION ALL
  SELECT 'oral', '口腔', 'whitening', '牙齿美白',
         '牙齿美白前为什么需要先确认牙体和牙周基础情况', 'risk', 70
  UNION ALL
  SELECT 'oral', '口腔', 'whitening', '牙齿美白',
         '牙齿美白适应条件、敏感风险和维护事项怎么理解', 'principle', 80
  UNION ALL
  SELECT 'oral', '口腔', 'pediatric_dentistry', '儿童口腔',
         '儿童口腔项目为什么需要结合年龄、配合度和发育阶段判断', 'rational_decision', 90
  UNION ALL
  SELECT 'oral', '口腔', 'pediatric_dentistry', '儿童口腔',
         '儿童口腔常见干预项目前家长需要了解哪些风险边界', 'risk', 100
  UNION ALL
  SELECT 'oral', '口腔', 'general_treatment', '基础治疗',
         '补牙、根管等基础治疗为什么需要先明确牙体损伤范围', 'principle', 110
  UNION ALL
  SELECT 'oral', '口腔', 'general_treatment', '基础治疗',
         '基础口腔治疗前后需要关注哪些复诊和维护事项', 'risk', 120
) seed
WHERE NOT EXISTS (
  SELECT 1
  FROM medical_topic_angle existing
  WHERE existing.industry_code = seed.industry_code
    AND existing.category_code = seed.category_code
    AND existing.topic_angle = seed.topic_angle
    AND existing.deleted_at IS NULL
);
