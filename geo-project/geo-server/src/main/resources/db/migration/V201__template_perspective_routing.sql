CREATE TABLE IF NOT EXISTS content_template_perspective (
  code VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  description VARCHAR(500) NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='article prompt template writing perspective dictionary';

INSERT INTO content_template_perspective (code, name, description, enabled, sort_order)
SELECT seed.code, seed.name, seed.description, 1, seed.sort_order
FROM (
  SELECT 'customer' AS code, '客户视角' AS name, '运营签约客户的品牌自有视角' AS description, 10 AS sort_order
  UNION ALL SELECT 'industry_neutral', '第三方行业中立视角', '第三方行业资讯、知识科普、中立观察视角', 20
  UNION ALL SELECT 'review_recommend', '第三方评测推荐视角', '第三方评测、对比、推荐客户视角', 30
) seed
WHERE NOT EXISTS (
  SELECT 1 FROM content_template_perspective p WHERE p.code = seed.code
);

CREATE TABLE IF NOT EXISTS brand_channel_template_perspective (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  brand_id BIGINT NOT NULL,
  channel_group_code VARCHAR(64) NOT NULL,
  channel_sub_code VARCHAR(64) NOT NULL DEFAULT '_ALL_',
  perspective_code VARCHAR(64) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_brand_channel_template_perspective (brand_id, channel_group_code, channel_sub_code),
  KEY idx_brand_channel_template_perspective_lookup (brand_id, channel_group_code, channel_sub_code, enabled),
  KEY idx_brand_channel_template_perspective_code (perspective_code),
  CONSTRAINT fk_brand_channel_template_perspective_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_brand_channel_template_perspective_code FOREIGN KEY (perspective_code) REFERENCES content_template_perspective(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='brand channel article template perspective routing';

DROP PROCEDURE IF EXISTS v201_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE v201_add_column_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_ddl TEXT
)
BEGIN
  IF (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name
  ) = 0 THEN
    SET @ddl = p_ddl;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL v201_add_column_if_missing(
  'article_prompt_template',
  'perspective_code',
  'ALTER TABLE article_prompt_template ADD COLUMN perspective_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''customer'' COMMENT ''writing perspective code'' AFTER question_scene_code'
);

CALL v201_add_column_if_missing(
  'batch_article_generation_task',
  'perspective_code',
  'ALTER TABLE batch_article_generation_task ADD COLUMN perspective_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''customer'' COMMENT ''frozen writing perspective code'' AFTER prompt_template_version_id'
);

CALL v201_add_column_if_missing(
  'batch_article_generation_task',
  'perspective_matched_scope',
  'ALTER TABLE batch_article_generation_task ADD COLUMN perspective_matched_scope VARCHAR(32) NULL COMMENT ''perspective resolver match scope'' AFTER perspective_code'
);

CALL v201_add_column_if_missing(
  'batch_article_generation_task',
  'perspective_matched_config_id',
  'ALTER TABLE batch_article_generation_task ADD COLUMN perspective_matched_config_id BIGINT UNSIGNED NULL COMMENT ''matched brand perspective config id'' AFTER perspective_matched_scope'
);

CALL v201_add_column_if_missing(
  'article_draft',
  'perspective_code',
  'ALTER TABLE article_draft ADD COLUMN perspective_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT ''customer'' COMMENT ''frozen writing perspective code'' AFTER prompt_template_version_id'
);

DROP PROCEDURE IF EXISTS v201_add_column_if_missing;

ALTER TABLE article_prompt_template
  MODIFY COLUMN perspective_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'customer' COMMENT 'writing perspective code';

ALTER TABLE batch_article_generation_task
  MODIFY COLUMN perspective_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'customer' COMMENT 'frozen writing perspective code';

ALTER TABLE article_draft
  MODIFY COLUMN perspective_code VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'customer' COMMENT 'frozen writing perspective code';

UPDATE article_prompt_template SET perspective_code = 'customer' WHERE perspective_code IS NULL OR perspective_code = '';
UPDATE batch_article_generation_task SET perspective_code = 'customer' WHERE perspective_code IS NULL OR perspective_code = '';
UPDATE article_draft SET perspective_code = 'customer' WHERE perspective_code IS NULL OR perspective_code = '';

DROP PROCEDURE IF EXISTS v201_add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE v201_add_index_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_ddl TEXT
)
BEGIN
  IF (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_index_name
  ) = 0 THEN
    SET @ddl = p_ddl;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS v201_add_fk_if_missing;
DELIMITER $$
CREATE PROCEDURE v201_add_fk_if_missing(
    IN p_table_name VARCHAR(64),
    IN p_constraint_name VARCHAR(64),
    IN p_ddl TEXT
)
BEGIN
  IF (
    SELECT COUNT(1)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND CONSTRAINT_NAME = p_constraint_name
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
  ) = 0 THEN
    SET @ddl = p_ddl;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL v201_add_index_if_missing(
  'article_prompt_template',
  'idx_article_prompt_template_perspective',
  'ALTER TABLE article_prompt_template ADD KEY idx_article_prompt_template_perspective (perspective_code, channel_group_code, channel_sub_code, article_type_code, question_scene_code, status)'
);

CALL v201_add_fk_if_missing(
  'article_prompt_template',
  'fk_article_prompt_template_perspective',
  'ALTER TABLE article_prompt_template ADD CONSTRAINT fk_article_prompt_template_perspective FOREIGN KEY (perspective_code) REFERENCES content_template_perspective(code)'
);

CALL v201_add_index_if_missing(
  'batch_article_generation_task',
  'idx_batch_article_task_perspective',
  'ALTER TABLE batch_article_generation_task ADD KEY idx_batch_article_task_perspective (perspective_code)'
);

CALL v201_add_index_if_missing(
  'batch_article_generation_task',
  'idx_batch_article_task_perspective_config',
  'ALTER TABLE batch_article_generation_task ADD KEY idx_batch_article_task_perspective_config (perspective_matched_config_id)'
);

CALL v201_add_fk_if_missing(
  'batch_article_generation_task',
  'fk_batch_article_task_perspective',
  'ALTER TABLE batch_article_generation_task ADD CONSTRAINT fk_batch_article_task_perspective FOREIGN KEY (perspective_code) REFERENCES content_template_perspective(code)'
);

CALL v201_add_fk_if_missing(
  'batch_article_generation_task',
  'fk_batch_article_task_perspective_config',
  'ALTER TABLE batch_article_generation_task ADD CONSTRAINT fk_batch_article_task_perspective_config FOREIGN KEY (perspective_matched_config_id) REFERENCES brand_channel_template_perspective(id)'
);

CALL v201_add_index_if_missing(
  'article_draft',
  'idx_article_draft_perspective',
  'ALTER TABLE article_draft ADD KEY idx_article_draft_perspective (perspective_code)'
);

CALL v201_add_fk_if_missing(
  'article_draft',
  'fk_article_draft_perspective',
  'ALTER TABLE article_draft ADD CONSTRAINT fk_article_draft_perspective FOREIGN KEY (perspective_code) REFERENCES content_template_perspective(code)'
);

DROP PROCEDURE IF EXISTS v201_add_index_if_missing;
DROP PROCEDURE IF EXISTS v201_add_fk_if_missing;
