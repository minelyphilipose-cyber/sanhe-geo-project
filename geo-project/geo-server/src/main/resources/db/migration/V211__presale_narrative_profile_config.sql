-- =========================================================================
-- V211__presale_narrative_profile_config.sql
--
-- 售前报表 · 叙事画像配置
--
-- 设计约束:
--   * 画像在 L2 由确定性规则生成,生成链路不新增在线 LLM。
--   * config_version/profile_version 仅用于诊断,不做历史配置冻结。
--   * finding copy 以 code × tier 为主,band/archetype 仅作为可空覆盖条件。
--   * heatmap pattern 使用规格统一枚举:
--       NEW_CUSTOMER_BLANK / RECO_UNSTABLE / RECO_EMERGING / BROAD_PRESENCE
-- =========================================================================

SET NAMES utf8mb4;

CREATE TABLE presale_narrative_band_rule (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT,
    config_version      VARCHAR(32)     NOT NULL DEFAULT 'v1',
    band                VARCHAR(20)     NOT NULL,
    min_overall         DECIMAL(5,2)    NULL COMMENT '极端兜底下限;常规判定应优先使用 ratio',
    max_overall         DECIMAL(5,2)    NULL COMMENT '极端兜底上限;常规判定应优先使用 ratio',
    min_avg_ratio       DECIMAL(6,4)    NULL COMMENT 'overall / industry_avg.overall 下限',
    max_avg_ratio       DECIMAL(6,4)    NULL COMMENT 'overall / industry_avg.overall 上限',
    min_top1_ratio      DECIMAL(6,4)    NULL COMMENT 'overall / top1.overall 下限',
    max_top1_ratio      DECIMAL(6,4)    NULL COMMENT 'overall / top1.overall 上限',
    min_delta_avg       DECIMAL(5,2)    NULL COMMENT 'overall - industry_avg.overall 下限',
    max_delta_avg       DECIMAL(5,2)    NULL COMMENT 'overall - industry_avg.overall 上限',
    min_delta_top1      DECIMAL(5,2)    NULL COMMENT 'overall - top1.overall 下限',
    max_delta_top1      DECIMAL(5,2)    NULL COMMENT 'overall - top1.overall 上限',
    min_mention_score   DECIMAL(5,2)    NULL,
    min_coverage_score  DECIMAL(5,2)    NULL,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    sort_order          INT             NOT NULL DEFAULT 100,
    remark              VARCHAR(500)    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_version_band (config_version, band),
    KEY idx_enabled_order (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售前报表叙事画像档位规则';

CREATE TABLE presale_narrative_finding_copy (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT,
    config_version      VARCHAR(32)     NOT NULL DEFAULT 'v1',
    code                VARCHAR(80)     NOT NULL COMMENT 'rule_code 或 DERIVED/STRENGTH code',
    tier                VARCHAR(20)     NOT NULL COMMENT 'T1/T2/T3/STRENGTH',
    band_override       VARCHAR(20)     NULL,
    archetype_override  VARCHAR(40)     NULL,
    title_template      VARCHAR(200)    NOT NULL,
    body_template       VARCHAR(1000)   NOT NULL,
    evidence_template   VARCHAR(500)    NULL,
    priority            INT             NOT NULL DEFAULT 100,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    remark              VARCHAR(500)    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_copy_match (config_version, code, tier, band_override, archetype_override),
    KEY idx_code_tier_enabled (code, tier, enabled),
    KEY idx_enabled_priority (enabled, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售前报表叙事发现文案模板';

CREATE TABLE presale_heatmap_summary (
    id                    BIGINT          PRIMARY KEY AUTO_INCREMENT,
    config_version        VARCHAR(32)     NOT NULL DEFAULT 'v1',
    heatmap_pattern       VARCHAR(40)     NOT NULL,
    band_override         VARCHAR(20)     NULL,
    summary_template      VARCHAR(500)    NOT NULL,
    color_legend_template VARCHAR(500)    NULL,
    enabled               TINYINT(1)      NOT NULL DEFAULT 1,
    sort_order            INT             NOT NULL DEFAULT 100,
    remark                VARCHAR(500)    NULL,
    created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pattern_band (config_version, heatmap_pattern, band_override),
    KEY idx_enabled_order (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售前报表热力图总览句模板';

CREATE TABLE presale_industry_lexicon (
    id                BIGINT          PRIMARY KEY AUTO_INCREMENT,
    industry          VARCHAR(50)     NOT NULL,
    customer_term     VARCHAR(50)     NOT NULL COMMENT '如患者/顾客/客户',
    conversion_term   VARCHAR(50)     NOT NULL COMMENT '如到诊/下单/预约',
    industry_short    VARCHAR(50)     NOT NULL COMMENT '行业短称',
    approved          TINYINT(1)      NOT NULL DEFAULT 0,
    source            VARCHAR(20)     NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/LLM_DRAFT/GENERIC',
    config_version    VARCHAR(32)     NOT NULL DEFAULT 'v1',
    remark            VARCHAR(500)    NULL,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_industry_version (industry, config_version),
    KEY idx_approved (approved, industry)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售前报表行业叙事词库';

CREATE TABLE presale_industry_lexicon_review_task (
    id                BIGINT          PRIMARY KEY AUTO_INCREMENT,
    industry          VARCHAR(50)     NOT NULL,
    draft_json        JSON            NULL COMMENT '离线 LLM 草稿或人工待审内容',
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    source            VARCHAR(20)     NOT NULL DEFAULT 'MISSING_LEXICON',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_status_time (status, created_at),
    KEY idx_industry (industry)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售前报表行业词库冷启动审核任务';

INSERT INTO presale_narrative_band_rule
    (config_version, band, min_overall, max_overall, min_avg_ratio, max_avg_ratio,
     min_top1_ratio, max_top1_ratio, min_delta_avg, max_delta_avg,
     min_delta_top1, max_delta_top1, min_mention_score, min_coverage_score, sort_order, remark)
VALUES
    ('v1', 'INVISIBLE', NULL, NULL, NULL, 0.4000, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 10,
     '按相对行业均值判定:overall < industry_avg.overall * 0.40'),
    ('v1', 'BEHIND', NULL, NULL, 0.4000, 0.8500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 20,
     '按相对行业均值判定:industry_avg.overall * 0.40 <= overall < industry_avg.overall * 0.85'),
    ('v1', 'MIDDLE', NULL, NULL, 0.8500, 1.1500, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 30,
     '按相对行业均值判定:industry_avg.overall * 0.85 <= overall < industry_avg.overall * 1.15'),
    ('v1', 'STRONG', NULL, NULL, 1.1500, NULL, NULL, 0.9000, NULL, NULL, NULL, NULL, 50.00, 50.00, 40,
     '高于行业均值 15% 且尚未达到 Top1 90%;ranking 为 null 时 ranking 维度跳过'),
    ('v1', 'LEADER', NULL, NULL, NULL, NULL, 0.9000, NULL, NULL, NULL, NULL, NULL, 65.00, 65.00, 50,
     '接近或达到行业 Top1:overall >= top1.overall * 0.90;ranking 为 null 时 ranking 维度跳过');

INSERT INTO presale_narrative_finding_copy
    (config_version, code, tier, title_template, body_template, evidence_template, priority, remark)
VALUES
    ('v1', 'HV_COVERAGE_LOW', 'T1',
     '高价值决策场景覆盖不足',
     '{{brand_name}} 在 {{scene_example}} 等高价值场景中尚未形成稳定出现,会影响 {{loss_phrase}}。',
     '{{high_value_covered}}/{{high_value_total}} 个高价值问题被覆盖',
     10, '统一承接 RULE_COVERAGE_LOW_RECOMMEND 与 RULE_SCENE_MISS_HIGH_VALUE'),
    ('v1', 'HV_COVERAGE_LOW', 'T2',
     '高价值场景覆盖仍需补齐',
     '{{brand_name}} 已有部分场景被 AI 识别,但 {{scene_example}} 等高价值问题仍需要补齐。',
     '{{high_value_covered}}/{{high_value_total}} 个高价值问题被覆盖',
     18, 'HV 覆盖中等风险模板'),
    ('v1', 'HV_COVERAGE_LOW', 'T3',
     '局部高价值场景可继续加固',
     '{{brand_name}} 的整体覆盖已具备基础,后续可继续加固 {{scene_example}} 等局部高价值问题。',
     '{{high_value_covered}}/{{high_value_total}} 个高价值问题被覆盖',
     60, 'HV 覆盖低风险模板'),
    ('v1', 'RECO_ABSENT', 'T1',
     '推荐型查询尚未建立存在感',
     '当 {{customer_term}} 直接询问推荐选择时,AI 尚未稳定提及 {{brand_name}},这是当前最优先补齐的入口。',
     '推荐型场景提及率 {{recommendation_rate}}%',
     20, 'DERIVED:自然推荐型缺失'),
    ('v1', 'BRANDED_ONLY', 'T2',
     '品牌认知未有效转入决策场景',
     'AI 能在认知类问题中识别 {{brand_name}},但在推荐、咨询或场景型问题中承接不足。',
     '认知场景得分 {{cognitive_score}},推荐场景提及率 {{recommendation_rate}}%',
     30, 'DERIVED:认知有、决策弱'),
    ('v1', 'SENTIMENT_THIN', 'T2',
     'AI 对品牌的正向表述偏薄',
     '当前回答以中性描述为主,AI 还没有形成足够清晰的推荐理由和优势表达。',
     '中性占比 {{neutral_share}}%,正向占比 {{positive_share}}%',
     40, 'DERIVED:neutral_share 触发,不等同低分或负面'),
    ('v1', 'NEGATIVE_PRESSURE', 'T1',
     '存在真实负面表达需要优先处理',
     'AI 回答中已经出现明确负面证据,需要先处理事实澄清、口碑解释和权威信源补强。',
     '检出负面证据 {{negative_count}} 条',
     5, 'DERIVED/RULE:必须由真实负面过滤后触发'),
    ('v1', 'PLATFORM_BLIND', 'T2',
     '平台覆盖存在明显盲区',
     '{{brand_name}} 在部分平台或场景中的出现仍不稳定,需要补齐多平台信源和内容分发。',
     '弱覆盖平台:{{weak_platforms}}',
     45, 'DERIVED/RULE:平台盲区'),
    ('v1', 'COMPETITOR_OVERTAKE_STRONG', 'T1',
     '推荐型场景中存在竞品替代风险',
     '在 {{scene_example}} 等自然推荐场景中,AI 已覆盖竞品但尚未稳定提及 {{brand_name}},需要优先处理。',
     '推荐型缺失场景中竞品覆盖:{{competitor_names}}',
     8, '强事实模板:仅在自然推荐型场景存在竞品覆盖证据时使用'),
    ('v1', 'COMPETITOR_OVERTAKE_SOFT', 'T2',
     '被点名比较时 AI 更倾向竞品',
     '在用户已经点名比较的场景中,AI 对竞品的偏好更明显;该信号不等同于自然推荐场景被替代。',
     '竞品偏好率 {{competitor_preferred_rate}}%',
     38, '软表达模板:仅 comparison judge 证据'),
    ('v1', 'COVERAGE_STRENGTH', 'STRENGTH',
     '多场景可见度已经形成基础',
     '{{brand_name}} 已在多个核心场景中被 AI 识别,后续重点是把稳定覆盖转化为更强推荐理由。',
     '核心场景覆盖率 {{coverage_score}} 分',
     80, 'STRENGTH:高分补位模板'),
    ('v1', 'RECO_STRENGTH', 'STRENGTH',
     '推荐入口已具备可见度基础',
     '{{brand_name}} 已在推荐型问题中形成一定出现,后续重点是提升推荐稳定性和理由完整度。',
     '推荐型场景提及率 {{recommendation_rate}}%',
     85, 'STRENGTH:推荐优势补位模板'),
    ('v1', 'DEFEND_GAP', 'STRENGTH',
     '领先基础下仍需守住局部短板',
     '{{brand_name}} 的整体表现已具备优势,但仍应持续维护对比、问询和平台盲区中的局部短板。',
     '综合得分 {{overall_score}}',
     90, 'STRENGTH:防守型补位模板');

INSERT INTO presale_heatmap_summary
    (config_version, heatmap_pattern, summary_template, color_legend_template, sort_order, remark)
VALUES
    ('v1', 'NEW_CUSTOMER_BLANK',
     '新顾客入口场景仍存在明显空白,需要优先补齐推荐、咨询和具体场景问题中的品牌出现。',
     '颜色越深表示该场景下品牌越稳定出现;灰色表示该平台未参与或无有效样本。',
     10, '先判空白'),
    ('v1', 'RECO_UNSTABLE',
     '推荐场景已有出现,但平台间波动较大,说明 AI 对品牌的推荐信号还不稳定。',
     '颜色差异体现不同平台的推荐稳定性差异;灰色表示该平台未参与或无有效样本。',
     20, '推荐有覆盖但不稳定'),
    ('v1', 'RECO_EMERGING',
     '推荐场景开始出现品牌信号,但覆盖广度和强度仍需要继续放大。',
     '颜色越深表示该场景信号越强;浅色表示仍处在建设初期。',
     30, '推荐初步出现'),
    ('v1', 'BROAD_PRESENCE',
     '新老顾客场景均已有品牌出现,当前重点是保持稳定覆盖并补强局部短板。',
     '颜色用于观察平台和场景之间的强弱差异,不是单一好坏判断。',
     40, '广泛存在');

INSERT INTO presale_industry_lexicon
    (industry, customer_term, conversion_term, industry_short, approved, source, config_version, remark)
VALUES
    ('_ALL_', '客户', '转化', '行业', 1, 'GENERIC', 'v1', '通用兜底词库'),
    ('口腔医疗', '患者', '到诊', '口腔', 1, 'MANUAL', 'v1', 'MVP 首批人工种子');
