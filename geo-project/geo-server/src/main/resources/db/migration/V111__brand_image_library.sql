CREATE TABLE IF NOT EXISTS brand_image_folder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    brand_id BIGINT NOT NULL,
    folder_name VARCHAR(128) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|disabled',
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_brand_image_folder_name (brand_id, folder_name),
    KEY idx_brand_image_folder_brand_status (brand_id, status),
    CONSTRAINT fk_brand_image_folder_brand FOREIGN KEY (brand_id) REFERENCES brand(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='brand image library folders';

CREATE TABLE IF NOT EXISTS brand_image_folder_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    folder_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_brand_image_folder_project (folder_id, project_id),
    KEY idx_brand_image_folder_project_project (project_id),
    CONSTRAINT fk_brand_image_folder_project_folder FOREIGN KEY (folder_id) REFERENCES brand_image_folder(id) ON DELETE CASCADE,
    CONSTRAINT fk_brand_image_folder_project_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='brand image folder project relation';

CREATE TABLE IF NOT EXISTS brand_image_folder_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    folder_id BIGINT NOT NULL,
    tag_name VARCHAR(10) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_brand_image_folder_tag (folder_id, tag_name),
    KEY idx_brand_image_folder_tag_name (tag_name),
    CONSTRAINT fk_brand_image_folder_tag_folder FOREIGN KEY (folder_id) REFERENCES brand_image_folder(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='brand image folder tags';

ALTER TABLE brand_material
    ADD COLUMN folder_id BIGINT NULL AFTER brand_id,
    ADD KEY idx_brand_material_folder (folder_id),
    ADD CONSTRAINT fk_brand_material_folder FOREIGN KEY (folder_id) REFERENCES brand_image_folder(id) ON DELETE SET NULL;

INSERT INTO brand_image_folder (brand_id, folder_name, description, status, is_default, created_by)
SELECT b.id, '默认图库', '历史品牌素材自动归档', 'active', 1, 0
FROM brand b
WHERE b.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM brand_image_folder f
      WHERE f.brand_id = b.id AND f.is_default = 1
  );

UPDATE brand_material m
JOIN brand_image_folder f ON f.brand_id = m.brand_id AND f.is_default = 1
SET m.folder_id = f.id
WHERE m.folder_id IS NULL;
