CREATE TABLE presale_lexicon_bucket (
    id                       BIGINT       PRIMARY KEY AUTO_INCREMENT,
    bucket_code              VARCHAR(50)  NOT NULL,
    bucket_name              VARCHAR(100) NOT NULL,
    customer_term            VARCHAR(50)  NOT NULL COMMENT '如患者/业主/客户',
    conversion_term          VARCHAR(50)  NOT NULL COMMENT '如到诊/上门/下单',
    default_industry_short   VARCHAR(50)  NULL COMMENT '默认短行业名',
    enabled                  TINYINT(1)   NOT NULL DEFAULT 1,
    source                   VARCHAR(30)  NOT NULL DEFAULT 'SEED',
    config_version           VARCHAR(32)  NOT NULL DEFAULT 'v1',
    remark                   VARCHAR(500) NULL,
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_bucket_version (bucket_code, config_version),
    KEY idx_enabled (enabled, bucket_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售前报表行业词汇 bucket';

CREATE TABLE presale_industry_bucket_mapping (
    id                BIGINT       PRIMARY KEY AUTO_INCREMENT,
    industry          VARCHAR(100) NOT NULL COMMENT '原始行业文本',
    industry_key      VARCHAR(100) NOT NULL COMMENT '保守规范化后的行业 key',
    bucket_code       VARCHAR(50)  NOT NULL,
    industry_short    VARCHAR(50)  NULL,
    approved          TINYINT(1)   NOT NULL DEFAULT 1,
    source            VARCHAR(30)  NOT NULL DEFAULT 'SEED',
    origin_task_id    BIGINT       NULL,
    approved_by       BIGINT       NULL,
    approved_at       DATETIME     NULL,
    config_version    VARCHAR(32)  NOT NULL DEFAULT 'v1',
    remark            VARCHAR(500) NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_industry_key_version (industry_key, config_version),
    KEY idx_bucket (bucket_code),
    KEY idx_approved_key (approved, industry_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售前报表行业到词汇 bucket 映射';

CREATE TABLE presale_industry_bucket_review_task (
    id                  BIGINT       PRIMARY KEY AUTO_INCREMENT,
    industry            VARCHAR(100) NOT NULL COMMENT '原始行业文本',
    industry_key        VARCHAR(100) NOT NULL COMMENT '保守规范化后的行业 key',
    draft_json          JSON         NULL COMMENT '仅允许 bucket_code/industry_short/suggest_new_bucket/reason',
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DRAFTED/APPROVED/REJECTED',
    source              VARCHAR(30)  NOT NULL DEFAULT 'MISSING_MAPPING',
    draft_source        VARCHAR(30)  NULL COMMENT 'LLM_CLASSIFIER/MANUAL',
    reject_reason       VARCHAR(500) NULL,
    fallback_hit_count  INT          NOT NULL DEFAULT 1,
    drafted_by          BIGINT       NULL,
    drafted_at          DATETIME     NULL,
    approved_by         BIGINT       NULL,
    approved_at         DATETIME     NULL,
    rejected_by         BIGINT       NULL,
    rejected_at         DATETIME     NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_industry_key (industry_key),
    KEY idx_status_time (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售前报表行业 bucket 映射审批任务';

INSERT INTO presale_lexicon_bucket
    (bucket_code, bucket_name, customer_term, conversion_term, default_industry_short, enabled, source, config_version, remark)
VALUES
    ('_ALL_', '通用', '客户', '下单', '行业', 1, 'SEED', 'v1', '通用兜底 bucket'),
    ('MEDICAL', '医疗服务', '患者', '到诊', '医疗', 1, 'SEED', 'v1', '医疗、口腔、健康服务'),
    ('HOME_SERVICE', '本地到家服务', '业主', '上门', '本地服务', 1, 'SEED', 'v1', '装修、维修、家政等到家服务'),
    ('EDUCATION', '教育培训', '学员', '报名', '教育培训', 1, 'SEED', 'v1', '教培、课程、训练营'),
    ('PROFESSIONAL', '专业服务', '客户', '咨询', '专业服务', 1, 'SEED', 'v1', '咨询、法务、财税等专业服务'),
    ('RETAIL', '零售消费', '客户', '下单', '零售', 1, 'SEED', 'v1', '零售、电商、消费品牌');

INSERT INTO presale_industry_bucket_mapping
    (industry, industry_key, bucket_code, industry_short, approved, source, config_version, remark)
VALUES
    ('_ALL_', '_all_', '_ALL_', '行业', 1, 'SEED', 'v1', '通用兜底映射'),
    ('口腔医疗', '口腔医疗', 'MEDICAL', '口腔', 1, 'SEED', 'v1', 'MVP 口腔医疗种子映射');

INSERT INTO presale_industry_bucket_mapping
    (industry, industry_key, bucket_code, industry_short, approved, source, config_version, remark)
SELECT l.industry,
       TRIM(l.industry),
       CASE
           WHEN l.customer_term = '客户' AND l.conversion_term IN ('转化', '下单') THEN '_ALL_'
           WHEN l.customer_term = '患者' AND l.conversion_term = '到诊' THEN 'MEDICAL'
           WHEN l.customer_term = '业主' AND l.conversion_term = '上门' THEN 'HOME_SERVICE'
           WHEN l.customer_term IN ('学员', '家长') AND l.conversion_term = '报名' THEN 'EDUCATION'
           WHEN l.customer_term = '客户' AND l.conversion_term = '咨询' THEN 'PROFESSIONAL'
           WHEN l.customer_term = '客户' AND l.conversion_term = '下单' THEN 'RETAIL'
       END,
       l.industry_short,
       1,
       'MIGRATED',
       l.config_version,
       CONCAT('由旧行业词库迁移:id=', l.id)
FROM presale_industry_lexicon l
LEFT JOIN presale_industry_bucket_mapping m
       ON m.industry_key = TRIM(l.industry)
      AND m.config_version = l.config_version
WHERE l.approved = 1
  AND l.industry <> '_ALL_'
  AND m.id IS NULL
  AND (
      (l.customer_term = '客户' AND l.conversion_term IN ('转化', '下单'))
      OR (l.customer_term = '患者' AND l.conversion_term = '到诊')
      OR (l.customer_term = '业主' AND l.conversion_term = '上门')
      OR (l.customer_term IN ('学员', '家长') AND l.conversion_term = '报名')
      OR (l.customer_term = '客户' AND l.conversion_term = '咨询')
      OR (l.customer_term = '客户' AND l.conversion_term = '下单')
  );

INSERT INTO presale_industry_bucket_review_task
    (industry, industry_key, draft_json, status, source, fallback_hit_count)
SELECT l.industry,
       TRIM(l.industry),
       JSON_OBJECT(
           'source_lexicon_id', l.id,
           'legacy_customer', l.customer_term,
           'legacy_conversion', l.conversion_term,
           'industry_short', l.industry_short,
           'reason', '旧词库无法精确匹配已审核 bucket,需人工归类'
       ),
       'PENDING',
       'MIGRATION_UNMATCHED',
       1
FROM presale_industry_lexicon l
LEFT JOIN presale_industry_bucket_mapping m
       ON m.industry_key = TRIM(l.industry)
      AND m.config_version = l.config_version
WHERE l.approved = 1
  AND l.industry <> '_ALL_'
  AND m.id IS NULL;
