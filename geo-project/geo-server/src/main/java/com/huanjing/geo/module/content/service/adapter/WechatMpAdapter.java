package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.service.render.wechat.WechatArticleRenderService;
import com.huanjing.geo.module.content.wechat.WechatHtmlRewriter;
import com.huanjing.geo.module.content.wechat.WechatMediaService;
import com.huanjing.geo.module.content.wechat.WechatMpClient;
import com.huanjing.geo.module.content.wechat.WechatTokenAwareExecutor;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.service.BrandService;
import com.huanjing.geo.module.system.entity.PublishSite;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Implements SiteAdapter only as transition compatibility. After self-media
 * account abstraction and Douyin mock integration are complete, this adapter
 * should keep only the SelfMediaAdapter role.
 */
@Component
@RequiredArgsConstructor
public class WechatMpAdapter implements SiteAdapter, AutoSelfMediaAdapter {
    public static final String PLATFORM = "wechat_mp";
    private static final int WECHAT_API_UNAUTHORIZED_CODE = 48001;
    private static final String WECHAT_API_UNAUTHORIZED = "WECHAT_API_UNAUTHORIZED";
    private static final String STAGE_PREPARE_COVER_MATERIAL = "WECHAT_PREPARE_COVER_MATERIAL";
    private static final String STAGE_RENDER_CONTENT = "WECHAT_RENDER_CONTENT";
    private static final String STAGE_ADD_DRAFT = "WECHAT_ADD_DRAFT";
    private static final String STAGE_SUBMIT_PUBLISH = "WECHAT_SUBMIT_PUBLISH";

