CREATE TABLE IF NOT EXISTS project_self_media_schedule_carry_over (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  company_id BIGINT NULL,
  brand_id BIGINT NULL,
  source_batch_id BIGINT UNSIGNED NULL,
  source_month VARCHAR(7) NOT NULL,
  target_month VARCHAR(7) NOT NULL,
  requested_count INT NOT NULL DEFAULT 0,
  carry_over_count INT NOT NULL DEFAULT 0,
  consumed_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  decision_operator_id BIGINT NULL,
  decision_reason VARCHAR(512) NULL,
  capacity_snapshot_json JSON NULL,
  carry_over_plan_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_project_self_media_carry_over_target (project_id, target_month, status, id),
  KEY idx_project_self_media_carry_over_source (project_id, source_month, status, id),
  KEY idx_project_self_media_carry_over_status (status, target_month, id),
  CONSTRAINT fk_project_self_media_carry_over_project FOREIGN KEY (project_id) REFERENCES project(id),
  CONSTRAINT fk_project_self_media_carry_over_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
  CONSTRAINT fk_project_self_media_carry_over_batch FOREIGN KEY (source_batch_id) REFERENCES project_self_media_schedule_batch(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='project self-media late-start carry-over schedule demand';

INSERT INTO sys_permission (perm_key, perm_name, module, action, status)
SELECT 'content.self_media_schedule.late_start_decide',
       'Self Media Schedule Late Start Decide',
       'content',
       'late_start_decide',
       'active'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE perm_key = 'content.self_media_schedule.late_start_decide'
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.perm_key = 'content.self_media_schedule.late_start_decide'
WHERE r.role_key = 'delivery_manager'
  AND p.status = 'active'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
