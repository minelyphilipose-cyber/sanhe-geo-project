-- Calibrate the pre-release simulated benchmark range.
-- These are deliberately marked LOW confidence: they are operational placeholders,
-- not measured industry statistics. The newer effective date supersedes V350 rows
-- without mutating historical report snapshots.

INSERT IGNORE INTO presale_benchmark (
    industry, industry_role,
    avg_overall, avg_mention, avg_ranking, avg_sentiment, avg_coverage,
    top1_overall, top1_mention, top1_ranking, top1_sentiment, top1_coverage,
    top10_score, confidence_level, source, sample_size, enabled, effective_from, remark
) VALUES
('automotive',          '_ALL_',53,55,50,54,49,82,83,81,80,79,71,'LOW','MANUAL',500,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('new_energy_vehicle',  '_ALL_',52,54,50,53,49,81,83,81,80,79,70,'LOW','MANUAL',280,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('auto_aftermarket',    '_ALL_',48,50,47,53,47,78,80,78,81,78,66,'LOW','MANUAL',180,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('real_estate',         '_ALL_',50,53,48,53,47,80,82,79,80,78,68,'LOW','MANUAL',220,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('home_decoration',     '_ALL_',50,52,47,54,47,80,82,79,81,78,68,'LOW','MANUAL',260,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('home_appliance',      '_ALL_',52,54,50,54,49,81,83,81,81,79,70,'LOW','MANUAL',240,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('furniture_home',      '_ALL_',49,51,47,53,47,79,81,78,80,78,67,'LOW','MANUAL',200,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('education',           '_ALL_',49,51,47,52,47,79,81,79,79,78,67,'LOW','MANUAL',90,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('healthcare',          '_ALL_',51,53,49,55,48,81,83,80,82,79,69,'LOW','MANUAL',180,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('medical_beauty',      '_ALL_',54,55,52,55,50,83,83,82,82,80,72,'LOW','MANUAL',500,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('pharma_health',       '_ALL_',52,53,50,55,48,82,83,81,83,79,70,'LOW','MANUAL',160,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('restaurant',          '_ALL_',54,55,53,55,51,83,83,82,82,80,73,'LOW','MANUAL',500,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('food_beverage',       '_ALL_',51,53,49,54,48,81,82,80,81,79,69,'LOW','MANUAL',230,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('alcohol_tea',         '_ALL_',52,54,50,54,48,82,83,81,81,79,70,'LOW','MANUAL',200,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('retail',              '_ALL_',51,53,50,52,48,81,82,81,79,78,69,'LOW','MANUAL',120,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('ecommerce',           '_ALL_',53,55,52,52,52,83,83,82,79,82,71,'LOW','MANUAL',260,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('beauty_care',         '_ALL_',52,54,49,54,48,82,83,80,82,79,70,'LOW','MANUAL',240,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('fashion_jewelry',     '_ALL_',50,52,48,53,47,80,82,79,80,78,68,'LOW','MANUAL',220,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('finance',             '_ALL_',55,55,53,55,51,83,83,82,83,81,73,'LOW','MANUAL',80,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('tech_software',       '_ALL_',52,50,55,48,53,81,79,83,78,82,70,'LOW','MANUAL',150,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('marketing_services',  '_ALL_',49,51,47,51,47,79,81,78,79,78,67,'LOW','MANUAL',150,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('logistics',           '_ALL_',47,49,47,52,47,78,80,78,79,78,65,'LOW','MANUAL',120,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('tourism',             '_ALL_',53,55,51,54,49,83,83,81,81,79,71,'LOW','MANUAL',500,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('hr_recruitment',      '_ALL_',47,49,47,51,47,78,80,78,79,78,65,'LOW','MANUAL',110,1,'2026-08-01','模拟基准 v1；待真实报告样本校准'),
('_ALL_',               '_ALL_',48,49,47,49,48,79,80,78,79,80,66,'LOW','MANUAL',500,1,'2026-08-01','模拟基准 v1；待真实报告样本校准');

-- Add only the standard industries absent from the original preset dictionary.
-- Existing values remain untouched to preserve historical display labels.
INSERT IGNORE INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark) VALUES
('presale_industry', 'new_energy_vehicle', '新能源汽车', 130, 1, '行业基准模拟数据 v1'),
('presale_industry', 'auto_aftermarket', '汽车后市场', 140, 1, '行业基准模拟数据 v1'),
('presale_industry', 'home_decoration', '家装建材', 150, 1, '行业基准模拟数据 v1'),
('presale_industry', 'home_appliance', '家电智能家居', 160, 1, '行业基准模拟数据 v1'),
('presale_industry', 'furniture_home', '家具家居', 170, 1, '行业基准模拟数据 v1'),
('presale_industry', 'pharma_health', '药品保健医疗器械', 180, 1, '行业基准模拟数据 v1'),
('presale_industry', 'food_beverage', '食品饮料', 190, 1, '行业基准模拟数据 v1'),
('presale_industry', 'alcohol_tea', '酒类茶叶', 200, 1, '行业基准模拟数据 v1'),
('presale_industry', 'ecommerce', '电商跨境电商', 210, 1, '行业基准模拟数据 v1'),
('presale_industry', 'fashion_jewelry', '服饰鞋包珠宝', 220, 1, '行业基准模拟数据 v1'),
('presale_industry', 'marketing_services', '广告营销公关', 230, 1, '行业基准模拟数据 v1'),
('presale_industry', 'logistics', '物流供应链', 240, 1, '行业基准模拟数据 v1'),
('presale_industry', 'hr_recruitment', '招聘人力资源', 250, 1, '行业基准模拟数据 v1');
