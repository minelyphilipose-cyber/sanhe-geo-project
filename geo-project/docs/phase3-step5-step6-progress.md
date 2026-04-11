# Phase3 Step5-6 Progress

## Entering Step5-6: Cumulative Completed
- Step1-4 baseline completed (auth/rbac/partner isolation/company-brand-project CRUD and delete protections).

## Newly Completed in Step5-6
- Added semi-strict project status transition policy and stage boundary rules in backend service.
- Added `activity_log` backend module (entity/mapper/service/controller).
- Added operation log writes for company/brand/project create/update/delete and project status/stage updates.
- Added admin activity log query API: `GET /api/admin/activity-logs`.
- Added frontend activity log page: `/admin/activity-logs`.
- Added frontend project detail UX constraints:
  - Illegal target status options are disabled by current status.
  - Stage selector is disabled when current selected status is archived.
  - Draft status only allows pending_start / collecting_materials stage options.

## Remaining / Open
- Backend compile/start verification not executed here (local env has no `mvn`).
- End-to-end API smoke with real DB and role accounts to be executed in local runtime.

## Prerequisites for Next Step
- Run Flyway migrations to latest version in local DB.
- Start geo-server and validate APIs and permission interception with manager/super_admin/partner accounts.

## Risk and Rollback
- Risk: If old DB contains inconsistent project status data, transition checks may block historical edits.
- Risk: If role-permission backfill is incomplete, `/api/admin/activity-logs` may return 403.
- Rollback: Can disable transition checks in `ProjectService` methods and keep table constraints unchanged.
- Rollback: Can remove activity log route/menu while keeping backend log writes (non-blocking).
