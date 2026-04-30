# Phase 2A Brand GEO Site Acceptance

Dev mock:

```powershell
python tools/brand-geo-site-mock-server.py
```

`application-dev.yml` points `geo.brand-geo-site.endpoint` to `http://127.0.0.1:18080/api/v1/content`.

PR description must link this document. Before merge, check off every item below.

## Brand API Contract

Brand update keeps PUT full-replacement semantics. The frontend edit form must submit every editable brand field with the current value; omitted fields are treated as overwritten.

For GEO site fields:

- Missing or blank `geoSiteCode` clears the brand GEO site configuration.
- Missing `geoSiteStatus` defaults to `active` when `geoSiteCode` is present.
- Present `geoSiteStatus` with blank `geoSiteCode` returns 400.

## 200 Success

Use `brand.geo_site_code = ok`, `geo_site_status = active`.

- [x] `distribution_tasks.status` becomes `submitted`.
- [x] `distribution_tasks.target_kind = brand_geo_site`.
- [x] `distribution_tasks.integration_method = brand_geo_site`.
- [x] `distribution_tasks.target_brand_id` is written.
- [x] `distribution_tasks.request_payload` is written.
- [x] `distribution_tasks.response_payload` is written.
- [x] `distribution_tasks.platform_article_id = 12345`.
- [x] `distribution_tasks.published_url = https://www.ok.com/{question|knowledge}/detail/12345`.
- [x] `article_draft.status` becomes `published`.
- [x] Monthly quota remains consumed.

## 400 Business Failure

Use `brand.geo_site_code = bad`.

- [x] `distribution_tasks.status` becomes `failed`.
- [x] `distribution_tasks.failure_kind = CLIENT_ERROR`.
- [x] `distribution_tasks.next_retry_at` is null.
- [x] `distribution_tasks.request_payload` is written.
- [x] `distribution_tasks.response_payload` is written.
- [x] `article_draft.status` returns to `approved`.
- [x] Monthly quota is refunded.

Also verify `brand.geo_site_code = biz-fail`:

- [x] HTTP 200 with body `code != 200` becomes `CLIENT_ERROR`.
- [x] It is not retryable and does not set `next_retry_at`.
- [x] Monthly quota is refunded.

## Network Error

Stop the mock server or point endpoint to an unreachable host.

- [x] `distribution_tasks.status` becomes `failed`.
- [x] `distribution_tasks.failure_kind = SERVER_ERROR`.
- [x] `distribution_tasks.next_retry_at` is about five minutes in the future.
- [x] `distribution_tasks.request_payload` is written when the request body was built.
- [x] `distribution_tasks.response_payload` is null.
- [x] `article_draft.status` returns to `approved`.
- [x] Monthly quota is refunded.

## Boundary Cases

Use `brand.geo_site_code = no-id`.

- [x] HTTP 200 with body `code = 200` but missing `data.id` becomes `SERVER_ERROR`.
- [x] Error message is `response data.id missing or invalid`.
- [x] It is retryable and sets `next_retry_at`.
- [x] Monthly quota is refunded.

Other required checks:

- [x] Brand without `geo_site_code` returns 400 before creating a task.
- [x] Brand with `geo_site_status = disabled` returns 400 before creating a task.
- [x] Article not in `approved` or `unpublished` returns 400 before creating a task.
- [x] User without brand access gets 403 before creating a task.
- [x] Quota exhausted returns 400 before creating a task.
