ALTER TABLE project ADD COLUMN target_regions TEXT DEFAULT NULL COMMENT '内容生成用目标区域词范围，JSON字符串数组';
ALTER TABLE project ADD COLUMN target_audience VARCHAR(500) DEFAULT NULL COMMENT '目标受众描述';
ALTER TABLE project ADD COLUMN custom_statement TEXT DEFAULT NULL COMMENT '项目定制品牌表述，为空则使用品牌基准表述';
ALTER TABLE project ADD COLUMN content_tone VARCHAR(500) DEFAULT NULL COMMENT '内容调性/写作风格要求';
ALTER TABLE project ADD COLUMN preferred_angles TEXT DEFAULT NULL COMMENT '优先写作角度，JSON字符串数组';
ALTER TABLE project ADD COLUMN extra_forbidden_phrases TEXT DEFAULT NULL COMMENT '项目级补充禁用词，JSON字符串数组';
ALTER TABLE project ADD COLUMN content_note TEXT DEFAULT NULL COMMENT '内容生成补充说明';
