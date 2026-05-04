# WeChat Open Platform Submission Checklist

This checklist is for submitting the "幻境AI" WeChat Open Platform third-party platform for full-network release review.

## Environment Configuration

| Item | Dev | Test | Prod | Status |
| --- | --- | --- | --- | --- |
| Component AppID | `wx514c6537de79de97` | TBD | TBD | Pending |
| Event receive URL | local/mock | TBD HTTPS URL | TBD HTTPS URL | Pending |
| Message receive URL | local/mock | TBD HTTPS URL | TBD HTTPS URL | Pending |
| Auth callback URL | `http://localhost:8080/api/wechat/open-platform/auth/callback` | TBD HTTPS URL | TBD HTTPS URL | Pending |
| Auth launch domain | local/mock | TBD registered domain | TBD registered domain | Pending |
| Frontend callback URL | `http://localhost:5173/admin/content/execution` | TBD HTTPS URL | TBD HTTPS URL | Pending |
| Token | env `WECHAT_TOKEN` | env `WECHAT_TOKEN` | env `WECHAT_TOKEN` | Pending |
| EncodingAESKey | env `WECHAT_ENCODING_AES_KEY` | env `WECHAT_ENCODING_AES_KEY` | env `WECHAT_ENCODING_AES_KEY` | Pending |
| Component secret | env `WECHAT_COMPONENT_SECRET` | env `WECHAT_COMPONENT_SECRET` | env `WECHAT_COMPONENT_SECRET` | Pending |

Production requirements:
- Callback URLs must be HTTPS, public, ICP-compliant, and use port 443.
- Production must not rely on `application-dev.yml` defaults.
- `geo.wechat.open-platform.draft-distribution-enabled` stays `false` until review passes and real account validation is complete.

## Product Decisions

Confirm these before real account validation:
- Draft `author`: current implementation uses brand name first, then official account name fallback.
- Draft `digest`: current implementation extracts plain text from sanitized HTML and truncates to 120 characters.
- `content_source_url`: first stage is empty.
- Comment settings: `need_open_comment = 0`, `only_fans_can_comment = 0`.
- First stage target: save to WeChat draft box only, no publish/send.

## Review Code Readiness

Backend callbacks:
- Component event callback supports `component_verify_ticket`.
- Component events handle `authorized`, `unauthorized`, and `updateauthorized`.
- `updateauthorized` revalidates `func_info` and updates account status.
- Message callback supports full-network review messages:
  - `TESTCOMPONENT_MSG_TYPE_TEXT`
  - `QUERY_AUTH_CODE:{code}` with customer-service reply.

Security:
- OAuth `state` is random, Redis-backed, short-lived, and one-time use.
- WeChat encrypted messages validate signature and AppID.
- Access tokens and component secrets are not written to logs or distribution payloads.
- Content image download blocks private, loopback, link-local, and multicast addresses.

Draft flow:
- Cover uses permanent thumb material.
- Content images use `media/uploadimg` and are rewritten to WeChat URLs.
- Idempotency uses frontend-generated `requestId` per click.
- Account status must be `active` before distribution.

## Test Readiness

Current local coverage:
- `WechatMessageCryptoServiceTest`
- `WechatHtmlRewriterTest`
- `WechatFuncInfoValidatorTest`

Required before submission:
- `mvn -q "-Dtest=WechatMessageCryptoServiceTest,WechatHtmlRewriterTest,WechatFuncInfoValidatorTest" test`
- `mvn -q -DskipTests compile`
- `npm run build`
- Manual test against test-server callback URL after WeChat dashboard configuration is saved.

## Business And Compliance Materials

Confirm with product/legal before submission:
- Company主体认证完成。
- ICP 备案域名可访问。
- 平台官网可访问，内容与 GEO 优化服务一致。
- 用户协议 URL 可访问。
- 隐私协议 URL 可访问。
- 第三方平台业务说明材料准备完成。
- 审核驳回联系人和整改负责人已指定。

## Tracking

After submission:
- Assign one owner to check review status in WeChat Open Platform daily.
- Record every status change: submitted, reviewing, rejected, passed.
- If rejected, classify as material issue, code review issue, or platform-type issue.
