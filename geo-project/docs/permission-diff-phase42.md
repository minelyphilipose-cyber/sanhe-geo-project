# Permission Diff - Phase 42

This document records the baseline permission diff before the permission refactor.

## Existing Permission Keys

Existing migrations already seed these business permission keys:

```text
brand.create
brand.delete
brand.material.delete
brand.material.upload
brand.statement.lock
brand.update
company.create
company.delete
company.read
company.update
company.write
dispatch.alert.resolve
dispatch.presale.enqueue
dispatch.task.replay.dead_letter
keyword_affix.manage
keyword_group.read
keyword_group.write
partner.read
partner.write
presale.benchmark.manage
presale.platform.manage
presale.prompt.manage
presale.report.create
presale.report.delete
presale.report.download
presale.report.edit_content
presale.report.export
presale.report.freeze
presale.report.generate
presale.report.list
presale.report.manage
presale.report.view
presale.rule.manage
project.create
project.delete
project.flow.update
project.read
project.sign_and_deduct
project.stage.update
project.status.activate
project.status.close
project.status.update
project.update
project.write
question_pool.core.confirm
question_pool.core.delete
report.review
user.manage
```

## Added Permission Keys

```text
brand.read
company.account.adjust
package.read
package.manage
partner.create
partner.update
partner.status.update
partner.discount.update
partner.account.read
partner.account.recharge.apply
partner.account.recharge.audit
partner.account.adjust
partner.account.txn.read
partner.staff.manage
project.start
project.pause
project.terminate
project.report.read
project.report.export
role.manage
permission.manage
activity_log.read
activity_log.finance.read
```

## Deprecated Permission Keys

The following legacy coarse-grained or ambiguous keys are marked `deprecated` instead of being
deleted. They should remain until all services and front-end references have moved to the granular
permissions.

```text
company.write
project.write
project.status.activate
project.status.close
project.status.update
project.flow.update
project.sign_and_deduct
partner.write
```

## Locked Partner Staff Rule

`partner_staff` can create and update draft business data, including customer, brand, and project
drafts. Create is not start: any "create and start" workflow must perform a second `project.start`
permission check before activation and deduction.

## Partner Delete Posture

`partner` is granted `company.delete` and `brand.delete` in V96, so partner administrators can
delete unlinked customer and brand records. `partner_staff` is still not granted these delete
permissions.

## Account Role Binding Rule

Normal-role permissions are DB-authoritative after the `LEGACY_ROLE_PERMS` fallback was removed.
Any code path that creates or changes an account must keep both role fields in sync:

- `sys_user.role` stores the primary role key used by existing user profile and JWT flows.
- `sys_user_role` stores the effective role relation used by permission loading.

Required implementation rule: account creation and role changes must write both `sys_user.role` and
`sys_user_role` in the same transaction. This applies to internal user creation, partner owner
creation, and any future partner self-service staff creation endpoint. A user with only
`sys_user.role` populated but no `sys_user_role` relation will log in with an empty normal-role
permission set.

V100 backfilled historical partner accounts that missed this relation. The migration is a safety net
for legacy data, not a substitute for writing both tables in new code.

## Phase 6 Account Audit Rules

Partner recharge approval is transactional and locks both the recharge order and account row. Phase 6
adds two audit rules:

- `partner_account_txn.recharge_order_id` links approved recharge-order account transactions back to
  the source order. A NULL value means direct finance recharge, while a non-NULL value means the
  transaction came from an approved partner recharge application.
- `partner_recharge_order.expires_at` is enforced at audit time. Pending recharge orders past
  `expires_at` cannot be approved, but finance can still reject them with an explicit reason so the
  partner sees a clear outcome.

Activity logs are also split by permission:

- `activity_log.read` can see all activity logs.
- `activity_log.finance.read` can see only amount-sensitive logs such as partner/company account
  operations, project deduction, and partner discount changes.

## Legacy Permission Retirement

`LEGACY_ROLE_PERMS` was compared against DB-compatible grants in
`permission-grant-diff-report-phase42.md`; compatible mode had an empty diff. In Phase 2 the Java
fallback was cleared for normal roles and now only keeps `super_admin -> *`.

Remaining sequence:

1. Keep `docs/scripts/permission-grant-diff.ps1` in CI so DB-compatible grants stay aligned with the
   historical role contract during the deprecated-permission transition.
2. After all service and front-end references stop calling deprecated keys, change permission loading
   from `status IN ('active', 'deprecated')` back to `status = 'active'`.
3. Remove deprecated grants and clean up deprecated permission rows in a later release.

Changing grants in the database now revokes normal-role permissions, except for the intentional
`super_admin` wildcard fallback.
