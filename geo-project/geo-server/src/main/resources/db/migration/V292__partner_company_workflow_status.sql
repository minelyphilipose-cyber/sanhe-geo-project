ALTER TABLE company
    ADD COLUMN partner_workflow_status VARCHAR(32) NULL COMMENT '合伙人协作录入状态' AFTER partner_staff_owner_id,
    ADD COLUMN partner_workflow_updated_at DATETIME NULL COMMENT '合伙人协作状态更新时间' AFTER partner_workflow_status;

UPDATE company
SET partner_workflow_status = CASE
        WHEN source_type = 'partner' AND status = 'signed' THEN 'package_bound'
        WHEN source_type = 'partner' THEN 'draft'
        ELSE partner_workflow_status
    END,
    partner_workflow_updated_at = COALESCE(updated_at, created_at, NOW())
WHERE source_type = 'partner'
  AND partner_workflow_status IS NULL;

CREATE INDEX idx_company_partner_workflow
    ON company (partner_id, partner_workflow_status, partner_staff_owner_id);
