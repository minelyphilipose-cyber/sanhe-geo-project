CREATE TABLE IF NOT EXISTS brand_offering (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  brand_id BIGINT NOT NULL,
  offering_name VARCHAR(128) NOT NULL COMMENT '产品/服务项目/特色业务项名称',
  offering_aliases_json TEXT NULL COMMENT '简称/别名 JSON 数组',
  target_users VARCHAR(500) NULL COMMENT '目标人群',
  offering_intro TEXT NULL COMMENT '产品/项目介绍',
  qualification_description VARCHAR(500) NULL COMMENT '资质描述',
  remark VARCHAR(500) NULL COMMENT '内部备注',
  status VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
  priority INT NOT NULL DEFAULT 50 COMMENT '优先级，数值越小越优先',
  use_scenarios VARCHAR(500) NULL COMMENT '适用场景',
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at DATETIME NULL,
  KEY idx_brand_offering_brand_status (brand_id, status, deleted_at),
  KEY idx_brand_offering_brand_priority (brand_id, priority, id)
) COMMENT='品牌产品/服务项目/特色业务项';