    private final WechatArticleRenderService articleRenderService;
    private final WechatHtmlRewriter htmlRewriter;
    private final WechatMediaService mediaService;
    private final WechatTokenAwareExecutor tokenAwareExecutor;
    private final WechatMpClient wechatMpClient;
    private final WechatOpenPlatformProperties openPlatformProperties;
    private final BrandService brandService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String integrationMethod) {
        return PLATFORM.equalsIgnoreCase(integrationMethod);
    }

    @Override
    public boolean supportsPlatform(String platform) {
        return PLATFORM.equalsIgnoreCase(platform);
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public void preflightCredential(SelfMediaAccount account) {
        if (account == null || !PLATFORM.equals(account.getPlatform())) {
            throw new BizException(400, "not wechat_mp account");
        }
        if (!"active".equals(account.getStatus())) {
            throw new BizException(401, "wechat_mp account not active, please re-authorize");
        }
        tokenAwareExecutor.execute(account, accessToken -> {
            wechatMpClient.getMaterialCount(accessToken);
            return true;
        });
    }

    @Override
    public ValidationResult validate(ArticleDraft article, String contentMarkdown, PublishSite site) {
        return ValidationResult.pass();
    }

    @Override
    public ValidationResult validate(ArticleDraft article,
                                     String contentMarkdown,
                                     TargetContext.SelfMediaTarget target) {
        if (target == null || target.coverMaterialId() == null) {
            throw new BizException(400, "请选择公众号封面图片");
        }
        return ValidationResult.pass();
    }

    @Override
    public SubmitResult submit(ArticleDraft article, String contentMarkdown, PublishSite site) {
        throw new UnsupportedOperationException("Use submitToTarget for wechat_mp");
    }

    @Override
    public String parsePublishedUrl(String responseBody, PublishSite site) {
        return null;
    }

    @Override
    public SubmitResult submitToTarget(ArticleDraft article, String contentMarkdown, TargetContext target) {
        TargetContext.SelfMediaTarget mpTarget = requireTarget(target);
        return submitToTarget(article, contentMarkdown, mpTarget);
    }

    @Override
    public SubmitResult submitToTarget(ArticleDraft article,
                                       String contentMarkdown,
                                       TargetContext.SelfMediaTarget mpTarget) {
        SelfMediaAccount account = mpTarget.account();
        String requestPayload = null;
        String operationStage = STAGE_PREPARE_COVER_MATERIAL;
        try {
            Brand brand = brandService.requireExistingBrand(account.getBrandId());
            Long materialBrandId = materialBrandId(account, mpTarget);
            operationStage = STAGE_PREPARE_COVER_MATERIAL;
            String thumbMediaId = mediaService.ensureThumbMediaId(account, materialBrandId, mpTarget.coverMaterialId());
            operationStage = STAGE_RENDER_CONTENT;
            String html = articleRenderService.renderOrFallbackForPublish(article, contentMarkdown);
            String wechatHtml = htmlRewriter.rewrite(html, src -> mediaService.ensureContentImageUrl(account, materialBrandId, src));
            WechatMpClient.DraftArticle draftArticle = buildDraftArticle(article, brand, account, wechatHtml, thumbMediaId);
            requestPayload = buildRequestPayload(account, mpTarget.coverMaterialId(), draftArticle);
            operationStage = STAGE_ADD_DRAFT;
            WechatMpClient.DraftResult result =
                    tokenAwareExecutor.execute(account, accessToken -> wechatMpClient.addDraft(accessToken, draftArticle));
            if (shouldSubmitPublish(mpTarget)) {
                operationStage = STAGE_SUBMIT_PUBLISH;
                WechatMpClient.PublishResult publishResult =
                        tokenAwareExecutor.execute(account, accessToken -> wechatMpClient.submitPublish(accessToken, result.mediaId()));
                ObjectNode response = objectMapper.createObjectNode();
                response.put("media_id", result.mediaId());
                response.put("publish_id", publishResult.publishId());
                response.put("message", "submitted_to_wechat_freepublish");
                SubmitResult submitResult = SubmitResult.success(200, requestPayload, objectMapper.writeValueAsString(response), null);
                submitResult.setOperationStage(STAGE_SUBMIT_PUBLISH);
                submitResult.setPlatformPublishId(publishResult.publishId());
                submitResult.setExternalStatus("submitted_to_publish");
                submitResult.setReviewStatus(ReviewStatusResult.ReviewStatus.UNDER_REVIEW);
                return submitResult;
            }
            ObjectNode response = objectMapper.createObjectNode();
            response.put("media_id", result.mediaId());
            response.put("message", "saved_to_wechat_draft");
            SubmitResult submitResult = SubmitResult.success(200, requestPayload, objectMapper.writeValueAsString(response), null, result.mediaId());
            submitResult.setOperationStage(STAGE_ADD_DRAFT);
            submitResult.setExternalStatus("saved_to_draft");
            submitResult.setReviewStatus(ReviewStatusResult.ReviewStatus.NOT_APPLICABLE);
            return submitResult;
        } catch (BizException ex) {
            SubmitResult result = SubmitResult.failure(
                    ex.getCode(),
                    requestPayload,
                    ex.getMessage(),
                    userFacingErrorMessage(ex, operationStage),
                    failureKind(ex.getCode(), ex.getMessage()),
                    retryable(ex.getCode()));
            result.setOperationStage(operationStage);
            return result;
        } catch (Exception ex) {
            SubmitResult result = SubmitResult.failure(500, requestPayload, null, safeMessage(ex), FailureKind.UNKNOWN, false);
            result.setOperationStage(operationStage);
            return result;
        }
    }

    @Override
    public ReviewStatusResult refreshReviewStatus(DistributionTask task,
                                                  SelfMediaAccount account) {
        if (task == null || account == null || !PLATFORM.equals(account.getPlatform())) {
            return ReviewStatusResult.notApplicable();
        }
        if (!StringUtils.hasText(task.getPlatformPublishId())) {
            return ReviewStatusResult.unknown(null, "wechat publish_id missing", false, task.getResponsePayload());
        }
        try {
            WechatMpClient.PublishStatusResult statusResult =
                    tokenAwareExecutor.execute(account, accessToken ->
                            wechatMpClient.getPublishStatus(accessToken, task.getPlatformPublishId()));
            return mapPublishStatus(statusResult);
        } catch (BizException ex) {
            return ReviewStatusResult.unknown(null, ex.getMessage(), retryable(ex.getCode()), null);
        } catch (Exception ex) {
            return ReviewStatusResult.unknown(null, safeMessage(ex), false, null);
        }
    }

    private ReviewStatusResult mapPublishStatus(WechatMpClient.PublishStatusResult result) {
        int publishStatus = result == null ? -1 : result.publishStatus();
        String externalStatus = String.valueOf(publishStatus);
        String feedback = result == null ? null : result.failIndex();
        String rawResponse = result == null ? null : result.rawResponse();
        String articleId = result == null ? null : result.articleId();
        String articleUrl = result == null ? null : result.articleUrl();
        if (publishStatus == 0) {
            return new ReviewStatusResult(
                    ReviewStatusResult.ReviewStatus.PUBLISHED,
                    externalStatus,
                    feedback,
                    false,
                    rawResponse,
                    articleId,
                    articleUrl);
        }
        if (publishStatus == 1) {
            return new ReviewStatusResult(ReviewStatusResult.ReviewStatus.UNDER_REVIEW, externalStatus, feedback, true, rawResponse);
        }
        if (publishStatus == 2 || publishStatus == 3 || publishStatus == 4) {
            return new ReviewStatusResult(ReviewStatusResult.ReviewStatus.REJECTED, externalStatus, feedback, false, rawResponse);
        }
        if (publishStatus == 5 || publishStatus == 6) {
            return new ReviewStatusResult(ReviewStatusResult.ReviewStatus.OFFLINE, externalStatus, feedback, false, rawResponse);
        }
        return ReviewStatusResult.unknown(externalStatus, feedback, true, rawResponse);
    }

    private TargetContext.SelfMediaTarget requireTarget(TargetContext target) {
        if (target instanceof TargetContext.SelfMediaTarget mpTarget) {
            return mpTarget;
        }
        throw new IllegalArgumentException("WechatMpAdapter requires SelfMediaTarget");
    }

    private WechatMpClient.DraftArticle buildDraftArticle(ArticleDraft article,
                                                          Brand brand,
                                                          SelfMediaAccount account,
                                                          String content,
                                                          String thumbMediaId) {
        String title = trimToLength(article == null ? null : article.getTitle(), 64);
        String author = trimToLength(firstText(brand.getBrandName(), account.getAccountName()), 8);
        return new WechatMpClient.DraftArticle(
                StringUtils.hasText(title) ? title : "未命名文章",
                StringUtils.hasText(author) ? author : "",
                digest(content),
                content,
                "",
                thumbMediaId,
                0,
                0
        );
    }

    private String buildRequestPayload(SelfMediaAccount account, Long coverMaterialId, WechatMpClient.DraftArticle article) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("platform", PLATFORM);
        root.put("selfMediaAccountId", account.getId());
        root.put("authorizerAppid", account.getPlatformAccountId());
        root.put("coverMaterialId", coverMaterialId);
        ObjectNode draft = root.putObject("draftArticle");
        draft.put("title", article.title());
        draft.put("author", article.author());
        draft.put("digest", article.digest());
        draft.put("contentSourceUrl", article.contentSourceUrl());
        draft.put("thumbMediaId", article.thumbMediaId());
        draft.put("needOpenComment", article.needOpenComment());
        draft.put("onlyFansCanComment", article.onlyFansCanComment());
        return objectMapper.writeValueAsString(root);
    }

    private boolean shouldSubmitPublish(TargetContext.SelfMediaTarget target) {
        if (target == null || target.platformOptions() == null) {
            return false;
        }
        Object action = target.platformOptions().get("publishAction");
        boolean requested = action != null && "publish".equalsIgnoreCase(String.valueOf(action).trim());
        if (!requested) {
            return false;
        }
        if (!openPlatformProperties.isAutoPublishEnabled()) {
            throw new BizException(403, "微信公众号自动发布未开启，当前仅允许保存草稿");
        }
        return true;
    }

    private Long materialBrandId(SelfMediaAccount account, TargetContext.SelfMediaTarget target) {
        Object value = target == null || target.platformOptions() == null
                ? null
                : target.platformOptions().get("materialBrandId");
        Long parsed = parseLong(value);
        if (parsed != null) {
            return parsed;
        }
        return account == null ? null : account.getBrandId();
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number number) {
                return number.longValue();
            }
            String text = String.valueOf(value).trim();
            return StringUtils.hasText(text) ? Long.valueOf(text) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String failureKind(int code, String message) {
        if (code == WECHAT_API_UNAUTHORIZED_CODE || containsIgnoreCase(message, "api unauthorized")) {
            return WECHAT_API_UNAUTHORIZED;
        }
        if (code == 40001 || code == 42001) {
            return FailureKind.AUTH_EXPIRED;
        }
        if (code == 45009 || code == 45011 || code == 429) {
            return FailureKind.SERVER_ERROR;
        }
        if (code >= 400 && code < 500) {
            return FailureKind.CLIENT_ERROR;
        }
        return FailureKind.UNKNOWN;
    }

    private boolean retryable(int code) {
        if (code == WECHAT_API_UNAUTHORIZED_CODE) {
            return false;
        }
        return code == 40001 || code == 42001 || code == 45009 || code == 45011 || code == 429 || code >= 500;
    }

    private String userFacingErrorMessage(BizException ex, String operationStage) {
        if (ex == null) {
            return "";
        }
        if (WECHAT_API_UNAUTHORIZED.equals(failureKind(ex.getCode(), ex.getMessage()))) {
            return switch (operationStage) {
                case STAGE_PREPARE_COVER_MATERIAL, STAGE_RENDER_CONTENT ->
                        "当前公众号缺少素材上传或图片处理权限。请确认客户公众号具备素材管理权限，并重新授权公众号。";
                case STAGE_ADD_DRAFT ->
                        "当前公众号缺少新增草稿权限。请确认客户公众号具备草稿箱/文章管理能力，并重新授权公众号。";
                case STAGE_SUBMIT_PUBLISH ->
                        "当前公众号缺少提交发布权限。请确认客户公众号具备发布/群发与通知能力，并重新授权公众号。";
                default ->
                        "当前公众号缺少发布所需授权。请在品牌详情重新授权公众号，并确认授权时已勾选素材、草稿和发布相关权限。";
            };
        }
        return ex.getMessage();
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return StringUtils.hasText(value)
                && StringUtils.hasText(needle)
                && value.toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT));
    }

    private String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return StringUtils.hasText(second) ? second.trim() : "";
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        int[] codePoints = trimmed.codePoints().toArray();
        if (codePoints.length <= maxLength) {
            return trimmed;
        }
        return new String(codePoints, 0, maxLength);
    }

    private String digest(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        return trimToLength(Jsoup.parse(html).text(), 120);
    }

    private String safeMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
