CREATE TABLE IF NOT EXISTS platform_render_template (
  id BIGINT NOT NULL AUTO_INCREMENT,
  platform_code VARCHAR(32) NOT NULL COMMENT 'wechat_mp/douyin_image_text/etc',
  name VARCHAR(128) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'enabled',
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_platform_render_template_platform_status (platform_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Platform render template';

CREATE TABLE IF NOT EXISTS platform_render_template_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  template_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'generic',
  source_html MEDIUMTEXT NULL,
  template_schema_json JSON NOT NULL,
  sanitized_preview_html MEDIUMTEXT NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_platform_render_template_version (template_id, version_no),
  KEY idx_platform_render_template_version_template (template_id),
  CONSTRAINT fk_platform_render_template_version_template
    FOREIGN KEY (template_id) REFERENCES platform_render_template(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Platform render template version';

CREATE TABLE IF NOT EXISTS article_platform_render (
  id BIGINT NOT NULL AUTO_INCREMENT,
  article_id BIGINT UNSIGNED NOT NULL,
  platform_code VARCHAR(32) NOT NULL,
  template_id BIGINT NULL,
  template_version_id BIGINT NULL,
  annotations_json JSON NULL,
  render_config_json JSON NULL,
  block_snapshot_json JSON NULL,
  rendered_html_snapshot MEDIUMTEXT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_article_platform_render_article_platform (article_id, platform_code),
  KEY idx_article_platform_render_version (template_version_id),
  CONSTRAINT fk_article_platform_render_article
    FOREIGN KEY (article_id) REFERENCES article_draft(id),
  CONSTRAINT fk_article_platform_render_template
    FOREIGN KEY (template_id) REFERENCES platform_render_template(id),
  CONSTRAINT fk_article_platform_render_version
    FOREIGN KEY (template_version_id) REFERENCES platform_render_template_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Article platform render configuration';
