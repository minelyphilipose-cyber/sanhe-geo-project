-- =========================================================================
-- V62__create_presale_report_module.sql  (v4)
--
-- 售前报表模块 · 建表脚本
--
-- 前置依赖:
--   V61 已下线旧 presale 模块(表/字典/权限/代码)
--   现有表:ai_platform_config / sys_dict_item / sys_permission / sys_role / sys_role_permission(id 映射)
--
-- 本次变更 vs v3 的主要差异:
--   1. Section 7 默认角色绑定改用真实 RBAC 结构(role_id + permission_id),
--      之前 v3 的 (role_key, perm_key) 列模型是错的,会导致迁移执行失败。
--      改用 JOIN sys_role + sys_permission 查出 id 后 INSERT。
--   2. presale_benchmark_history 增加 industry_role 列 + 对应索引,
--      避免二维基准值下的审计失真。
--   3. Section 7 开头新增占位符 guard 检查 SELECT,便于 DBA 在执行前核对。
--
--   其他延续 v3 的所有变更:
--   - presale_benchmark 二维 (industry, industry_role) + _ALL_ 兜底
--   - schema_version 默认 'v1.2'
--   - 三层快照字段(raw/computed/editable)
--   - 冻结仅 MANUAL
--   - 子表 version_id 外键 + CASCADE
--   - presale_platform_config 独立子表
--
-- 执行顺序:严格 V61 -> V62。Flyway 自动按版本号顺序执行。
--
-- !!! DBA 执行前必读 !!!
--   Section 7 的 role_key 占位符(__ROLE_SUPER_ADMIN__ 等)必须先替换为
--   仓库真实 role_key。执行前先跑 Section 7 开头的 guard SELECT 核对。
-- =========================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- =========================================================================
-- Section 1. 配置类表(开发期导入,运行期只读)
-- =========================================================================

