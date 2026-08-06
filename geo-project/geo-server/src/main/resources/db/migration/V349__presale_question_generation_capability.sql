ALTER TABLE ai_platform_config
    ADD COLUMN enabled_for_presale_question_generation TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '是否用于售前报告 LLM 问题生成' AFTER enabled_for_presale;
