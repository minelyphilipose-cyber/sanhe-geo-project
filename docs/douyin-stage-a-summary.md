# Douyin Stage A Completion Summary

Last updated: 2026-05-05

Stage A completed the Douyin image-text mock integration path from configuration through frontend submission and review-status refresh. Real OpenAPI integration remains blocked by external Douyin Open Platform credentials, scope approval, callback-domain configuration, and a real test account.

## 1. Completed Scope

| Commit | Message | Purpose |
| --- | --- | --- |
| `e2ef0e59` | `feat: add Douyin open platform configuration properties (Stage A.1)` | Added Douyin client/open-platform/feature configuration properties and structured mock fault configuration. |
| `da208289` | `docs: add Douyin client interface design (Stage A.2)` | Captured DouyinClient method signatures, DTO fields, error handling, and mock/real separation design. |
| `1bf9bb82` | `feat: add Douyin client interface and mock implementation (Stage A.2)` | Added `DouyinClient`, DTOs, typed exceptions, error mapping, and `MockDouyinClient` with fault injection. |
| `f8887ef2` | `fix: filter by platform when looking up self-media account on WeChat OAuth save` | Fixed WeChat OAuth account lookup to match the new `(platform, platform_account_id)` uniqueness model. |
| `84c82840` | `feat: add Douyin OAuth authorization service and callback (Stage A.3)` | Added mock-capable Douyin OAuth URL generation, callback handling, Redis state, token storage, and account upsert. |
| `1cf9b510` | `feat: add Douyin token service with cache, lock and refresh (Stage A.4)` | Added Douyin token cache, DB fallback, refresh lock, refresh-token rotation handling, and expiry behavior. |
| `92827816` | `feat: add Douyin media service with image upload and dedup (Stage A.5)` | Added image upload service using MinIO `objectKey`, SHA-256 dedup, mapping table reuse, lock, and token retry. |
| `d1352ebc` | `fix: persist review status fields on distribution task (Step 2 follow-up)` | Persisted adapter `externalStatus`, `reviewStatus`, and `reviewFeedback` onto `DistributionTask`. |
| `69451ca0` | `feat: add Douyin image-text adapter (Stage A.6)` | Added `DouyinImageTextAdapter` with validation, media upload orchestration, create-image-text call, and mock review parsing. |
| `c78eb32a` | `feat: add Douyin distribution backend integration (Stage A.7)` | Extended backend distribution request, refresh-review-status endpoint, Douyin capability endpoint, and generic self-media routing. |
| `0f85e7b2` | `fix: add migration for distribution task review status columns (Step 2 follow-up amend)` | Added missing V106 migration for `external_status`, `review_status`, and `review_feedback`. |
| `e5e4da61` | `fix: list self-media accounts across all platforms (Stage A.7 backend follow-up)` | Listed all self-media platforms for a brand and sorted by `platform ASC, updated_at DESC`. |
| `4ea8edbc` | `feat: add Douyin image-text frontend integration (Stage A.7)` | Added frontend Douyin capability/auth UI, account/image/text form, submission, review-status display, and refresh action. |

### Functional Boundary

Stage A supports a full mock loop:

- Douyin feature and mock mode configuration.
- Mock OAuth account authorization and account persistence with `platform='douyin'`.
- Token caching/refresh behavior using the mock client.
- Brand material image upload through MinIO `objectKey`.
- Mock create-image-text submission.
- Distribution task persistence with external/review status fields.
- Frontend entry, account selection, image selection/reordering, text input, submit, and review refresh.

Stage A does not include:

- `RealDouyinClient` HTTP implementation.
- Real OAuth verification against Douyin.
- Real multipart image upload.
- Real `create_image_text` publishing.
- Official review-status polling or webhook integration.
- Withdrawal/delete, quota soft-limit, hashtag/at-user/POI/micro-app fields, or Stage B content generation.

## 2. Mock Loop Re-run Guide

### Required Configuration

Use a development profile with:

```yaml
geo:
  douyin:
    client:
      mode: mock
      fault:
        upload-failed: false
        create-failed: false
        review-outcome: passed
        token-expired: false
        rate-limit: false
        permission-denied: false
    feature:
      image-text:
        enabled: true
```

`DOUYIN_CLIENT_KEY` and `DOUYIN_CLIENT_SECRET` can be dummy non-empty values in mock mode. The backend still requires them while building the token exchange request.

### Fault Settings

| Fault | Values / Type | Effect |
| --- | --- | --- |
| `review-outcome` | `passed`, `pending`, `rejected` | Mock create succeeds and writes `_mock_review_outcome`; refresh maps it to `published`, `under_review`, or `rejected`. |
| `upload-failed` | boolean | `uploadImage` throws a validation-style client exception. |
| `create-failed` | boolean | `createImageText` throws a generic client exception. |
| `token-expired` | boolean | Access-token protected calls throw auth-token errors. |
| `rate-limit` | boolean | `createImageText` throws a rate-limit exception. |
| `permission-denied` | boolean | `createImageText` throws a permission exception. |

