# Permission Refactor Restart Rollback Runbook

Use this before the first backend restart that loads `V96__phase42_permission_refactor_foundation.sql`.

## Preconditions

- Do not restart while backend tasks are running.
- Take a database backup or snapshot before restart.
- Confirm the current deployed artifact can be redeployed if rollback is needed.
- Keep this runbook and the generated diff in the release ticket.

## Post-Restart Smoke Checks

Run these immediately after restart:

1. Login succeeds for an internal admin account.
2. `/api/auth/me` or login response returns a non-empty `permissions` list. Use at least one
   non-`super_admin` business-role account and confirm the list contains a real business permission
   code such as `company.read`, `brand.read`, or `project.read`, not only `*`.
3. A changed-permission endpoint works for an authorized user, for example create brand or create project.
4. A denied case still denies, for example `partner_staff` cannot start a project.
5. Login with a partner account and confirm it can see only its own `partner_id` data, not another
   partner's customer/project/account data.
6. Confirm a partner administrator account receives `company.delete` and `brand.delete`, while a
   `partner_staff` account does not receive either delete permission.

If any of the first three fail, treat it as an上线阻塞 issue and roll back.

## Rollback Steps

1. Stop routing traffic to the new backend instance if a load balancer is used.
2. Redeploy the previous backend artifact.
3. Restore the pre-restart database backup if migration `V96` has already been applied.
4. If a full restore is not possible, apply the emergency SQL below only after confirming no new production writes depend on the new tables or columns.

## Rollback Decision Tree

Before choosing emergency SQL, run:

```sql
SELECT COUNT(*) AS recharge_order_count FROM partner_recharge_order;
SELECT COUNT(*) AS discount_history_count FROM partner_discount_history;
```

If either count is non-zero, use database backup restore. Do not use emergency SQL, because dropping
or disabling the new structures would lose business/audit data. Emergency SQL is acceptable only
when both counts are zero and no post-restart writes depend on V96.

## Emergency SQL

```sql
-- Re-activate legacy permissions if V96 marked them deprecated.
UPDATE sys_permission
SET status = 'active'
WHERE perm_key IN (
    'company.write',
    'project.write',
    'project.status.activate',
    'project.status.close',
    'project.status.update',
    'project.flow.update',
    'project.sign_and_deduct',
    'partner.write'
);

-- Remove grants for new granular permissions from role bindings.
DELETE rp
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.perm_key IN (
    'brand.read',
    'company.account.adjust',
    'package.read',
    'package.manage',
    'partner.create',
    'partner.update',
    'partner.status.update',
    'partner.discount.update',
    'partner.account.read',
    'partner.account.recharge.apply',
    'partner.account.recharge.audit',
    'partner.account.adjust',
    'partner.account.txn.read',
    'partner.staff.manage',
    'project.start',
    'project.pause',
    'project.terminate',
    'project.report.read',
    'project.report.export',
    'role.manage',
    'permission.manage',
    'activity_log.read',
    'activity_log.finance.read'
);

-- Mark new permissions inactive rather than deleting rows.
UPDATE sys_permission
SET status = 'inactive'
WHERE perm_key IN (
    'brand.read',
    'company.account.adjust',
    'package.read',
    'package.manage',
    'partner.create',
    'partner.update',
    'partner.status.update',
    'partner.discount.update',
    'partner.account.read',
    'partner.account.recharge.apply',
    'partner.account.recharge.audit',
    'partner.account.adjust',
    'partner.account.txn.read',
    'partner.staff.manage',
    'project.start',
    'project.pause',
    'project.terminate',
    'project.report.read',
    'project.report.export',
    'role.manage',
    'permission.manage',
    'activity_log.read',
    'activity_log.finance.read'
);
```

## Schema Rollback Note

The safest schema rollback is restoring the database backup. Avoid dropping columns/tables in-place
under incident pressure because soft-delete columns, recharge orders, and discount history may already
have accepted writes after restart.

If a DBA approves an in-place schema rollback, drop only after data export:

```sql
-- Only after explicit DBA approval and data export.
DROP TABLE IF EXISTS partner_recharge_order;
DROP TABLE IF EXISTS partner_discount_history;
ALTER TABLE project DROP COLUMN deleted_by, DROP COLUMN deleted_at;
ALTER TABLE brand DROP COLUMN deleted_by, DROP COLUMN deleted_at;
ALTER TABLE company DROP COLUMN deleted_by, DROP COLUMN deleted_at;
ALTER TABLE sys_role DROP COLUMN data_scope;
```

## Unverified Item

Backend startup with the V96 migration is not verified yet because the current backend service is
running tasks and must not be restarted.

Front-end permission-code reconciliation against `sys_permission` is not verified yet. Watch the
first week after restart for UI symptoms such as buttons not appearing, buttons appearing when they
should not, or buttons calling endpoints with mismatched permission codes.
