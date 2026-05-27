ALTER TABLE project ADD COLUMN core_keywords VARCHAR(200) DEFAULT NULL COMMENT '内容策略核心关键词，逗号分隔' AFTER target_regions;
