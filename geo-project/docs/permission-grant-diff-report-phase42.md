# Permission Grant Diff Report - Phase 42

Command:

```powershell
powershell -ExecutionPolicy Bypass -File geo-project\docs\scripts\permission-grant-diff.ps1 -FailOnCompatibleDiff
```

## Result

Compatible mode has no differences. This means the transition model of DB grants plus deprecated
bindings covers `LEGACY_ROLE_PERMS`.

Active-only mode shows only expected deprecated coarse permissions.

| Role | Active-only legacy extras | Compatible diff |
|---|---|---|
| super_admin | none | none |
| manager | `company.write`, `partner.write`, `project.status.activate`, `project.status.close`, `project.write` | none |
| delivery_manager | `company.write`, `project.status.activate`, `project.status.close`, `project.write` | none |
| operator | `company.write`, `project.write` | none |
| sales | none | none |
| partner | `company.write`, `project.write` | none |
| partner_staff | `company.write` | none |
| partner_viewer | none | none |

## Interpretation

The database can become the sole authority after the legacy fallback is retired. Until then,
`LEGACY_ROLE_PERMS` can still grant deprecated coarse permissions regardless of database status.

Phase 2 update: Java fallback has been retired for normal roles and now only keeps `super_admin -> *`.
The diff script remains in CI to protect the DB-compatible grant contract until deprecated permission
keys are fully removed.

The remaining retirement path is recorded in `permission-diff-phase42.md`.