### End-to-end Steps

1. Start backend with mock mode and `geo.douyin.feature.image-text.enabled=true`.
2. Start frontend and open `ContentExecution`.
3. Use the Douyin authorization action. In mock mode, complete the callback with the generated state and a mock code.
4. Confirm a `self_media_account` row exists with `platform='douyin'` and the target brand.
5. Choose a Douyin account, select 1-30 JPG/PNG brand materials, optionally enter text, and submit.
6. Confirm a `distribution_task` is created with `target_kind='mp_account'`, `self_media_account_id`, `platform_article_id`, `external_status='accepted'`, and a review status.
7. Use the refresh-review-status action. In mock mode, expected status mapping is:
   - `passed` -> `published`
   - `pending` -> `under_review`
   - `rejected` -> `rejected`

Stage A manual verification ran the `passed` branch end to end. `pending` and `rejected` are covered by backend/unit tests but should be manually exercised before broader internal rollout.

## 3. A.8 / A.9 Prerequisites

### Operations And External Dependencies

- Douyin Open Platform enterprise verification is complete.
- Douyin application is created and approved.
- `client_key` and `client_secret` are issued and stored outside git, then injected into runtime configuration.
- Production, staging, and development callback URLs are configured in Douyin Open Platform and exactly match `geo.douyin.open-platform.auth-callback-url`.
- `video.create.bind` scope is approved.
- At least one internal Douyin test account is available for authorization and publishing tests.
- 3-5 JPG/PNG test images are available in brand materials and each file is within the 20MB Stage A limit.
- Privacy policy, user agreement, ICP/HTTPS domain, and any industry-category requirements are confirmed.

### Runtime Configuration Checklist

Do not commit secrets. The real environment must provide:

```yaml
geo:
  douyin:
    client:
      mode: real
    open-platform:
      client-key: ${DOUYIN_CLIENT_KEY}
      client-secret: ${DOUYIN_CLIENT_SECRET}
      auth-callback-url: https://<real-api-domain>/api/douyin/open-platform/auth/callback
      frontend-callback-url: https://<real-app-domain>/admin/content/execution
      required-scopes:
        - video.create.bind
    feature:
      image-text:
        enabled: true
```

Confirm the deployment mechanism for these values before A.8 starts, such as CI/CD secrets, Kubernetes Secret/ConfigMap, environment variables, or a managed configuration service.

## 4. A.8 / A.9 Startup Order

### A.8 Real OAuth

1. Add `RealDouyinClient` HTTP implementation behind `geo.douyin.client.mode=real`.
2. Keep the existing `DouyinAuthorizationService` and callback endpoint.
3. Run real authorization using the issued `client_key/client_secret`.
4. Verify callback domain strict matching, returned `open_id`, scope string, token encryption, and account upsert.
5. Expand error-code mapping based on real responses.

### A.9 Real Publish

1. Implement real image upload HTTP behavior, including multipart/form-data details and timeout behavior.
2. Implement real create-image-text HTTP behavior.
3. Publish one controlled image-text item with the internal test account.
4. Verify platform response fields, error messages, frequency limits, and whether any review-status query or event notification is available.

Expected integration findings include DTO field naming differences, multipart upload details, HTTP timeout tuning, rate-limit behavior, and error codes beyond the public-document subset.

## 5. Known Follow-ups

- `SelfMediaDistributeRequest.privateStatus` and `downloadType` are currently strings parsed in the controller. A later DTO cleanup should make them `Integer` and let frontend typing enforce numeric values.
- `DistributionTask.externalStatus / reviewStatus / reviewFeedback` required V106 after Java fields were already added. Future entity-field additions must include the migration in the same review unit.
- Mock `pending` and `rejected` branches are unit-tested but were not manually run end to end during Stage A.
- WeChat OAuth and self-media authorization paths should receive a consistency review around `requireBrandWithAccess` after real Douyin OAuth work starts.
- `hashtags / atUsers / poiId / microApp / microAppId` are intentionally not sent in Stage A. Product should decide whether Stage B or A.10 needs any of them.
- Local daily quota soft checks are not implemented until Douyin per-account publish quota is confirmed.

## 6. Stage A Workflow Lessons

- Entity fields and SQL migrations must move together. V106 was a necessary repair because Java fields and write logic landed before the real schema columns.
- Schema changes, backend behavior changes, and frontend UI changes must remain separate commits. The A.7 Step 2 split repaired an earlier mixed commit and should remain the model for future review units.
- Mock fault priority and `_mock_review_outcome` are the key test hooks that allow OAuth, token refresh, upload failure, create failure, rate-limit, permission, and review-state flows to be exercised before real platform credentials are available.