-- ----------------------------
-- 1.1  Prompt 模板库
-- ----------------------------
CREATE TABLE presale_prompt_template (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT,
    prompt_code         VARCHAR(50)     NOT NULL                    COMMENT 'Prompt 唯一编码,如 REC_REST_001',
    industry            VARCHAR(50)     NOT NULL                    COMMENT '行业,对应 sys_dict_item.dict_type=presale_industry',
    industry_role       VARCHAR(50)     NOT NULL                    COMMENT '身份,对应 sys_dict_item.dict_type=presale_industry_role',
    category            VARCHAR(20)     NOT NULL                    COMMENT '意图类型: 推荐型/对比型/问题型/认知型/场景型',
    business_value      VARCHAR(10)     NOT NULL                    COMMENT '业务价值: 高/中/低',
    prompt_content      TEXT            NOT NULL                    COMMENT 'Prompt 正文,支持 {brand}/{region}/{competitor} 变量占位符',
    has_competitor_var  TINYINT(1)      NOT NULL DEFAULT 0          COMMENT '是否含竞品变量 {competitor}(决定是否进入第二轮)',
    enabled             TINYINT(1)      NOT NULL DEFAULT 1          COMMENT '是否启用',
    sort_order          INT             NOT NULL DEFAULT 100,
    remark              VARCHAR(500)    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_prompt_code (prompt_code),
    KEY idx_industry_role (industry, industry_role, enabled),
    KEY idx_category_value (category, business_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售前 Prompt 模板库';


-- ----------------------------
-- 1.2  行业×身份有效组合映射
-- ----------------------------
CREATE TABLE presale_industry_role_mapping (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT,
    industry            VARCHAR(50)     NOT NULL,
    industry_role       VARCHAR(50)     NOT NULL,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    remark              VARCHAR(500)    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_industry_role (industry, industry_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行业身份有效组合映射';


-- ----------------------------
-- 1.3  行业基准值(二维:行业 × 身份)
-- ----------------------------
-- 匹配策略:
--   1. 优先 (industry, industry_role) 精确匹配
--   2. 未命中 → 回退到 (industry, '_ALL_') 行业级兜底
--   3. 兜底行由业务侧为每个行业手工补一条,避免生成时找不到基准值
CREATE TABLE presale_benchmark (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT,
    industry            VARCHAR(50)     NOT NULL,
    industry_role       VARCHAR(50)     NOT NULL                    COMMENT '身份;行业级兜底用 _ALL_',
    -- 行业平均值
    avg_overall         DECIMAL(5,2)    NOT NULL,
    avg_mention         DECIMAL(5,2)    NOT NULL,
    avg_ranking         DECIMAL(5,2)    NOT NULL,
    avg_sentiment       DECIMAL(5,2)    NOT NULL,
    avg_coverage        DECIMAL(5,2)    NOT NULL,
    -- 行业 Top1
    top1_overall        DECIMAL(5,2)    NOT NULL,
    top1_mention        DECIMAL(5,2)    NOT NULL,
    top1_ranking        DECIMAL(5,2)    NOT NULL,
    top1_sentiment      DECIMAL(5,2)    NOT NULL,
    top1_coverage       DECIMAL(5,2)    NOT NULL,
    -- Top10 阈值
    top10_score         DECIMAL(5,2)    NOT NULL,
    -- 元信息
    confidence_level    VARCHAR(10)     NOT NULL                    COMMENT 'HIGH/MEDIUM/LOW',
    source              VARCHAR(20)     NOT NULL                    COMMENT 'MANUAL/AUTO_P50/HYBRID',
    sample_size         INT             NOT NULL DEFAULT 0,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    effective_from      DATE            NOT NULL                    COMMENT '生效起始日',
    remark              VARCHAR(500)    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_industry_role_effective (industry, industry_role, effective_from),
    KEY idx_industry_role_enabled (industry, industry_role, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行业×身份基准值(支持按日期生效,含行业级兜底)';


-- ----------------------------
-- 1.4  基准值变更审计
-- ----------------------------
CREATE TABLE presale_benchmark_history (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT,
    benchmark_id        BIGINT          NOT NULL                    COMMENT '关联 presale_benchmark.id',
    industry            VARCHAR(50)     NOT NULL,
    industry_role       VARCHAR(50)     NOT NULL                    COMMENT '身份;行业级兜底用 _ALL_。与 presale_benchmark 保持一致,便于按身份粒度追溯',
    operation           VARCHAR(20)     NOT NULL                    COMMENT 'INSERT/UPDATE/DISABLE',
    before_snapshot     JSON            NULL,
    after_snapshot      JSON            NULL,
    operator_id         BIGINT          NULL,
    operator_name       VARCHAR(100)    NULL,
    remark              VARCHAR(500)    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_benchmark (benchmark_id, created_at),
    KEY idx_industry_role_time (industry, industry_role, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='基准值变更审计(二维,含 industry_role)';


-- ----------------------------
-- 1.5  优化规则库
-- ----------------------------
CREATE TABLE presale_optimization_rule (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT,
    rule_code           VARCHAR(50)     NOT NULL                    COMMENT '规则编码,如 RULE_COVERAGE_LOW_RECOMMEND',
    rule_name           VARCHAR(200)    NOT NULL,
    category            VARCHAR(30)     NOT NULL                    COMMENT '基础设施/内容建设/关系建设/平台扩展',
    default_priority    VARCHAR(10)     NOT NULL                    COMMENT '默认优先级 HIGH/MEDIUM/LOW',
    -- 触发条件(DSL 或 SpEL 表达式)
    trigger_expression  TEXT            NOT NULL                    COMMENT '触发条件表达式',
    -- 文案模板(支持 {{variable}} 占位符,由 evidence_data 填充)
    title_template      VARCHAR(500)    NOT NULL,
    description_template TEXT           NOT NULL,
    evidence_template   VARCHAR(1000)   NULL,
    enabled             TINYINT(1)      NOT NULL DEFAULT 1,
    sort_order          INT             NOT NULL DEFAULT 100,
    remark              VARCHAR(500)    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_rule_code (rule_code),
    KEY idx_category_enabled (category, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优化规则库';


-- ----------------------------
-- 1.6  售前平台专用配置(不污染公共 ai_platform_config)
-- ----------------------------
CREATE TABLE presale_platform_config (
    id                  BIGINT          PRIMARY KEY AUTO_INCREMENT,
    platform_code       VARCHAR(64)     NOT NULL                    COMMENT '关联 ai_platform_config.platform_code',
    in_whitelist        TINYINT(1)      NOT NULL DEFAULT 0          COMMENT '是否进入售前测试白名单',
    rate_limit_qps      INT             NOT NULL DEFAULT 3          COMMENT '售前专用 QPS(优先于 ai_platform_config.rpm_limit 换算)',
    max_retry           INT             NOT NULL DEFAULT 2          COMMENT '最大重试次数',
    timeout_ms          INT             NOT NULL DEFAULT 60000      COMMENT '单次调用超时(ms)',
    remark              VARCHAR(500)    NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_code (platform_code),
    KEY idx_whitelist (in_whitelist)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售前诊断平台专用配置';


-- =========================================================================
-- Section 2. 业务主表
-- =========================================================================

-- ----------------------------
-- 2.1  售前报表主表
-- ----------------------------
CREATE TABLE presale_report (
    id                      BIGINT          PRIMARY KEY AUTO_INCREMENT,
    -- 客户信息(用户填报)
    brand_name              VARCHAR(200)    NOT NULL,
    industry                VARCHAR(50)     NOT NULL,
    industry_role           VARCHAR(50)     NOT NULL,
    region                  VARCHAR(100)    NOT NULL,
    user_demand             TEXT            NULL                        COMMENT '需求说明',
    -- 归属
    created_by              BIGINT          NOT NULL                    COMMENT '创建人(运营)',
    assigned_to             BIGINT          NULL                        COMMENT '指派给谁跟进',
    -- 版本状态
    current_version_no      INT             NOT NULL DEFAULT 0          COMMENT '最新版本号,0 表示尚未生成',
    current_version_id      BIGINT          NULL                        COMMENT '最新版本的 version_id(便于直接 JOIN)',
    status                  VARCHAR(30)     NOT NULL DEFAULT 'DRAFT'    COMMENT 'DRAFT/GENERATING/DONE/FAILED/ARCHIVED',
    -- 软删除
    deleted_at              DATETIME        NULL,
    deleted_by              BIGINT          NULL,
    -- 时间戳
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_created_by (created_by, created_at),
    KEY idx_assigned (assigned_to, status),
    KEY idx_brand_industry (brand_name, industry),
    KEY idx_status (status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售前报表主表';


-- ----------------------------
-- 2.2  售前报表版本快照(核心表,三层结构)
-- ----------------------------
CREATE TABLE presale_report_version (
    id                      BIGINT          PRIMARY KEY AUTO_INCREMENT,
    report_id               BIGINT          NOT NULL                    COMMENT '主表 ID',
    version_no              INT             NOT NULL                    COMMENT '版本号,严格单调递增',
    schema_version          VARCHAR(20)     NOT NULL DEFAULT 'v1.2'     COMMENT 'report_data schema 版本',

    -- ===== 三层快照字段 =====
    raw_snapshot_json       JSON            NULL                        COMMENT 'L1 原始事实层(meta/client_info/test_summary/platform_breakdown/competitors/sentiment_detail/benchmarks_frozen)',
    computed_snapshot_json  JSON            NULL                        COMMENT 'L2 计算结果层(scores/intent_breakdown/scene_coverage/optimization_findings/roi_simulation)',
    editable_content_json   JSON            NULL                        COMMENT 'L3 可编辑文案层(report_title/key_takeaways/optimization_findings_content 等)',

    -- ===== 生成状态 =====
    generation_status       VARCHAR(30)     NOT NULL                    COMMENT 'INIT/QUEUED/LOADING_PROMPTS/TESTING_ROUND_1/ANALYZING_ROUND_1/COMPETITOR_DETECTION/TESTING_ROUND_2/ANALYZING_ROUND_2/AGGREGATING/FINALIZING/DONE/FAILED',
    failure_category        VARCHAR(50)     NULL,
    failure_detail          TEXT            NULL,

    -- ===== 降级标记 =====
    is_degraded             TINYINT(1)      NOT NULL DEFAULT 0,
    degraded_platforms      JSON            NULL,

    -- ===== 冻结(仅手动 MANUAL,不再 AUTO_AFTER_EXPORT) =====
    frozen_at               DATETIME        NULL                        COMMENT '定稿冻结时间',
    frozen_by               BIGINT          NULL                        COMMENT '冻结操作人',
    frozen_reason           VARCHAR(100)    NULL                        COMMENT 'MANUAL(预留未来扩展)',

    -- ===== L3 文案编辑审计 =====
    content_updated_at      DATETIME        NULL                        COMMENT 'L3 最后一次编辑时间',
    content_updated_by      BIGINT          NULL,

    -- ===== 导出统计(不参与冻结判定) =====
    export_attempt_count    INT             NOT NULL DEFAULT 0,
    export_success_count    INT             NOT NULL DEFAULT 0,
    export_success_at       DATETIME        NULL,
    last_export_error       TEXT            NULL,

    -- ===== 并发执行统计 =====
    total_llm_calls         INT             NULL,
    total_retry_count       INT             NULL,
    rate_limit_hit_count    INT             NULL,
    duration_seconds        INT             NULL,

    -- ===== 时间戳 =====
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_report_version (report_id, version_no),
    KEY idx_status (generation_status),
    KEY idx_frozen (frozen_at),
    CONSTRAINT fk_version_report FOREIGN KEY (report_id) REFERENCES presale_report(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售前报表版本快照(三层结构)';


-- =========================================================================
-- Section 3. 运行数据表(子表统一 version_id 外键)
-- =========================================================================

-- ----------------------------
-- 3.1  AI 测试原始结果
-- ----------------------------
CREATE TABLE presale_ai_test_result (
    id                      BIGINT          PRIMARY KEY AUTO_INCREMENT,
    report_id               BIGINT          NOT NULL,
    version_id              BIGINT          NOT NULL                    COMMENT '关联 presale_report_version.id',
    version_no              INT             NOT NULL                    COMMENT '冗余版本号(查询友好)',
    round                   TINYINT         NOT NULL                    COMMENT '测试轮次: 1 或 2',
    -- 输入
    platform_code           VARCHAR(64)     NOT NULL,
    prompt_template_id      BIGINT          NOT NULL,
    prompt_content          TEXT            NOT NULL                    COMMENT '填充变量后的实际 prompt',
    -- 原始输出
    raw_response            MEDIUMTEXT      NULL,
    response_tokens         INT             NULL,
    -- 结构化分析(LLM 产出)
    is_mentioned            TINYINT(1)      NULL,
    mention_position        INT             NULL,
    sentiment               VARCHAR(10)     NULL                        COMMENT 'POSITIVE/NEUTRAL/NEGATIVE',
    sentiment_keywords      JSON            NULL,
    mentioned_competitors   JSON            NULL,
    context_snippet         TEXT            NULL,
    analysis_model          VARCHAR(50)     NULL,
    -- 执行信息
    call_status             VARCHAR(20)     NOT NULL                    COMMENT 'SUCCESS/FAILED/RATE_LIMITED/TIMEOUT',
    call_duration_ms        INT             NULL,
    retry_count             INT             NOT NULL DEFAULT 0,
    error_type              VARCHAR(50)     NULL,
    error_message           TEXT            NULL,
    -- 剔除标记
    is_excluded             TINYINT(1)      NOT NULL DEFAULT 0,
    excluded_by             BIGINT          NULL,
    excluded_at             DATETIME        NULL,
    exclude_reason          VARCHAR(200)    NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    KEY idx_version (version_id),
    KEY idx_report_version (report_id, version_no),
    KEY idx_platform (platform_code, call_status),
    KEY idx_excluded (is_excluded, version_id),
    CONSTRAINT fk_test_result_version FOREIGN KEY (version_id)
        REFERENCES presale_report_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 测试原始结果与结构化分析';


-- ----------------------------
-- 3.2  优化发现
-- ----------------------------
CREATE TABLE presale_optimization_finding (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    report_id       BIGINT          NOT NULL,
    version_id      BIGINT          NOT NULL,
    version_no      INT             NOT NULL,
    finding_id      VARCHAR(20)     NOT NULL                    COMMENT '本次报告内唯一,如 F001',
    rule_code       VARCHAR(50)     NOT NULL,
    priority        VARCHAR(10)     NOT NULL,
    category        VARCHAR(30)     NOT NULL,
    evidence_data   JSON            NULL                        COMMENT '规则触发时的结构化上下文',
    sort_order      INT             NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_version_finding (version_id, finding_id),
    KEY idx_version (version_id),
    KEY idx_priority (priority),
    CONSTRAINT fk_finding_version FOREIGN KEY (version_id)
        REFERENCES presale_report_version(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本次报告识别出的优化发现(L2 结构化数据)';


-- ----------------------------
-- 3.3  生成操作日志(失败时 version_id 可为 NULL)
-- ----------------------------
CREATE TABLE presale_generation_log (
    id                      BIGINT          PRIMARY KEY AUTO_INCREMENT,
    report_id               BIGINT          NOT NULL,
    version_id              BIGINT          NULL                        COMMENT '失败时可能为 NULL',
    version_no              INT             NULL,
    triggered_by            BIGINT          NOT NULL,
    trigger_type            VARCHAR(20)     NOT NULL                    COMMENT 'USER_TRIGGER/USER_RETRY/AUTO_RETRY',
    status                  VARCHAR(30)     NOT NULL                    COMMENT 'SUCCESS/FAILED/CANCELLED/TIMEOUT',
    started_at              DATETIME        NOT NULL,
    finished_at             DATETIME        NULL,
    duration_seconds        INT             NULL,
    failure_category        VARCHAR(50)     NULL,
    failure_detail          TEXT            NULL,
    -- 并发统计
    total_llm_calls         INT             NULL,
    total_retry_count       INT             NULL,
    rate_limit_hit_count    INT             NULL,
    max_concurrent_reached  INT             NULL,
    avg_llm_duration_ms     INT             NULL,
    -- 配额
    count_quota             TINYINT(1)      NOT NULL DEFAULT 0,
    hit_quota_limit         TINYINT(1)      NOT NULL DEFAULT 0,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    KEY idx_report (report_id, created_at),
    KEY idx_version (version_id),
    KEY idx_user_date (triggered_by, created_at),
    KEY idx_status_date (status, created_at)
    -- 注意:version_id 不设外键,因失败场景允许为 NULL 且级联删除会删掉失败审计
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='生成操作日志';


-- =========================================================================
-- Section 4. 字典数据初始化(按仓库真实 schema:dict_type/dict_key/dict_value/enabled)
-- =========================================================================

-- ----------------------------
-- 4.1  报告类型字典项
-- ----------------------------
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark) VALUES
    ('report_type', 'presale_diagnosis_v2', '售前诊断报告 V2', 10, 1, '售前报表模块 V2(三层快照)');

-- ----------------------------
-- 4.2  售前行业字典
-- ----------------------------
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark) VALUES
    ('presale_industry', 'restaurant',      '餐饮',       10,  1, NULL),
    ('presale_industry', 'education',       '教育培训',   20,  1, NULL),
    ('presale_industry', 'medical_beauty',  '医美',       30,  1, NULL),
    ('presale_industry', 'healthcare',      '医疗健康',   40,  1, NULL),
    ('presale_industry', 'retail',          '零售',       50,  1, NULL),
    ('presale_industry', 'real_estate',     '房产',       60,  1, NULL),
    ('presale_industry', 'automotive',      '汽车',       70,  1, NULL),
    ('presale_industry', 'finance',         '金融',       80,  1, NULL),
    ('presale_industry', 'tourism',         '旅游',       90,  1, NULL),
    ('presale_industry', 'b2b_service',     'B2B 服务',  100, 1, NULL),
    ('presale_industry', 'beauty_care',     '美妆个护',  110, 1, NULL),
    ('presale_industry', 'tech_software',   '科技软件',  120, 1, NULL);

-- ----------------------------
-- 4.3  售前身份字典
-- ----------------------------
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark) VALUES
    ('presale_industry_role', 'chain_brand',      '连锁品牌',     10, 1, NULL),
    ('presale_industry_role', 'single_store',     '单店',         20, 1, NULL),
    ('presale_industry_role', 'franchise',        '加盟商',       30, 1, NULL),
    ('presale_industry_role', 'manufacturer',     '生产厂家',     40, 1, NULL),
    ('presale_industry_role', 'dealer',           '经销商',       50, 1, NULL),
    ('presale_industry_role', 'platform',         '平台方',       60, 1, NULL),
    ('presale_industry_role', 'service_provider', '服务商',       70, 1, NULL),
    ('presale_industry_role', 'kol',              '个人/KOL',     80, 1, NULL);

-- ----------------------------
-- 4.4  意图类型字典
-- ----------------------------
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark) VALUES
    ('presale_intent_category', 'recommendation', '推荐型', 10, 1, NULL),
    ('presale_intent_category', 'comparison',     '对比型', 20, 1, NULL),
    ('presale_intent_category', 'problem',        '问题型', 30, 1, NULL),
    ('presale_intent_category', 'awareness',      '认知型', 40, 1, NULL),
    ('presale_intent_category', 'scene',          '场景型', 50, 1, NULL);

-- ----------------------------
-- 4.5  业务价值字典
-- ----------------------------
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark) VALUES
    ('presale_business_value', 'high',   '高', 10, 1, NULL),
    ('presale_business_value', 'medium', '中', 20, 1, NULL),
    ('presale_business_value', 'low',    '低', 30, 1, NULL);

-- ----------------------------
-- 4.6  优化分类字典
-- ----------------------------
INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order, enabled, remark) VALUES
    ('presale_optimization_category', 'infrastructure',  '基础设施', 10, 1, NULL),
    ('presale_optimization_category', 'content',         '内容建设', 20, 1, NULL),
    ('presale_optimization_category', 'relationship',    '关系建设', 30, 1, NULL),
    ('presale_optimization_category', 'platform_expand', '平台扩展', 40, 1, NULL);


-- =========================================================================
-- Section 5. 权限初始化(按仓库真实 schema:perm_key/perm_name/module/action/status)
-- =========================================================================

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.report.list', '售前报表-列表', 'presale', 'list', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.report.list'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.report.create', '售前报表-创建', 'presale', 'create', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.report.create'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.report.view', '售前报表-查看', 'presale', 'view', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.report.view'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.report.generate', '售前报表-生成', 'presale', 'generate', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.report.generate'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.report.edit_content', '售前报表-编辑文案', 'presale', 'edit', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.report.edit_content'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.report.freeze', '售前报表-定稿冻结', 'presale', 'freeze', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.report.freeze'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.report.export', '售前报表-导出 PDF', 'presale', 'export', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.report.export'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.report.delete', '售前报表-删除', 'presale', 'delete', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.report.delete'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.prompt.manage', '售前配置-Prompt 管理', 'presale', 'manage', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.prompt.manage'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.benchmark.manage', '售前配置-基准值管理', 'presale', 'manage', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.benchmark.manage'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.rule.manage', '售前配置-规则管理', 'presale', 'manage', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.rule.manage'
);

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'presale.platform.manage', '售前配置-平台管理', 'presale', 'manage', 'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'presale.platform.manage'
);


-- =========================================================================
-- Section 6. 售前平台配置初始数据(基于当前 ai_platform_config 白名单平台)
-- 说明:platform_code 需与 ai_platform_config 一致。此处仅创建占位记录,
--       实际白名单由运营在 UI 上勾选,这里先建表不做硬编码。
-- =========================================================================

-- 无初始 INSERT:白名单由运营在 UI 上维护


-- =========================================================================
-- Section 7. 默认角色权限绑定
--
-- !!! 重要 !!!
-- 以下 role_key 值为占位符,需 DBA 在执行前替换为仓库真实角色标识:
--   __ROLE_SUPER_ADMIN__ → 超级管理员角色的 role_key
--   __ROLE_MANAGER__     → 管理者角色的 role_key(如 admin / manager)
--   __ROLE_SALES__       → 售前运营角色的 role_key(如 sales / ops)
--   __ROLE_VIEWER__      → 只读角色的 role_key(可选,如 viewer / customer_service)
--
-- 授权矩阵:
--   super_admin : 全部 12 项
--   manager     : 全部 12 项(含配置管理)
--   sales       : 6 项核心工作流(list/create/view/generate/edit_content/export)
--   viewer      : 2 项只读(list/view)
--
-- 写法说明:
--   仓库 sys_role_permission 使用整型外键 (role_id, permission_id),
--   因此通过 JOIN sys_role + sys_permission 查出 id 后 INSERT。
--   如果占位符在 sys_role.role_key 中不存在,对应 INSERT 会静默插入 0 行(不报错),
--   所以执行前必须先跑下面的 guard SELECT 核对。
--
-- =========================================================================

-- ---------- 7.0 Guard 检查(执行前手动核对,占位符替换后再跑主体)----------
-- 任意一行 count 返回 0 说明该角色不存在,需要修正占位符或注释掉对应 INSERT
SELECT 'super_admin' AS role_placeholder, COUNT(*) AS exists_count FROM sys_role WHERE role_key = '__ROLE_SUPER_ADMIN__';
SELECT 'manager'     AS role_placeholder, COUNT(*) AS exists_count FROM sys_role WHERE role_key = '__ROLE_MANAGER__';
SELECT 'sales'       AS role_placeholder, COUNT(*) AS exists_count FROM sys_role WHERE role_key = '__ROLE_SALES__';
SELECT 'viewer'      AS role_placeholder, COUNT(*) AS exists_count FROM sys_role WHERE role_key = '__ROLE_VIEWER__';
SELECT 'presale_perms' AS perm_group, COUNT(*) AS exists_count FROM sys_permission WHERE perm_key LIKE 'presale.%';
-- 预期:presale_perms 应为 12;各 role 按实际情况,至少 super_admin 和 manager 应为 1


-- ---------- 7.1 super_admin 全量授权(12 项)----------
INSERT INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_key = '__ROLE_SUPER_ADMIN__'
  AND p.perm_key LIKE 'presale.%'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );


-- ---------- 7.2 manager 全量授权(12 项)----------
INSERT INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_key = '__ROLE_MANAGER__'
  AND p.perm_key LIKE 'presale.%'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );


-- ---------- 7.3 sales 核心工作流授权(6 项)----------
INSERT INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, NOW()
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_key = '__ROLE_SALES__'
  AND p.perm_key IN (
    'presale.report.list',
    'presale.report.create',
    'presale.report.view',
    'presale.report.generate',
    'presale.report.edit_content',
    'presale.report.export'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );


-- ---------- 7.4 viewer 只读授权(2 项,默认注释,按需开启)----------
-- INSERT INTO sys_role_permission (role_id, permission_id, created_at)
-- SELECT r.id, p.id, NOW()
-- FROM sys_role r
-- CROSS JOIN sys_permission p
-- WHERE r.role_key = '__ROLE_VIEWER__'
--   AND p.perm_key IN (
--     'presale.report.list',
--     'presale.report.view'
--   )
--   AND NOT EXISTS (
--       SELECT 1
--       FROM sys_role_permission rp
--       WHERE rp.role_id = r.id
--         AND rp.permission_id = p.id
--   );


-- ---------- 7.5 执行后验证(可选,跑完主体后核对实际生效数量)----------
-- 预期:super_admin/manager 各 12 条,sales 6 条,viewer 0 或 2 条
-- SELECT r.role_key, COUNT(*) AS granted_count
-- FROM sys_role_permission rp
-- JOIN sys_role r ON r.id = rp.role_id
-- JOIN sys_permission p ON p.id = rp.permission_id
-- WHERE p.perm_key LIKE 'presale.%'
-- GROUP BY r.role_key;


SET FOREIGN_KEY_CHECKS = 1;

-- =========================================================================
-- End of V62
-- =========================================================================
