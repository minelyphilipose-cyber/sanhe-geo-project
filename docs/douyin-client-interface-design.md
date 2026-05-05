# Douyin Client Interface Design

Last updated: 2026-05-05  
Scope: Stage A.2 design only. No code is introduced by this document.

Official documentation reviewed on 2026-05-05:

- [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token)
- [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token)
- [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload)
- [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text)
- [名词解释](https://partner.open-douyin.com/docs/resource/zh-CN/local-life/introduction/glossary)

## DouyinClient Interface Methods

Suggested package: `com.huanjing.geo.module.content.douyin.client`

```java
public interface DouyinClient {

    /**
     * Exchange an OAuth authorization code for a user access token.
     *
     * Official API:
     * POST https://open.douyin.com/oauth/access_token/
     *
     * Docs reviewed: 2026-05-05
     * https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token
     *
     * Notes:
     * - Content-Type must be application/x-www-form-urlencoded.
     * - grant_type is fixed to authorization_code.
     * - access_token and refresh_token should be stored server-side.
     */
    DouyinTokenResponse exchangeCodeForToken(DouyinCodeTokenRequest request);

    /**
     * Refresh an expired or near-expired user access token using refresh_token.
     *
     * Official API:
     * POST https://open.douyin.com/oauth/refresh_token/
     *
     * Docs reviewed: 2026-05-05
     * https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token
     *
     * Notes:
     * - Official request fields do not include open_id.
     * - open_id can be kept in the caller/account context for logging and account matching,
     *   but should not be sent to this OpenAPI unless official docs change.
     */
    DouyinTokenResponse refreshAccessToken(DouyinRefreshAccessTokenRequest request);

    /**
     * Upload one image and return Douyin image metadata.
     *
     * Official API:
     * POST https://open.douyin.com/api/douyin/v1/video/upload_image/?open_id={open_id}
     *
     * Docs reviewed: 2026-05-05
     * https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload
     *
     * Notes:
     * - Content-Type must be multipart/form-data.
     * - access-token is passed in request header.
     * - open_id is passed as query parameter.
     */
    DouyinImageUploadResponse uploadImage(DouyinImageUploadRequest request);

    /**
     * Create a Douyin image-text post with previously uploaded image ids.
     *
     * Official API:
     * POST https://open.douyin.com/api/douyin/v1/video/create_image_text/?open_id={open_id}
     *
     * Docs reviewed: 2026-05-05
     * https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text
     *
     * Notes:
     * - Content-Type must be application/json.
     * - access-token is passed in request header.
     * - open_id is passed as query parameter.
     * - Published image-text enters Douyin review and is visible only to the author during review.
     */
    DouyinCreateImageTextResponse createImageText(DouyinCreateImageTextRequest request);
}
```

## Request / Response Data Structures

### `DouyinCodeTokenRequest`

Represents body fields for `POST /oauth/access_token/`.

| Java Field | Official Field | Required | Type | Source |
| --- | --- | --- | --- | --- |
| `clientKey` | `client_key` | Yes | String | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), reviewed 2026-05-05 |
| `clientSecret` | `client_secret` | Yes | String | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), reviewed 2026-05-05 |
| `code` | `code` | Yes | String | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), reviewed 2026-05-05 |
| `grantType` | `grant_type` | Yes | String, fixed `authorization_code` | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), reviewed 2026-05-05 |

### `DouyinRefreshAccessTokenRequest`

Represents body fields for `POST /oauth/refresh_token/`.

