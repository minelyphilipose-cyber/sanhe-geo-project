# P1.4-c E2E Verification

Date: 2026-04-29
Branch: feature/phase1-multichannel-c1

## Setup

- MockCmsServer: `localhost:9090`
- Dev app: `localhost:8081`
- Case 1 article/site: article `25`, brand official site `2`
- Case 2 article/site: article `56`, brand official site `3`

## Case 1: 200 OK -> submitted

Mock behavior: default `ok`

Quota:

```text
CASE1_QUOTA_BEFORE=0
CASE1_QUOTA_AFTER=1
```

Database result:

```text
id  article_id  project_id  target_kind          brand_official_site_id  attempt_no  status     failure_kind  platform_article_id   locked_until
8   25          7           brand_official_site  2                       1           submitted  NULL          mock_article_eb4a08f4 NULL

id  project_id  quota_month  used_count  monthly_limit
7   7           2026-04      1           20
```

Result: PASS

## Case 2: 401 -> failed/AUTH_EXPIRED

Mock behavior: `auth_expired`

Quota:

```text
CASE2_QUOTA_BEFORE=0
CASE2_QUOTA_AFTER=1
```

Database result:

```text
id  article_id  project_id  target_kind          brand_official_site_id  attempt_no  status  failure_kind  platform_article_id  locked_until
9   56          8           brand_official_site  3                       1           failed  AUTH_EXPIRED  NULL                 NULL

id  project_id  quota_month  used_count  monthly_limit
8   8           2026-04      1           10
```

Result: PASS

## Conclusion

Both core IC-4 paths passed:

- 200 OK finalized `submitting -> submitted`, cleared `locked_until`, and reserved quota +1.
- 401 finalized `submitting -> failed/AUTH_EXPIRED`, cleared `locked_until`, and kept quota +1 without rollback.
