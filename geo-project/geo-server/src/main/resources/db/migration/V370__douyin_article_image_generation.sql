ALTER TABLE brand_material ADD COLUMN source_article_id BIGINT NULL COMMENT '文章专属生成图片，不参与公共随机选图';
ALTER TABLE article_draft ADD COLUMN douyin_image_batch_id BIGINT NULL;

CREATE TABLE douyin_article_image_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    source_version_no INT NOT NULL,
    applied_version_no INT NULL,
    subject_brand_id BIGINT NOT NULL,
    model_config_id BIGINT NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    status VARCHAR(24) NOT NULL,
    run_token VARCHAR(36) NULL,
    image_count INT NOT NULL,
    style_code VARCHAR(64) NOT NULL,
    source_title VARCHAR(255) NOT NULL,
    source_markdown MEDIUMTEXT NOT NULL,
    brand_facts_json MEDIUMTEXT NOT NULL,
    plan_json MEDIUMTEXT NULL,
    material_ids_json TEXT NOT NULL,
    error_message VARCHAR(1000) NULL,
    automatic BIT(1) NOT NULL DEFAULT b'0',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_douyin_image_request (article_id, request_key),
    KEY idx_douyin_image_queue (status, updated_at),
    KEY idx_douyin_image_history (subject_brand_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
