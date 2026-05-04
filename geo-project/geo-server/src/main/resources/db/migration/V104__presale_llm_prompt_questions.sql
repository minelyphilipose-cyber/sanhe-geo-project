ALTER TABLE presale_report_version_prompt_template
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'template' COMMENT 'template/llm' AFTER source_template_version,
    MODIFY COLUMN source_template_id BIGINT NULL COMMENT '来源全局 Prompt 模板 ID,LLM 来源为空';
