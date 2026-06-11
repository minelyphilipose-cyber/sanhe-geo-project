-- ============================================================
-- V236: harden common medical compliance blocking rules.
-- Applies to all medical industries/channels unless scoped later in admin config.
-- ============================================================

DELETE FROM medical_compliance_rule
WHERE (rule_type, IFNULL(industry_code, ''), IFNULL(channel_tier, ''), IFNULL(channel_group_code, ''), IFNULL(channel_sub_code, ''), pattern) IN (
  ('efficacy_claim', '', '', '', '', '立竿见影'),
  ('efficacy_claim', '', '', '', '', '一次见效'),
  ('efficacy_claim', '', '', '', '', '包好'),
  ('efficacy_claim', '', '', '', '', '痊愈'),
  ('efficacy_claim', '', '', '', '', '媲美真牙'),
  ('efficacy_claim', '', '', '', '', '彻底解决'),
  ('patient_testimonial', '', '', '', '', '现身说法'),
  ('patient_testimonial', '', '', '', '', '亲测'),
  ('patient_testimonial', '', '', '', '', '真实案例')
);

INSERT INTO medical_compliance_rule (
  rule_type, industry_code, channel_tier, channel_group_code, channel_sub_code,
  pattern, match_mode, severity, enabled, remark
) VALUES
('efficacy_claim', NULL, NULL, NULL, NULL, '立竿见影', 'contains', 'block', 1, '医疗内容不得使用即时见效承诺'),
('efficacy_claim', NULL, NULL, NULL, NULL, '一次见效', 'contains', 'block', 1, '医疗内容不得承诺单次见效'),
('efficacy_claim', NULL, NULL, NULL, NULL, '包好', 'contains', 'block', 1, '医疗内容不得承诺治疗结果'),
('efficacy_claim', NULL, NULL, NULL, NULL, '痊愈', 'contains', 'block', 1, '医疗内容不得使用治愈性承诺'),
('efficacy_claim', NULL, NULL, NULL, NULL, '媲美真牙', 'contains', 'block', 1, '口腔医疗不得承诺仿真效果'),
('efficacy_claim', NULL, NULL, NULL, NULL, '彻底解决', 'contains', 'block', 1, '医疗内容不得承诺彻底解决'),
('patient_testimonial', NULL, NULL, NULL, NULL, '现身说法', 'contains', 'block', 1, '医疗内容不得使用患者证明式表达'),
('patient_testimonial', NULL, NULL, NULL, NULL, '亲测', 'contains', 'block', 1, '医疗内容不得使用体验证明式表达'),
('patient_testimonial', NULL, NULL, NULL, NULL, '真实案例', 'contains', 'block', 1, '医疗内容不得使用患者案例证明式表达');
