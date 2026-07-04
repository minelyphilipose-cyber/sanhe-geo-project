UPDATE company c
JOIN (
    SELECT psr.company_id,
           psr.assigned_internal_owner_id
    FROM project_start_request psr
    JOIN (
        SELECT company_id,
               MAX(id) AS latest_request_id
        FROM project_start_request
        WHERE status = 'approved'
          AND assigned_internal_owner_id IS NOT NULL
        GROUP BY company_id
    ) latest ON latest.latest_request_id = psr.id
    JOIN sys_user assigned_owner
      ON assigned_owner.id = psr.assigned_internal_owner_id
     AND assigned_owner.role = 'operator'
     AND assigned_owner.is_active = 1
) resolved ON resolved.company_id = c.id
LEFT JOIN sys_user current_owner ON current_owner.id = c.owner_id
SET c.owner_id = resolved.assigned_internal_owner_id,
    c.updated_at = NOW()
WHERE c.owner_type = 'partner'
  AND (
      c.owner_id IS NULL
      OR current_owner.id IS NULL
      OR current_owner.role <> 'operator'
      OR current_owner.is_active <> 1
  );
