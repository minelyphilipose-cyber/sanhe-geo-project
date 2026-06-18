package com.huanjing.geo.module.content.service.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatMpAdapterTest {

    private final WechatMpAdapter adapter = new WechatMpAdapter(null, null, null, null, null, null, null, null);

    @Test
    void selfMediaIdentity_matchesWechatPlatform() {
        assertInstanceOf(AutoSelfMediaAdapter.class, adapter);
        assertEquals("wechat_mp", adapter.platform());
        assertTrue(adapter.supportsPlatform("wechat_mp"));
        assertInstanceOf(AutoSelfMediaAdapter.class, adapter);
    }

    @Test
    void refreshReviewStatus_notApplicableForWechatDraftFlow() {
        ReviewStatusResult result = adapter.refreshReviewStatus(null, new SelfMediaAccount());

        assertEquals(ReviewStatusResult.ReviewStatus.NOT_APPLICABLE, result.status());
    }

    @Test
    void validate_missingCoverRejectsBeforePlatformCall() {
        Fixture fixture = fixture(false);
        TargetContext.SelfMediaTarget target = new TargetContext.SelfMediaTarget(
                fixture.account, null, List.of(), List.of(), null, null, "request-1", Map.of());

        var ex = assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> fixture.adapter.validate(article(), "markdown body", target));

        assertEquals(400, ex.getCode());
        assertEquals("请选择公众号封面图片", ex.getMessage());
        verify(fixture.wechatMpClient, never()).addDraft(any(), any());
    }

    @Test
    void preflightCredential_activeAccountChecksMaterialCount() {
        Fixture fixture = fixture(false);

        fixture.adapter.preflightCredential(fixture.account);

        verify(fixture.wechatMpClient).getMaterialCount("access-token");
    }

    @Test
    void preflightCredential_inactiveAccountRejectsBeforePlatformCall() {
        Fixture fixture = fixture(false);
        fixture.account.setStatus("expired");

        var ex = assertThrows(com.huanjing.geo.common.exception.BizException.class,
                () -> fixture.adapter.preflightCredential(fixture.account));

        assertEquals(401, ex.getCode());
        assertEquals("wechat_mp account not active, please re-authorize", ex.getMessage());
        verify(fixture.wechatMpClient, never()).getMaterialCount(any());
    }

    @Test
    void submitToTarget_savesDraftByDefaultAndBuildsSanitizedPayload() {
        Fixture fixture = fixture(false);
        TargetContext.SelfMediaTarget target = target(fixture.account, Map.of());

        SubmitResult result = fixture.adapter.submitToTarget(article(), "markdown body", target);

        assertTrue(result.isSuccess());
        assertEquals("draft_media_id", result.getPlatformArticleId());
        assertEquals("saved_to_draft", result.getExternalStatus());
        assertTrue(result.getRequestPayload().contains("\"platform\":\"wechat_mp\""));
        assertTrue(result.getRequestPayload().contains("\"authorizerAppid\":\"wx-appid\""));
        assertTrue(result.getRequestPayload().contains("\"coverMaterialId\":100"));
        assertTrue(result.getRequestPayload().contains("\"title\":\"测试标题\""));
        assertTrue(result.getRequestPayload().contains("\"author\":\"三禾\""));
        assertTrue(result.getRequestPayload().contains("\"thumbMediaId\":\"thumb_media_id\""));
        assertFalse(result.getRequestPayload().contains("access-token"));
        assertFalse(result.getRequestPayload().contains("<p>wechat body</p>"));

        ArgumentCaptor<WechatMpClient.DraftArticle> draftCaptor = ArgumentCaptor.forClass(WechatMpClient.DraftArticle.class);
        verify(fixture.wechatMpClient).addDraft(eq("access-token"), draftCaptor.capture());
        WechatMpClient.DraftArticle draftArticle = draftCaptor.getValue();
        assertEquals("测试标题", draftArticle.title());
        assertEquals("三禾", draftArticle.author());
        assertEquals("wechat body", draftArticle.digest());
        assertEquals("<p>wechat body</p>", draftArticle.content());
        assertEquals("thumb_media_id", draftArticle.thumbMediaId());
        verify(fixture.wechatMpClient, never()).submitPublish(any(), any());
    }

    @Test
    void submitToTarget_submitsPublishWhenRequestedAndEnabled() {
        Fixture fixture = fixture(true);
        TargetContext.SelfMediaTarget target = target(fixture.account, Map.of("publishAction", "publish"));

        SubmitResult result = fixture.adapter.submitToTarget(article(), "markdown body", target);

        assertTrue(result.isSuccess());
        assertEquals("publish_id", result.getPlatformPublishId());
        assertEquals(null, result.getPlatformArticleId());
        assertEquals("submitted_to_publish", result.getExternalStatus());
        assertEquals("under_review", result.getReviewStatus());
        verify(fixture.wechatMpClient).submitPublish(eq("access-token"), eq("draft_media_id"));
    }

    @Test
    void submitToTarget_rejectsPublishWhenNotEnabled() {
        Fixture fixture = fixture(false);
        TargetContext.SelfMediaTarget target = target(fixture.account, Map.of("publishAction", "publish"));

        SubmitResult result = fixture.adapter.submitToTarget(article(), "markdown body", target);

        assertFalse(result.isSuccess());
        assertEquals(403, result.getStatusCode());
        assertEquals("微信公众号自动发布未开启，当前仅允许保存草稿", result.getErrorMessage());
        verify(fixture.wechatMpClient, never()).submitPublish(any(), any());
    }

    @Test
    void submitToTarget_mapsWechatApiUnauthorizedToActionableFailure() {
        Fixture fixture = fixture(true);
        TargetContext.SelfMediaTarget target = target(fixture.account, Map.of("publishAction", "publish"));
        when(fixture.wechatMpClient.addDraft(eq("access-token"), any()))
                .thenThrow(new BizException(48001, "api unauthorized rid: rid-from-wechat"));

        SubmitResult result = fixture.adapter.submitToTarget(article(), "markdown body", target);

        assertFalse(result.isSuccess());
        assertEquals(48001, result.getStatusCode());
        assertEquals("WECHAT_API_UNAUTHORIZED", result.getFailureKind());
        assertEquals("WECHAT_ADD_DRAFT", result.getOperationStage());
        assertEquals("当前公众号缺少新增草稿权限。请确认客户公众号具备草稿箱/文章管理能力，并重新授权公众号。",
                result.getErrorMessage());
        assertEquals("api unauthorized rid: rid-from-wechat", result.getResponseBody());
        assertFalse(result.isRetryable());
        verify(fixture.wechatMpClient, never()).submitPublish(any(), any());
    }

    @Test
    void submitToTarget_mapsPublishApiUnauthorizedToPublishStageFailure() {
        Fixture fixture = fixture(true);
        TargetContext.SelfMediaTarget target = target(fixture.account, Map.of("publishAction", "publish"));
        when(fixture.wechatMpClient.submitPublish(eq("access-token"), eq("draft_media_id")))
                .thenThrow(new BizException(48001, "api unauthorized rid: publish-rid"));

        SubmitResult result = fixture.adapter.submitToTarget(article(), "markdown body", target);

        assertFalse(result.isSuccess());
        assertEquals("WECHAT_API_UNAUTHORIZED", result.getFailureKind());
        assertEquals("WECHAT_SUBMIT_PUBLISH", result.getOperationStage());
        assertEquals("当前公众号缺少提交发布权限。请确认客户公众号具备发布/群发与通知能力，并重新授权公众号。",
                result.getErrorMessage());
        assertEquals("api unauthorized rid: publish-rid", result.getResponseBody());
        assertFalse(result.isRetryable());
    }

    @Test
    void refreshReviewStatus_publishedReturnsArticleId() {
        Fixture fixture = fixture(true);
        DistributionTask task = reviewTask("publish_id");
        when(fixture.wechatMpClient.getPublishStatus(eq("access-token"), eq("publish_id")))
                .thenReturn(new WechatMpClient.PublishStatusResult(0, "article_id", "{\"publish_status\":0}", null));

        ReviewStatusResult result = fixture.adapter.refreshReviewStatus(task, fixture.account);

        assertEquals(ReviewStatusResult.ReviewStatus.PUBLISHED, result.status());
        assertEquals("0", result.externalStatus());
        assertEquals("article_id", result.platformArticleId());
    }

    @Test
    void refreshReviewStatus_publishingRemainsUnderReview() {
        Fixture fixture = fixture(true);
        DistributionTask task = reviewTask("publish_id");
        when(fixture.wechatMpClient.getPublishStatus(eq("access-token"), eq("publish_id")))
                .thenReturn(new WechatMpClient.PublishStatusResult(1, null, "{\"publish_status\":1}", null));

        ReviewStatusResult result = fixture.adapter.refreshReviewStatus(task, fixture.account);

        assertEquals(ReviewStatusResult.ReviewStatus.UNDER_REVIEW, result.status());
        assertTrue(result.retryable());
    }

    @Test
    void refreshReviewStatus_deletedOrBlockedMapsToOfflineTerminal() {
        Fixture fixture = fixture(true);
        DistributionTask task = reviewTask("publish_id");
        when(fixture.wechatMpClient.getPublishStatus(eq("access-token"), eq("publish_id")))
                .thenReturn(new WechatMpClient.PublishStatusResult(5, null, "{\"publish_status\":5}", null));

        ReviewStatusResult result = fixture.adapter.refreshReviewStatus(task, fixture.account);

        assertEquals(ReviewStatusResult.ReviewStatus.OFFLINE, result.status());
        assertEquals("5", result.externalStatus());
        assertFalse(result.retryable());
    }

    @Test
    void refreshReviewStatus_missingPublishIdReturnsUnknown() {
        Fixture fixture = fixture(true);

        ReviewStatusResult result = fixture.adapter.refreshReviewStatus(reviewTask(null), fixture.account);

        assertEquals(ReviewStatusResult.ReviewStatus.UNKNOWN, result.status());
        assertEquals("wechat publish_id missing", result.reviewFeedback());
    }

    private Fixture fixture(boolean autoPublishEnabled) {
        WechatArticleRenderService articleRenderService = mock(WechatArticleRenderService.class);
        WechatHtmlRewriter htmlRewriter = mock(WechatHtmlRewriter.class);
        WechatMediaService mediaService = mock(WechatMediaService.class);
        WechatTokenAwareExecutor tokenAwareExecutor = mock(WechatTokenAwareExecutor.class);
        WechatMpClient wechatMpClient = mock(WechatMpClient.class);
        WechatOpenPlatformProperties properties = new WechatOpenPlatformProperties();
        BrandService brandService = mock(BrandService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        properties.setAutoPublishEnabled(autoPublishEnabled);
        SelfMediaAccount account = account();
        Brand brand = new Brand();
        brand.setBrandName("三禾");

        when(brandService.requireExistingBrand(10L)).thenReturn(brand);
        when(mediaService.ensureThumbMediaId(account, 10L, 100L)).thenReturn("thumb_media_id");
        when(articleRenderService.renderOrFallbackForPublish(any(), eq("markdown body"))).thenReturn("<p>markdown body</p>");
        when(htmlRewriter.rewrite(eq("<p>markdown body</p>"), any())).thenReturn("<p>wechat body</p>");
        when(wechatMpClient.addDraft(eq("access-token"), any())).thenReturn(new WechatMpClient.DraftResult("draft_media_id"));
        when(wechatMpClient.submitPublish(eq("access-token"), eq("draft_media_id"))).thenReturn(new WechatMpClient.PublishResult("publish_id"));
        when(tokenAwareExecutor.execute(eq(account), any())).thenAnswer(invocation -> {
            Function<String, ?> operation = invocation.getArgument(1);
            return operation.apply("access-token");
        });

        WechatMpAdapter tested = new WechatMpAdapter(
                articleRenderService,
                htmlRewriter,
                mediaService,
                tokenAwareExecutor,
                wechatMpClient,
                properties,
                brandService,
                objectMapper
        );
        return new Fixture(tested, account, wechatMpClient);
    }

    private SelfMediaAccount account() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(1L);
        account.setBrandId(10L);
        account.setPlatform("wechat_mp");
        account.setPlatformAccountId("wx-appid");
        account.setAccountName("公众号");
        account.setStatus("active");
        return account;
    }

    private ArticleDraft article() {
        ArticleDraft article = new ArticleDraft();
        article.setTitle("测试标题");
        return article;
    }

    private TargetContext.SelfMediaTarget target(SelfMediaAccount account, Map<String, Object> platformOptions) {
        return new TargetContext.SelfMediaTarget(account, 100L, List.of(), List.of(), null, null, "request-1", platformOptions);
    }

    private DistributionTask reviewTask(String publishId) {
        DistributionTask task = new DistributionTask();
        task.setId(10L);
        task.setPlatformPublishId(publishId);
        task.setResponsePayload("{}");
        return task;
    }

    private record Fixture(WechatMpAdapter adapter, SelfMediaAccount account, WechatMpClient wechatMpClient) {
    }
}
