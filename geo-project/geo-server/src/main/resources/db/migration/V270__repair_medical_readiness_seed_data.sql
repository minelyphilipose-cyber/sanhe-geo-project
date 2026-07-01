-- Repair medical readiness seed data for databases that already ran V231 before
-- the authority media tier and customer category coverage were finalized.

UPDATE medical_channel_style_module
SET channel_tier = 'education',
    style_prompt = '权威媒体档：公共信息价值优先，事实与观点分开，避免营销化表达和单一机构导向；必须保留风险、禁忌、适应证或个体差异提示。',
    high_risk = 0,
    enabled = 1
WHERE channel_group_code = 'authority_media'
  AND channel_sub_code IS NULL;

INSERT INTO medical_channel_style_module (
  channel_group_code, channel_sub_code, channel_tier, style_prompt, high_risk, enabled
)
SELECT 'authority_media', NULL, 'education',
       '权威媒体档：公共信息价值优先，事实与观点分开，避免营销化表达和单一机构导向；必须保留风险、禁忌、适应证或个体差异提示。',
       0, 1
WHERE NOT EXISTS (
  SELECT 1
  FROM medical_channel_style_module
  WHERE channel_group_code = 'authority_media'
    AND channel_sub_code IS NULL
);

INSERT INTO medical_topic_angle (
  industry_code, industry_name, category_code, category_name, topic_angle,
  recommended_focus, enabled, sort_order
)
SELECT seed.medical_industry_code,
       CASE seed.medical_industry_code
         WHEN 'medical_beauty' THEN '医美'
         WHEN 'oral' THEN '口腔'
         ELSE '医疗'
       END,
       seed.medical_category_code,
       seed.category_name,
       CONCAT(seed.category_name, '适应条件、风险边界和注意事项怎么理解'),
       'risk',
       1,
       900
FROM (
  SELECT bo.medical_industry_code,
         bo.medical_category_code,
         COALESCE(
           MAX(NULLIF(bo.medical_category_name, '')),
           MAX(NULLIF(bo.offering_name, '')),
           bo.medical_category_code
         ) AS category_name
  FROM brand_offering bo
  WHERE bo.status = 'active'
    AND bo.medical_project_enabled = 1
    AND bo.medical_industry_code IN ('medical_beauty', 'oral')
    AND bo.medical_category_code IS NOT NULL
    AND bo.medical_category_code <> ''
    AND bo.deleted_at IS NULL
    AND NOT EXISTS (
      SELECT 1
      FROM medical_topic_angle mta
      WHERE mta.industry_code = bo.medical_industry_code
        AND mta.category_code = bo.medical_category_code
        AND mta.enabled = 1
        AND mta.deleted_at IS NULL
    )
  GROUP BY bo.medical_industry_code, bo.medical_category_code
) seed;