| Java Field | Official Field | Required | Type | Source |
| --- | --- | --- | --- | --- |
| `clientKey` | `client_key` | Yes | String | [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| `grantType` | `grant_type` | Yes | String, fixed `refresh_token` | [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| `refreshToken` | `refresh_token` | Yes | String | [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |

Open question: the earlier planning note mentioned `refreshAccessToken(String refreshToken, String openId)`. The official refresh access token API does not accept `open_id`; recommended design is to omit it from the client request DTO and keep `open_id` at the service/account layer for logging and persistence correlation.

### `DouyinTokenResponse`

Used by both `exchangeCodeForToken(...)` and `refreshAccessToken(...)`. Some fields are present in both official examples; types are normalized to Java types based on documented examples.

| Java Field | Official Field | Required | Type | Source |
| --- | --- | --- | --- | --- |
| `accessToken` | `data.access_token` | On success | String | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| `refreshToken` | `data.refresh_token` | On success | String | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| `openId` | `data.open_id` | On success | String | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| `expiresIn` | `data.expires_in` | On success | Long | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| `refreshExpiresIn` | `data.refresh_expires_in` | On success | Long | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| `scope` | `data.scope` | On success | String | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| `errorCode` | `data.error_code` | Yes | Long | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| `description` | `data.description` | No | String | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| `message` | `message` | Yes | String | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| `logId` | `data.log_id` | No | String | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), reviewed 2026-05-05 |

Type note: official examples for `expires_in` vary between number and string across token APIs. Implementation should deserialize leniently into `Long`.

### `DouyinImageUploadRequest`

Represents headers, query, and multipart body for `POST /api/douyin/v1/video/upload_image/`.

| Java Field | Official Field | Required | Type | Source |
| --- | --- | --- | --- | --- |
| `accessToken` | Header `access-token` | Yes | String | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `openId` | Query `open_id` | Yes | String | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `imageBytes` | Body part `image` | Yes | Binary | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `filename` | Multipart filename for `image` | Yes for our client | String | Official docs only show `--form 'image=@"/path/to/file"'`; exact filename behavior需真实联调阶段补充. [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `contentType` | Multipart content type | Optional for our client | String | Official docs require `multipart/form-data`; exact per-file MIME handling需真实联调阶段补充. [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |

### `DouyinImageUploadResponse`

| Java Field | Official Field | Required | Type | Source |
| --- | --- | --- | --- | --- |
| `imageId` | `data.image.image_id` | On success | String | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `width` | `data.image.width` | On success | Integer | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `height` | `data.image.height` | On success | Integer | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `errorCode` | `data.error_code` | Yes | Long | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `description` | `data.description` | No | String | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `extraErrorCode` | `extra.error_code` | No | Long | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `extraDescription` | `extra.description` | No | String | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `logId` | `extra.logid` | No | String | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `now` | `extra.now` | No | Long | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `subErrorCode` | `extra.sub_error_code` | No | Long | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| `subDescription` | `extra.sub_description` | No | String | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |

### `DouyinCreateImageTextRequest`

Represents headers, query, and JSON body for `POST /api/douyin/v1/video/create_image_text/`.

| Java Field | Official Field | Required | Type | Source |
| --- | --- | --- | --- | --- |
| `accessToken` | Header `access-token` | Yes | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `openId` | Query `open_id` | Yes | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `imageList` | `image_list` | Yes | `List<String>` | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `text` | `text` | No in docs, required by our adapter for Stage A | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `atUsers` | `at_users` | No | `List<String>` | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `downloadType` | `download_type` | No | Integer | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `privateStatus` | `private_status` | No | Integer | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `microAppId` | `micro_app_id` | No | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `microAppTitle` | `micro_app_title` | No | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `microAppUrl` | `micro_app_url` | No | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `musicId` | `music_id` | No | Long | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `poiCommerce` | `poi_commerce` | No | Boolean | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `poiId` | `poi_id` | No | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `taskId` | `task_id` | No | Long | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `agentClientKey` | `agent_client_key` | No | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |

Stage A recommendation: implement only `accessToken`, `openId`, `imageList`, `text`, `downloadType`, and `privateStatus` in adapter usage first. Keep optional anchor fields in DTO for forward compatibility but do not expose them in UI until product confirms.

### `DouyinCreateImageTextResponse`

| Java Field | Official Field | Required | Type | Source |
| --- | --- | --- | --- | --- |
| `itemId` | `data.item_id` | On success | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `videoId` | `data.video_id` | On success | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `errorCode` | `data.error_code` | Yes | Long | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `description` | `data.description` | No | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `extraErrorCode` | `extra.error_code` | No | Long | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `extraDescription` | `extra.description` | No | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `logId` | `extra.logid` | No | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `now` | `extra.now` | No | Long | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `subErrorCode` | `extra.sub_error_code` | No | Long | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| `subDescription` | `extra.sub_description` | No | String | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |

## Error Handling Design

### Exception Hierarchy

Initial design proposal:

```java
public class DouyinClientException extends RuntimeException {
    private final int httpStatus;
    private final Long errorCode;
    private final String description;
    private final String logId;
    private final boolean retryable;
    private final String rawBody;
}

public class DouyinAuthException extends DouyinClientException {}
public class DouyinPermissionException extends DouyinClientException {}
public class DouyinRateLimitException extends DouyinClientException {}
public class DouyinValidationException extends DouyinClientException {}
public class DouyinServerException extends DouyinClientException {}
```

Recommendation: keep the hierarchy shallow. Most application handling can branch on `retryable`, `errorCode`, and exception type.

### Error Code Mapping Strategy

Known public error codes from reviewed docs:

| Area | Official Code | Meaning | Proposed Exception | Retryable | Source |
| --- | --- | --- | --- | --- | --- |
| OAuth token | `10002` | 参数错误 | `DouyinValidationException` | No | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), reviewed 2026-05-05 |
| OAuth token | `10007` | 授权码过期 | `DouyinAuthException` | No | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), reviewed 2026-05-05 |
| OAuth token | `10013` | client_key/client_secret错误 | `DouyinAuthException` | No | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), reviewed 2026-05-05 |
| OAuth token | `10014` | client_key不匹配 | `DouyinAuthException` | No | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), reviewed 2026-05-05 |
| OAuth token | `10001` | 系统异常/系统错误 | `DouyinServerException` | Yes | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| OAuth token | `10003` | 密钥错误 | `DouyinAuthException` | No | [获取 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/get-access-token), reviewed 2026-05-05 |
| Refresh access token | `10005` | 参数缺失 | `DouyinValidationException` | No | [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| Refresh access token | `10010` | refresh_token过期 | `DouyinAuthException` | No | [刷新 access_token](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/account-permission/refresh-access-token), reviewed 2026-05-05 |
| Image upload | `2100005` | 参数错误 | `DouyinValidationException` | No | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| Image upload | `2190005` | 文件太大 | `DouyinValidationException` | No | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), reviewed 2026-05-05 |
| Image upload / create | `2100004` | 系统繁忙 | `DouyinServerException` | Yes | [图片上传](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/image-upload), [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| Create image text | `2114001` | 标题文字超过1000字 | `DouyinValidationException` | No | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| Create image text | `28001003` | access_token无效 | `DouyinAuthException` | No; trigger token refresh/re-auth path | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| Create image text | `28001008` | access_token过期 | `DouyinAuthException` | No at client layer; token service may refresh then retry once | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| Create image text | `28001016` | 应用被封禁或下线 | `DouyinPermissionException` | No | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| Create image text | `28001014` | 应用未授权任何能力 | `DouyinPermissionException` | No | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| Create image text | `28001018` | 应用未获得该能力 | `DouyinPermissionException` | No | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| Create image text | `28003017` | quota已用完 | `DouyinRateLimitException` | No until quota resets | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| Create image text | `28001019` | 应用该能力被封禁 | `DouyinPermissionException` | No | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |
| Create image text | `28001007` / `210005` | 参数不合法 | `DouyinValidationException` | No | [创建图文](https://partner.open-douyin.com/docs/resource/zh-CN/dop/develop/openapi/video-management/douyin/create-image-text/create-image-text), reviewed 2026-05-05 |

Unknown errors:

- Public docs do not fully cover all platform responses. Unknown `error_code` should map to `DouyinClientException` with `retryable=false` by default, preserve `rawBody`, `description`, `logId`, `sub_error_code`, and `sub_description`.
- Account封禁、图片内容违规、敏感词、频率限制的完整错误码需真实联调阶段补充 unless present in later official docs.

## Mock and Real Implementation Separation

### Bean Registration

Initial recommendation:

- `DouyinClient` is the interface.
- `MockDouyinClient` is registered when `geo.douyin.client.mode=mock`.
- `RealDouyinClient` is not implemented in A.2. Reserve registration for later:
  - `RealDouyinClient` when `geo.douyin.client.mode=real`
  - throw a clear startup error if mode is `real` before real implementation exists, or delay real bean creation until A.8.

Suggested Spring pattern:

```java
@Configuration
public class DouyinClientConfig {
    @Bean
    @ConditionalOnProperty(prefix = "geo.douyin.client", name = "mode", havingValue = "mock", matchIfMissing = true)
    DouyinClient mockDouyinClient(DouyinClientProperties properties) {
        return new MockDouyinClient(properties);
    }
}
```

Alternative: mark `MockDouyinClient` with `@Component` and `@ConditionalOnProperty`. A small config class is preferred because A.8 can add HTTP client dependencies cleanly without scattering conditions.

### Fault Injection

Current config is `geo.douyin.client.fault`. The old A.1 value is a string. For A.2, prefer a structured object, but this changes A.1 config shape. Decision needed before coding.

Option A, string list:

```yaml
fault: "token_expired,upload_failed"
```

Option B, structured object:

```yaml
fault:
  token-expired: false
  upload-failed: false
  create-failed: false
  rate-limit: false
  permission-denied: false
  review-outcome: passed
```

Initial recommendation: use Option B with `DouyinClientProperties.Fault` nested class. It is clearer for tests and supports combined scenarios without parsing strings. If accepted, A.2 should amend `DouyinClientProperties` and `application-dev.yml` as part of client mock implementation.

Mock scenarios to cover in A.2:

- `tokenExpired`: token exchange or publish APIs throw `DouyinAuthException`.
- `permissionDenied`: create image-text throws `DouyinPermissionException`.
- `uploadFailed`: upload image throws `DouyinValidationException` or generic client exception.
- `createFailed`: create image-text throws `DouyinClientException`.
- `rateLimit`: create image-text throws `DouyinRateLimitException`.
- `reviewOutcome`: returned create response carries mock external status for later adapter/status tests.

## Key Decision Points

These are initial recommendations, not final decisions.

### 1. Synchronous vs Asynchronous Client

Recommendation: keep `DouyinClient` synchronous for A.2-A.7.

Reasoning:

- Existing WeChat adapter flow is synchronous.
- Distribution task state machine already handles submission attempt lifecycle.
- Async/polling adds complexity before real platform latency is known.

Open point: if real image uploads become slow or need parallel upload, A.5/A.6 can parallelize image upload inside the service/adapter without changing the client interface.

### 2. Token Passing

Recommendation:

- `DouyinClient` receives `accessToken` and `openId` explicitly in upload/create request DTOs.
- Token acquisition and refresh ownership stays in future `DouyinTokenService`, not in the low-level client.

Reasoning:

- Mirrors official API boundaries: upload/create require header `access-token` and query `open_id`.
- Keeps client stateless and mockable.
- Lets service layer handle encrypted token storage, refresh lock, account status, and retry-on-expired-token behavior.

Open point: `refreshAccessToken(...)` does not need `openId` per official docs. Keep `openId` in service logs, not in request DTO.

### 3. HTTP Client

Recommendation: use Spring `RestClient` or existing project HTTP pattern if one is already standardized by the time A.8 starts. For A.2, do not add real HTTP code.

Reasoning:

- A.2 only requires mock implementation.
- Real implementation needs multipart upload and form-urlencoded support; both are straightforward with Spring HTTP clients.
- Avoid introducing an extra dependency before A.8.

Open point: before A.8, inspect existing HTTP clients used by WeChat and BrandGeoSite adapters and choose the most consistent project pattern.

### 4. Retry Position

Recommendation:

- `DouyinClient` does not retry by itself.
- Adapter/token service handles one controlled retry only for expired token after refresh.
- Distribution task retry policy handles server/system busy and network errors.

Reasoning:

- Keeps low-level client deterministic for tests.
- Avoids hidden duplicate publish attempts.
- Prevents accidental repeated image-text creation if the network fails after platform accepts the request.

Open point: idempotency behavior of Douyin create image-text is not confirmed in public docs; duplicate publish protection should rely on our `distribution_tasks.request_id` and not repeated client retries.

### 5. Response Normalization

Recommendation: each client response DTO keeps normalized top-level fields and raw response body.

Reasoning:

- Public docs show `data.error_code` and `extra.error_code`; both need preservation.
- Unknown error codes must be diagnosable during real联调.

Open point: exact raw-body storage location is adapter/service-level `response_payload`, not necessarily inside every DTO. A.2 mock can include raw JSON strings for parity.

## A.2 Coding Boundaries After Design Approval

A.2 should implement only:

1. `DouyinClient` interface.
2. DTO/response classes referenced by the interface.
3. Mock exception classes and error mapping utility if needed by the mock.
4. `MockDouyinClient`.
5. Spring bean registration for mock mode.
6. Unit tests for success and configured fault scenarios.

A.2 should not implement:

- OAuth service/controller.
- Token persistence/refresh service.
- Media service.
- `SelfMediaAdapter` implementation.
- Frontend changes.
- Database migrations.
