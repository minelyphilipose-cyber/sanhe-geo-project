-- ============================================================
-- V23: brand profile extension + material assets + versioning
-- ============================================================

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand'
      AND column_name = 'business_intro'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN business_intro TEXT NULL AFTER description',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand'
      AND column_name = 'business_standard_statement'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN business_standard_statement TEXT NULL AFTER standard_brand_statement',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand'
      AND column_name = 'official_account'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN official_account VARCHAR(128) NULL AFTER website',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand'
      AND column_name = 'video_account'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN video_account VARCHAR(128) NULL AFTER official_account',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

SET @col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'brand'
      AND column_name = 'douyin_account'
);
SET @ddl_sql := IF(
    @col_exists = 0,
    'ALTER TABLE brand ADD COLUMN douyin_account VARCHAR(128) NULL AFTER video_account',
    'SELECT 1'
);
PREPARE ddl_stmt FROM @ddl_sql;
EXECUTE ddl_stmt;
DEALLOCATE PREPARE ddl_stmt;

CREATE TABLE IF NOT EXISTS brand_material (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    brand_id     BIGINT NOT NULL,
    category     VARCHAR(32) NOT NULL COMMENT 'brand_image|case|qualification|other',
    file_name    VARCHAR(255) NOT NULL,
    file_type    VARCHAR(64) NULL,
    file_url     VARCHAR(500) NOT NULL,
    object_key   VARCHAR(255) NOT NULL,
    file_size    BIGINT NULL,
    created_by   BIGINT NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_brand_material_brand_id (brand_id),
    KEY idx_brand_material_category (category),
    CONSTRAINT fk_brand_material_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
    CONSTRAINT fk_brand_material_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='brand assets';

CREATE TABLE IF NOT EXISTS brand_profile_version (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    brand_id       BIGINT NOT NULL,
    version_no     INT NOT NULL,
    snapshot_json  LONGTEXT NOT NULL,
    change_reason  VARCHAR(255) NULL,
    created_by     BIGINT NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_brand_profile_version (brand_id, version_no),
    KEY idx_brand_profile_version_brand_id (brand_id),
    KEY idx_brand_profile_version_created_at (created_at),
    CONSTRAINT fk_brand_profile_version_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
    CONSTRAINT fk_brand_profile_version_creator FOREIGN KEY (created_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='brand profile history versions';

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'brand_material_category', 'brand_image', '品牌图', 10
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'brand_material_category' AND dict_key = 'brand_image');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'brand_material_category', 'case', '案例', 20
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'brand_material_category' AND dict_key = 'case');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'brand_material_category', 'qualification', '资质', 30
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'brand_material_category' AND dict_key = 'qualification');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'brand_material_category', 'other', '其他', 40
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'brand_material_category' AND dict_key = 'other');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'activity_action', 'brand.material.upload', '上传品牌素材', 140
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'brand.material.upload');

INSERT INTO sys_dict_item (dict_type, dict_key, dict_value, sort_order)
SELECT 'activity_action', 'brand.material.delete', '删除品牌素材', 150
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type = 'activity_action' AND dict_key = 'brand.material.delete');
