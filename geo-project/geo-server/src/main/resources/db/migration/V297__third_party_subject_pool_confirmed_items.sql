CREATE TABLE IF NOT EXISTS third_party_subject_pool_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_brand_id BIGINT NOT NULL COMMENT 'publishing/source brand',
  subject_brand_id BIGINT NOT NULL COMMENT 'confirmed content subject brand',
  subject_project_id BIGINT NULL COMMENT 'active project snapshot at confirmation time',
  match_source VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT 'direct/llm/manual',
  matched_industry VARCHAR(128) NULL COMMENT 'matched subject industry label or key',
  coverage_terms_snapshot JSON NULL COMMENT 'source coverage terms at confirmation time',
  confirmed_at DATETIME NOT NULL,
  confirmed_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_third_party_subject_pool_source_subject (source_brand_id, subject_brand_id),
  KEY idx_third_party_subject_pool_source_brand_id (source_brand_id),
  KEY idx_third_party_subject_pool_subject_brand_id (subject_brand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='confirmed third-party subject pool items';
