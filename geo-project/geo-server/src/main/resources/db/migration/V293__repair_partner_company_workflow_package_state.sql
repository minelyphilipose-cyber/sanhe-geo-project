UPDATE company c
SET c.partner_workflow_status = 'package_requested',
    c.partner_workflow_updated_at = NOW()
WHERE c.source_type = 'partner'
  AND c.partner_workflow_status IN ('package_bound', 'project_entry', 'entry_completed')
  AND NOT EXISTS (
      SELECT 1
      FROM company_package_binding cpb
      WHERE cpb.company_id = c.id
        AND cpb.status = 'active'
        AND cpb.active_flag = 1
  );

UPDATE company c
SET c.partner_workflow_status = 'package_bound',
    c.partner_workflow_updated_at = NOW()
WHERE c.source_type = 'partner'
  AND (c.partner_workflow_status IS NULL OR c.partner_workflow_status IN ('draft', 'package_requested'))
  AND EXISTS (
      SELECT 1
      FROM company_package_binding cpb
      WHERE cpb.company_id = c.id
        AND cpb.status = 'active'
        AND cpb.active_flag = 1
  );
