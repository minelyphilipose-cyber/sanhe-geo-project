-- ============================================================
-- V14: intensity level shrink to 3 tiers (L1/L2/L3)
-- ============================================================

-- normalize existing package_plan values to L1/L2/L3
UPDATE package_plan
SET monthly_report_depth = CASE
    WHEN monthly_report_depth IN ('L4', 'L5') THEN 'L3'
    WHEN monthly_report_depth IS NULL THEN 'L2'
    ELSE monthly_report_depth
END;
UPDATE package_plan
SET quarterly_report_depth = CASE
    WHEN quarterly_report_depth IN ('L4', 'L5') THEN 'L3'
    WHEN quarterly_report_depth IS NULL THEN 'L2'
    ELSE quarterly_report_depth
END;
UPDATE package_plan
SET consultant_intensity = CASE
    WHEN consultant_intensity IN ('L4', 'L5') THEN 'L3'
    WHEN consultant_intensity IS NULL THEN 'L2'
    ELSE consultant_intensity
END;
UPDATE package_plan
SET competitor_insight_depth = CASE
    WHEN competitor_insight_depth IN ('L4', 'L5') THEN 'L3'
    WHEN competitor_insight_depth IS NULL THEN 'L2'
    ELSE competitor_insight_depth
END;
UPDATE package_plan
SET media_distribution_intensity = CASE
    WHEN media_distribution_intensity IN ('L4', 'L5') THEN 'L3'
    WHEN media_distribution_intensity IS NULL THEN 'L2'
    ELSE media_distribution_intensity
END;
UPDATE package_plan
SET commitment_target_intensity = CASE
    WHEN commitment_target_intensity IN ('L4', 'L5') THEN 'L3'
    WHEN commitment_target_intensity IS NULL THEN 'L2'
    ELSE commitment_target_intensity
END;

-- normalize project snapshot values to L1/L2/L3
UPDATE project
SET plan_monthly_report_depth = CASE
    WHEN plan_monthly_report_depth IN ('L4', 'L5') THEN 'L3'
    WHEN plan_monthly_report_depth IS NULL THEN 'L2'
    ELSE plan_monthly_report_depth
END;
UPDATE project
SET plan_quarterly_report_depth = CASE
    WHEN plan_quarterly_report_depth IN ('L4', 'L5') THEN 'L3'
    WHEN plan_quarterly_report_depth IS NULL THEN 'L2'
    ELSE plan_quarterly_report_depth
END;
UPDATE project
SET plan_consultant_intensity = CASE
    WHEN plan_consultant_intensity IN ('L4', 'L5') THEN 'L3'
    WHEN plan_consultant_intensity IS NULL THEN 'L2'
    ELSE plan_consultant_intensity
END;
UPDATE project
SET plan_competitor_insight_depth = CASE
    WHEN plan_competitor_insight_depth IN ('L4', 'L5') THEN 'L3'
    WHEN plan_competitor_insight_depth IS NULL THEN 'L2'
    ELSE plan_competitor_insight_depth
END;
UPDATE project
SET plan_media_distribution_intensity = CASE
    WHEN plan_media_distribution_intensity IN ('L4', 'L5') THEN 'L3'
    WHEN plan_media_distribution_intensity IS NULL THEN 'L2'
    ELSE plan_media_distribution_intensity
END;
UPDATE project
SET plan_commitment_target_intensity = CASE
    WHEN plan_commitment_target_intensity IN ('L4', 'L5') THEN 'L3'
    WHEN plan_commitment_target_intensity IS NULL THEN 'L2'
    ELSE plan_commitment_target_intensity
END;

-- map three default package tiers to low / medium / high
UPDATE package_plan
SET monthly_report_depth = 'L1',
    quarterly_report_depth = 'L1',
    consultant_intensity = 'L1',
    competitor_insight_depth = 'L1',
    media_distribution_intensity = 'L1',
    commitment_target_intensity = 'L1'
WHERE package_type = 'trial_6980';

UPDATE package_plan
SET monthly_report_depth = 'L2',
    quarterly_report_depth = 'L2',
    consultant_intensity = 'L2',
    competitor_insight_depth = 'L2',
    media_distribution_intensity = 'L2',
    commitment_target_intensity = 'L2'
WHERE package_type = 'standard_12800';

UPDATE package_plan
SET monthly_report_depth = 'L3',
    quarterly_report_depth = 'L3',
    consultant_intensity = 'L3',
    competitor_insight_depth = 'L3',
    media_distribution_intensity = 'L3',
    commitment_target_intensity = 'L3'
WHERE package_type = 'growth_26800';

-- dictionary adjustment to 3 visible tiers
UPDATE sys_dict_item
SET dict_value = '低', enabled = 1, sort_order = 10
WHERE dict_type = 'intensity_level' AND dict_key = 'L1';

UPDATE sys_dict_item
SET dict_value = '中', enabled = 1, sort_order = 20
WHERE dict_type = 'intensity_level' AND dict_key = 'L2';

UPDATE sys_dict_item
SET dict_value = '高', enabled = 1, sort_order = 30
WHERE dict_type = 'intensity_level' AND dict_key = 'L3';

UPDATE sys_dict_item
SET enabled = 0
WHERE dict_type = 'intensity_level' AND dict_key IN ('L4', 'L5');
