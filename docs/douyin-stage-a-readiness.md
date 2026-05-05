# Douyin Stage A Readiness

Last updated: 2026-05-05

This note evaluates whether the Step 1 / Step 2 self-media abstraction is ready to support Douyin image-text Stage A. Scope is Mock-first development. Real OAuth and real publish calls remain gated by platform credentials and `video.create.bind` permission.

## Current Foundation

| Area | Current State | Douyin Fit | Stage A Action |
| --- | --- | --- | --- |
| Adapter abstraction | `SelfMediaAdapter` exists with `platform()`, `supportsPlatform(...)`, `validate(...)`, `submitToTarget(...)`, `refreshReviewStatus(...)` | Good | Add `DouyinImageTextAdapter` as a Spring bean implementing the same interface. |
| Target context | `TargetContext.SelfMediaTarget` carries account, cover material, image material ids, topic list, visibility, request id and extra json | Mostly ready | Use image material ids + topic list + visibility for Douyin; define `extraJson` keys only for mock/future platform-specific options. |
| Submit result | `SubmitResult` has platform article id, external status, review status, review feedback | Ready | Douyin mock can return `UNDER_REVIEW`/`UNKNOWN` as needed; real Douyin defaults review refresh to `UNKNOWN` unless confirmed otherwise. |
| Review status model | `ReviewStatusResult` uses explicit enum including `NOT_APPLICABLE`, `UNKNOWN`, `UNDER_REVIEW`, `PUBLISHED`, `REJECTED`, `OFFLINE` | Ready | WeChat returns `NOT_APPLICABLE`; Douyin returns `UNKNOWN` for refresh unless a real status source is confirmed. Mock can inject passed/rejected/pending. |
| Account model | `self_media_account` stores platform, platform account id, encrypted tokens, token expiry, scope json, extra json | Ready | Add Douyin accounts with `platform='douyin'`; store `open_id`/union data in `extra_json`; keep tokens encrypted. |
| Material mapping | `self_media_material_mapping` supports platform media id/url/type and self-media account FK | Ready | Use `media_type='douyin_image'` for uploaded images; avoid mixing with WeChat `thumb/content_image`. |
| Distribution task | `distribution_tasks.self_media_account_id` and `target_kind='mp_account'` generic self-media target | Ready with historical naming | Continue using `target_kind='mp_account'`; join `self_media_account.platform` to distinguish WeChat vs Douyin. |
| Frontend account API | `/self-media-accounts/...` paths and `SelfMediaAccount` type are in place | Ready | Add Douyin capability/auth UI alongside WeChat under self-media account namespace. |
| Mock mode precedent | WeChat client already supports mock mode | Useful but separate | Add Douyin mock client with explicit fault object rather than reusing WeChat mock flags. |

## Interface Fit Details

### `SelfMediaAdapter`

The current interface is sufficient for Stage A:

- `platform()` routes `douyin` independently from `wechat_mp`.
- `validate(...)` can enforce Douyin-specific image count, title/text length, account status, and required image material ids before task creation.
- `submitToTarget(...)` can run the full mock publish flow and return `SubmitResult` with request/response payloads.
- `refreshReviewStatus(...)` has an explicit return enum, so Douyin can return `UNKNOWN` without overloading `Optional.empty()`.

No interface signature change is required before A.1.

### `TargetContext.SelfMediaTarget`

Required Douyin Stage A fields can be mapped without schema changes:

| Douyin Need | Existing Field | Stage A Usage |
| --- | --- | --- |
| Account | `account` | Must be `platform='douyin'`. |
| Image array | `imageMaterialIds` | Required for Douyin image-text; Stage A can require 1-9 ids pending product confirmation. |
| Cover image | `coverMaterialId` | Optional alias for first image; for Douyin first image should be treated as cover if product confirms. |
| Topic list | `hashtags` | Optional; default empty until product confirms hashtag behavior. |
| Visibility/private state | `privateStatus` | Optional; default product decision pending. |
| Download permission | `downloadType` | Stage A暂不使用，保留给产品确认是否允许下载后接入。 |
| Idempotency | `requestId` | Required for retries and duplicate submit prevention. |
| Platform extensions | `platformOptions` | Reserved for future Douyin-only fields; Stage A仅在Mock故障或临时实验参数确有需要时使用。 |

### `SubmitResult` and Review State

Stage A mock should populate:

- `platformArticleId`: mock Douyin item id.
- `externalStatus`: raw mock platform status such as `pending`, `published`, `rejected`.
- `reviewStatus`: normalized enum storage value via existing helper.
- `reviewFeedback`: rejection reason for mock rejected cases.

Real Douyin refresh behavior is set to `UNKNOWN` unless official documentation or backend webhook confirms a queryable review state.

## Stage A Implementation Readiness

| Step | Readiness | Notes |
| --- | --- | --- |
| A.1 配置/迁移 | Ready | Add namespaced config such as `geo.douyin.feature.image-text.enabled`, mock enabled flag, client key/secret placeholders. Migration only if extra state is needed. |
| A.2 DouyinClient Mock | Ready | Implement mock OAuth, token, media upload, image-text create, and fault injection. |
| A.3 OAuth | Ready for mock; real blocked | Mock can issue code/open_id/tokens. Real OAuth waits for app registration and credentials. |
| A.4 Token | Ready | Reuse account token fields and encryption pattern; add refresh lock strategy similar to WeChat. |
| A.5 Media | Ready | Use `self_media_material_mapping`; implement image preprocessing decisions only after product confirms minimum quality bar. |
| A.6 Adapter | Ready | Add `DouyinImageTextAdapter`; no base interface changes expected. |
| A.7 E2E Mock | Ready | Frontend can add Douyin option using same self-media account list and distribution API shape. |
| A.8/A.9 Real联调 | Blocked by operations | Requires `client_key/client_secret` and `video.create.bind` scope. |

## Known Gaps Before Coding

| Gap | Blocking Stage | Proposed Handling |
| --- | --- | --- |
| Product quality bar for Stage A content images | A.6/A.7 UX polish | Keep minimum mock flow; mark UI experimental and whitelist. |
| Exact Douyin image count/size/text limits | Real publish | Use config-driven limits and update after official confirmation. |
| Real review callback/query source | Real status refresh | Return `UNKNOWN` in real adapter until confirmed. |
| Daily quota number | Pre-submit quota soft check | Read optional `extra_json.daily_publish_quota`; skip hard local blocking when absent. |
| Sandboxing availability | Real联调 | If no sandbox, rely on mock fault coverage and a single real test account. |

## Recommended Stage A Coding Guardrails

1. Keep each A.x step as a separate commit and review unit.
2. Do not add real OpenAPI behavior before the mock client path is green.
3. Keep feature availability behind `geo.douyin.feature.image-text.enabled` plus a whitelist decision before UI exposure.
4. Store unknown platform responses in `response_payload` and map unknown error codes to a safe failed state with raw details preserved.
5. Do not change WeChat behavior while adding Douyin; route strictly by `self_media_account.platform`.
