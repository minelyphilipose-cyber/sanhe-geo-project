# V105 Self-Media Account Migration Runbook

## Scope

`V105__self_media_account_abstraction.sql` migrates WeChat MP account data into generic self-media tables:

- `mp_account` -> `self_media_account`
- `mp_material_mapping` -> `self_media_material_mapping`
- `distribution_tasks.mp_account_id` -> `distribution_tasks.self_media_account_id`

The old `mp_account` and `mp_material_mapping` tables are retained for inspection. Application code should stop reading or writing them after Step 2.3.

## Pre-Run Checklist

1. Confirm MySQL version is 8.0.16+; the project baseline is MySQL 8.0.45.
2. Back up the target database before running Flyway:

```bash
mysqldump -h <host> -u <user> -p --single-transaction --routines --triggers <database> > backup_before_v105.sql
```

3. Confirm no application process is writing to `mp_account`, `mp_material_mapping`, or `distribution_tasks` during the migration.
4. Confirm the current latest applied migration is `V104`.

## Important Rollback Note

This migration contains multiple MySQL DDL operations. MySQL DDL is auto-committed and cannot be fully rolled back by Flyway transactions. If the migration fails midway, inspect the schema state manually and restore from the backup when needed.

## Flyway Command

From `geo-project/geo-server`:

```bash
mvn "-Dflyway.url=jdbc:mysql://<host>:3306/<database>" "-Dflyway.user=<user>" "-Dflyway.password=<password>" "-Dflyway.locations=filesystem:src/main/resources/db/migration" org.flywaydb:flyway-maven-plugin:9.22.3:migrate
```

## Post-Run Self-Check SQL

### Legacy Scope Dirty Data

```sql
SELECT COUNT(*) AS dirty_func_info_count
FROM mp_account
WHERE func_info_json IS NOT NULL AND NOT JSON_VALID(func_info_json);
```

Expected: `0`. If non-zero, affected rows were migrated with `self_media_account.scope_json = NULL` and need manual review.

### Account Row Count Alignment

```sql
SELECT
  (SELECT COUNT(*) FROM mp_account) AS mp_account_count,
  (SELECT COUNT(*) FROM self_media_account) AS self_media_account_count;
```

Expected: counts match.

### Material Mapping Row Count Alignment

```sql
SELECT
  (SELECT COUNT(*) FROM mp_material_mapping) AS mp_material_mapping_count,
  (SELECT COUNT(*) FROM self_media_material_mapping) AS self_media_material_mapping_count;
```

Expected: counts match.

### Distribution Task Account ID Migration

```sql
SELECT COUNT(*) AS missing_self_media_account_id_count
FROM distribution_tasks
WHERE target_kind = 'mp_account' AND self_media_account_id IS NULL;
```

Expected: `0`.

### Distribution Task Account Reference Integrity

```sql
SELECT COUNT(*) AS orphan_self_media_account_ref_count
FROM distribution_tasks dt
LEFT JOIN self_media_account sma ON dt.self_media_account_id = sma.id
WHERE dt.target_kind = 'mp_account'
  AND dt.self_media_account_id IS NOT NULL
  AND sma.id IS NULL;
```

Expected: `0`. If non-zero, stop deployment and inspect the account migration before deploying Step 2.3 code.

### Old Column Removal

```sql
SELECT COUNT(*) AS old_mp_account_id_column_count
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'distribution_tasks'
  AND COLUMN_NAME = 'mp_account_id';
```

Expected: `0`.

### Recreated CHECK Constraint

```sql
SELECT CONSTRAINT_NAME, CHECK_CLAUSE
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'chk_distribution_target_consistency';
```

Expected: clause references `self_media_account_id`, not `mp_account_id`, and retains all target branches: `site`, `mp_account`, `brand_official_site`, `brand_geo_site`, `industry_site`, `authority_media`.

### Recreated Indexes

```sql
SHOW INDEX
FROM distribution_tasks
WHERE Key_name IN (
  'uk_distribution_article_target_attempt',
  'idx_distribution_self_media_account_status',
  'uk_distribution_request_id'
);
```

Expected:

- `uk_distribution_article_target_attempt` expression uses `COALESCE(site_id, self_media_account_id, brand_official_site_id, industry_site_id, authority_media_id)`.
- `idx_distribution_self_media_account_status` exists on `(self_media_account_id, status)`.
- `uk_distribution_request_id` remains unchanged from V98.

## CHECK Constraint Smoke Test

Run only in a disposable dry-run database.

```sql
SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO distribution_tasks (
  article_id, project_id, site_id, target_kind, self_media_account_id,
  brand_official_site_id, target_brand_id, industry_site_id, authority_media_id,
  attempt_no, status, integration_method, operator_id
) VALUES (
  900000001, 900000001, NULL, 'mp_account', 900000001,
  NULL, NULL, NULL, NULL,
  1, 'pending', 'wechat_mp', 900000001
);

SELECT COUNT(*) AS valid_mp_account_check_insert_count
FROM distribution_tasks
WHERE article_id = 900000001 AND target_kind = 'mp_account';

-- Expected failure: chk_distribution_target_consistency is violated.
INSERT INTO distribution_tasks (
  article_id, project_id, site_id, target_kind, self_media_account_id,
  brand_official_site_id, target_brand_id, industry_site_id, authority_media_id,
  attempt_no, status, integration_method, operator_id
) VALUES (
  900000002, 900000002, NULL, 'mp_account', NULL,
  NULL, NULL, NULL, NULL,
  1, 'pending', 'wechat_mp', 900000002
);

DELETE FROM distribution_tasks WHERE article_id IN (900000001, 900000002);
SET FOREIGN_KEY_CHECKS = 1;
```

Expected:

- The first insert succeeds and count is `1`.
- The second insert fails with `Check constraint 'chk_distribution_target_consistency' is violated`.
