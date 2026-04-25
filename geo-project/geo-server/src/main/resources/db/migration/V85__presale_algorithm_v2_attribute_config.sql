CREATE TABLE industry_core_attribute_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  industry VARCHAR(50) NOT NULL,
  attributes_json JSON NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_industry (industry)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO industry_core_attribute_config(industry, attributes_json, enabled)
VALUES ('_ALL_', '["品牌历史","产品","服务","价格","口碑","创新","规模","影响力"]', 1);
