ALTER TABLE presale_report
    ADD COLUMN user_type VARCHAR(50) NULL COMMENT '目标用户/消费群体' AFTER user_demand;

CREATE TABLE presale_report_version_prompt_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id BIGINT NOT NULL COMMENT '售前报告 ID',
    report_version_id BIGINT NOT NULL COMMENT '售前报告版本 ID',
    source_template_id BIGINT NOT NULL COMMENT '来源全局 Prompt 模板 ID',
    source_prompt_code VARCHAR(50) NOT NULL COMMENT '来源全局 Prompt 编码',
    source_template_version VARCHAR(16) NOT NULL COMMENT '来源模板版本',
    category VARCHAR(20) NOT NULL COMMENT '意图类型',
    business_value VARCHAR(10) NOT NULL COMMENT '业务价值',
    prompt_content TEXT NOT NULL COMMENT '版本级 Prompt 原文模板',
    has_competitor_var TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否包含竞品占位符',
    sort_order_in_version INT NOT NULL COMMENT '版本内排序',
    remark VARCHAR(500) NULL COMMENT '备注',
    is_user_added TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否用户新增,首版固定为 0',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_version_sort (report_version_id, has_competitor_var, sort_order_in_version),
    KEY idx_report_version (report_id, report_version_id)
) COMMENT='售前报告版本级 Prompt 模板快照';
