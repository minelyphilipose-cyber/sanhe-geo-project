package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.service.render.MarkdownToHtmlRenderer;
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

    private final MarkdownToHtmlRenderer markdownRenderer;
    private final WechatHtmlRewriter htmlRewriter;
    private final WechatMediaService mediaService;
    private final WechatTokenAwareExecutor tokenAwareExecutor;
    private final WechatMpClient wechatMpClient;
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
        try {
            Brand brand = brandService.requireExistingBrand(account.getBrandId());
            String thumbMediaId = mediaService.ensureThumbMediaId(account, account.getBrandId(), mpTarget.coverMaterialId());
            String html = markdownRenderer.render(contentMarkdown);
            String wechatHtml = htmlRewriter.rewrite(html, src -> mediaService.ensureContentImageUrl(account, src));
            WechatMpClient.DraftArticle draftArticle = buildDraftArticle(article, brand, account, wechatHtml, thumbMediaId);
            requestPayload = buildRequestPayload(account, mpTarget.coverMaterialId(), draftArticle);
            WechatMpClient.DraftResult result =
                    tokenAwareExecutor.execute(account, accessToken -> wechatMpClient.addDraft(accessToken, draftArticle));
            ObjectNode response = objectMapper.createObjectNode();
            response.put("media_id", result.mediaId());
            response.put("message", "saved_to_wechat_draft");
            return SubmitResult.success(200, requestPayload, objectMapper.writeValueAsString(response), null, result.mediaId());
        } catch (BizException ex) {
            return SubmitResult.failure(ex.getCode(), requestPayload, null, ex.getMessage(), failureKind(ex.getCode()), retryable(ex.getCode()));
        } catch (Exception ex) {
            return SubmitResult.failure(500, requestPayload, null, safeMessage(ex), FailureKind.UNKNOWN, false);
        }
    }

    @Override
    public ReviewStatusResult refreshReviewStatus(DistributionTask task,
                                                  SelfMediaAccount account) {
        return ReviewStatusResult.notApplicable();
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

    private String failureKind(int code) {
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
        return code == 40001 || code == 42001 || code == 45009 || code == 45011 || code == 429 || code >= 500;
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
