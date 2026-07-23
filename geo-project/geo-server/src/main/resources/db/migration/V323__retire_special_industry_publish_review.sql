-- The V2 flow no longer uses internal legal review or a universal ad-review-number publish gate.
UPDATE medical_compliance_kernel
SET require_manual_publish_review = 0
WHERE require_manual_publish_review <> 0;

UPDATE special_industry_profile
SET readiness_policy_json = JSON_SET(
        readiness_policy_json,
        '$.requireAdReviewNoForOfficialSite',
        CAST('false' AS JSON)
    )
WHERE JSON_VALID(readiness_policy_json)
  AND JSON_EXTRACT(readiness_policy_json, '$.requireAdReviewNoForOfficialSite') = CAST('true' AS JSON);

UPDATE special_industry_profile
SET qualification_schema_json = JSON_SET(
        qualification_schema_json,
        '$[2].requiredForOfficialSite',
        CAST('false' AS JSON)
    )
WHERE regulatory_domain = 'medical'
  AND JSON_VALID(qualification_schema_json)
  AND JSON_LENGTH(qualification_schema_json) > 2;

-- Normalize legacy UI rule codes to the canonical V2 contract.
UPDATE medical_compliance_rule SET rule_type = 'safety_claim' WHERE rule_type = 'safety_absolute';
UPDATE medical_compliance_rule SET rule_type = 'promotion' WHERE rule_type = 'urgency_promotion';
UPDATE medical_compliance_rule SET rule_type = 'beauty_anxiety' WHERE rule_type = 'anxiety_inducement';
UPDATE medical_compliance_rule SET rule_type = 'comparison_case' WHERE rule_type = 'before_after';
UPDATE medical_compliance_rule SET rule_type = 'brand_exposure_exceeded' WHERE rule_type = 'brand_exposure';
UPDATE medical_compliance_rule SET rule_type = 'rational_decision_missing' WHERE rule_type = 'rational_hint_missing';

UPDATE medical_compliance_rule
SET severity = 'warn'
WHERE rule_type IN (
    'patient_testimonial',
    'comparison_case',
    'experience_seeding',
    'self_media_experience_seeding',
    'self_media_contact_reference',
    'brand_exposure_exceeded',
    'risk_disclosure_missing',
    'rational_decision_missing',
    'third_party_official_tone',
    'project_forbidden_phrase'
)
  AND severity <> 'warn';
