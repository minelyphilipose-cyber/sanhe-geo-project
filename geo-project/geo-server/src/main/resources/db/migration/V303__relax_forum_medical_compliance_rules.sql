-- Relax medical forum publishing guardrails after production observation:
-- 1) forum medical articles use the education tier; allow up to 5 brand mentions.
-- 2) remove the over-broad one-character medical-beauty rule "丑".

UPDATE medical_compliance_kernel
SET brand_exposure_limit = 5,
    updated_at = NOW()
WHERE industry_code IN ('medical_beauty', 'oral')
  AND channel_tier = 'education'
  AND enabled = 1;

DELETE FROM medical_compliance_rule
WHERE rule_type = 'beauty_anxiety'
  AND industry_code = 'medical_beauty'
  AND pattern = '丑';
