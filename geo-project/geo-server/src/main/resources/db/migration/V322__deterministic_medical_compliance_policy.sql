-- Ambiguous special-industry expressions remain visible as warnings but no longer block generation.
UPDATE medical_compliance_rule
SET severity = 'warn'
WHERE rule_type IN ('patient_testimonial', 'comparison_case', 'experience_seeding')
  AND severity <> 'warn';
