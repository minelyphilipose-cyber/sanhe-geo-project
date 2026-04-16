CREATE TABLE IF NOT EXISTS article_generation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT '项目id',
    article_type VARCHAR(50) NOT NULL COMMENT '文章类型',
    article_angle VARCHAR(50) DEFAULT NULL COMMENT '写作角度',
    generated_title VARCHAR(255) DEFAULT NULL COMMENT '生成标题',
    model_code VARCHAR(50) DEFAULT NULL COMMENT '模型平台编码',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_project_id (project_id),
    KEY idx_project_angle (project_id, article_angle)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='content article generation log';
