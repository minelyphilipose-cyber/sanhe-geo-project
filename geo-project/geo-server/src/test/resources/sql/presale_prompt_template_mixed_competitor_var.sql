INSERT INTO presale_prompt_template
(
    prompt_code,
    industry,
    industry_role,
    category,
    business_value,
    prompt_content,
    has_competitor_var,
    enabled,
    sort_order,
    remark
)
VALUES
('C5_SQL_PTVAR_0', 'c5_industry', 'c5_role', 'C5_TEST_INTENT', '中', 'c5 prompt without competitor', 0, 1, 1, 'c5 integration test'),
('C5_SQL_PTVAR_1', 'c5_industry', 'c5_role', 'C5_TEST_INTENT', '中', 'c5 prompt with {competitor}', 1, 1, 2, 'c5 integration test'),
('C5_SQL_DISABLED', 'c5_industry', 'c5_role', 'C5_T_DISABLED', '低', 'c5 disabled prompt', 0, 0, 3, 'c5 integration test');
