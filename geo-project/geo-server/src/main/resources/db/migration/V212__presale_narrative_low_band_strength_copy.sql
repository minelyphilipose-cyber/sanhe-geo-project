-- Add configurable low-band filler copy for narrative findings.
-- V211 may already be applied in environments, so these seed changes live in V212.

INSERT INTO presale_narrative_finding_copy
    (config_version, code, tier, title_template, body_template, evidence_template, priority, remark)
VALUES
    ('v1', 'HV_COVERAGE_LOW', 'STRENGTH',
     '仍有高价值场景需要补齐',
     '{{brand_name}} 当前最需要先补齐 {{scene_example}} 等高价值问题,让 AI 在关键决策入口能稳定识别你。',
     '{{high_value_covered}}/{{high_value_total}} 个高价值问题被覆盖',
     70, 'STRENGTH:低档补位模板,运营可配置'),
    ('v1', 'RECO_ABSENT', 'STRENGTH',
     '推荐入口需要先建立存在感',
     '当 {{customer_term}} 直接询问推荐选择时,{{brand_name}} 还需要更多权威信源和场景内容来进入 AI 答案。',
     '推荐型场景提及率 {{recommendation_rate}}%',
     72, 'STRENGTH:低档补位模板,运营可配置'),
    ('v1', 'PLATFORM_BLIND', 'STRENGTH',
     '多平台可见度需要补齐',
     '{{brand_name}} 在 {{weak_platforms}} 等平台仍缺少稳定出现,需要先补齐基础信源覆盖。',
     '弱覆盖平台:{{weak_platforms}}',
     74, 'STRENGTH:低档补位模板,运营可配置');
