-- ============================================================
-- V143: default Agent official site publish target
-- ============================================================

INSERT INTO publish_sites (
  site_name,
  site_code,
  domain,
  industry_tags,
  tier,
  status,
  integration_method,
  is_framework,
  current_health_status,
  remark
) SELECT
  'Agent 官网',
  'agent_official_site',
  'agent-site.local',
  JSON_ARRAY('general'),
  'S0',
  'active',
  'brand_geo_site',
  0,
  'normal',
  'Agent 官网自动发布目标，站点唯一标识由发布平台管理维护'
WHERE NOT EXISTS (
  SELECT 1 FROM publish_sites
  WHERE site_code = 'agent_official_site'
);
