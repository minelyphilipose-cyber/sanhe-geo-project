package com.huanjing.geo.module.content.douyin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.DouyinFeatureProperties;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.douyin.client.DouyinClient;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextResponse;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinAuthException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinPermissionException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinRateLimitException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinServerException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinValidationException;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.service.adapter.AutoSelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.FailureKind;
import com.huanjing.geo.module.content.service.adapter.AutoSelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.ReviewStatusResult;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DouyinImageTextAdapterTest {
    private DouyinFeatureProperties featureProperties;
    private DouyinTokenService douyinTokenService;
    private DouyinMediaService douyinMediaService;
    private DouyinClient douyinClient;
    private ObjectMapper objectMapper;
    private DouyinImageTextAdapter adapter;

    @BeforeEach
    void setUp() {
        featureProperties = new DouyinFeatureProperties();
        featureProperties.getImageText().setEnabled(true);
        douyinTokenService = mock(DouyinTokenService.class);
        douyinMediaService = mock(DouyinMediaService.class);
        douyinClient = mock(DouyinClient.class);
        objectMapper = new ObjectMapper();
        adapter = new DouyinImageTextAdapter(
                featureProperties,
                douyinTokenService,
                douyinMediaService,
                douyinClient,
                objectMapper
        );
    }

    @Test
    void identity_matchesDouyinPlatform() {
        assertInstanceOf(AutoSelfMediaAdapter.class, adapter);
        assertEquals("douyin", adapter.platform());
        assertTrue(adapter.supportsPlatform("douyin"));
        assertInstanceOf(AutoSelfMediaAdapter.class, adapter);
    }

    @Test
    void validate_featureDisabledThrows503() {
        featureProperties.getImageText().setEnabled(false);

        BizException ex = assertThrows(BizException.class, () -> adapter.validate(article(), "markdown", target()));

        assertEquals(503, ex.getCode());
        assertEquals("douyin image-text feature disabled", ex.getMessage());
    }

    @Test
    void validate_nonDouyinAccountThrows400() {
        TargetContext.SelfMediaTarget target = target(account("wechat_mp", "active"), List.of(101L), Map.of("text", "ok"));

        BizException ex = assertThrows(BizException.class, () -> adapter.validate(article(), "markdown", target));

        assertEquals(400, ex.getCode());
        assertEquals("not douyin account", ex.getMessage());
    }

    @Test
    void validate_inactiveAccountThrows400() {
        TargetContext.SelfMediaTarget target = target(account("douyin", "disabled"), List.of(101L), Map.of("text", "ok"));

        BizException ex = assertThrows(BizException.class, () -> adapter.validate(article(), "markdown", target));

        assertEquals(400, ex.getCode());
        assertEquals("douyin account not active", ex.getMessage());
    }

    @Test
    void validate_emptyImagesThrows400() {
        TargetContext.SelfMediaTarget target = target(account(), List.of(), Map.of("text", "ok"));

        BizException ex = assertThrows(BizException.class, () -> adapter.validate(article(), "markdown", target));

        assertEquals(400, ex.getCode());
        assertEquals("douyin image_list empty", ex.getMessage());
    }

    @Test
    void validate_moreThanThirtyImagesThrows400() {
        List<Long> images = new ArrayList<>();
        for (long i = 1; i <= 31; i++) {
            images.add(i);
        }
        TargetContext.SelfMediaTarget target = target(account(), images, Map.of("text", "ok"));

        BizException ex = assertThrows(BizException.class, () -> adapter.validate(article(), "markdown", target));

        assertEquals(400, ex.getCode());
        assertEquals("douyin image_list exceeds 30", ex.getMessage());
    }

    @Test
    void validate_textLongerThan1000Throws400() {
        TargetContext.SelfMediaTarget target = target(account(), List.of(101L), Map.of("text", "x".repeat(1001)));

        BizException ex = assertThrows(BizException.class, () -> adapter.validate(article(), "markdown", target));

        assertEquals(400, ex.getCode());
        assertEquals("douyin text exceeds 1000", ex.getMessage());
    }

    @Test
    void submit_successUploadsImagesCreatesPostAndFillsReviewFields() throws Exception {
        when(douyinMediaService.ensureUploadedImageId(any(), any(), any()))
                .thenReturn("image-1", "image-2");
        when(douyinTokenService.getAccessToken(any(SelfMediaAccount.class))).thenReturn("access-token");
        when(douyinClient.createImageText(any()))
                .thenReturn(response("item-1", "video-1", "passed"));

        SubmitResult result = adapter.submitToTarget(article(), "markdown", target());

        assertTrue(result.isSuccess());
        assertEquals("item-1", result.getPlatformArticleId());
        assertEquals("accepted", result.getExternalStatus());
        assertEquals("published", result.getReviewStatus());
        assertTrue(result.getResponseBody().contains("\"_mock_review_outcome\":\"passed\""));
        assertTrue(result.getRequestPayload().contains("\"imageIdList\":[\"image-1\",\"image-2\"]"));
        assertTrue(result.getRequestPayload().contains("\"text\":\"custom text\""));
        assertTrue(result.getRequestPayload().contains("\"privateStatus\":1"));
        assertTrue(result.getRequestPayload().contains("\"downloadType\":2"));
        assertFalse(result.getRequestPayload().contains("access-token"));

        ArgumentCaptor<DouyinCreateImageTextRequest> requestCaptor = ArgumentCaptor.forClass(DouyinCreateImageTextRequest.class);
        verify(douyinClient).createImageText(requestCaptor.capture());
        assertEquals("access-token", requestCaptor.getValue().getAccessToken());
        assertEquals("open-1", requestCaptor.getValue().getOpenId());
        assertEquals(List.of("image-1", "image-2"), requestCaptor.getValue().getImageList());
        assertEquals("custom text", requestCaptor.getValue().getText());
        assertEquals(1, requestCaptor.getValue().getPrivateStatus());
        assertEquals(2, requestCaptor.getValue().getDownloadType());
    }

    @Test
    void submit_withoutPlatformTextUsesArticleTitle() {
        when(douyinMediaService.ensureUploadedImageId(any(), any(), any())).thenReturn("image-1");
        when(douyinTokenService.getAccessToken(any(SelfMediaAccount.class))).thenReturn("access-token");
        when(douyinClient.createImageText(any())).thenReturn(response("item-1", "video-1", null));

        SubmitResult result = adapter.submitToTarget(article(), "markdown",
                target(account(), List.of(101L), Map.of()));

        assertTrue(result.isSuccess());
        assertTrue(result.getRequestPayload().contains("\"text\":\"Article Title\""));
        assertEquals("under_review", result.getReviewStatus());
    }

    @Test
    void submit_accessTokenInvalidEvictsAndRetriesCreateOnce() {
        when(douyinMediaService.ensureUploadedImageId(any(), any(), any())).thenReturn("image-1");
        when(douyinTokenService.getAccessToken(any(SelfMediaAccount.class))).thenReturn("old-token", "fresh-token");
        when(douyinClient.createImageText(any()))
                .thenThrow(new DouyinAuthException(200, 28001008L, "token invalid", "log", false, "{}"))
                .thenReturn(response("item-1", "video-1", "pending"));

        SubmitResult result = adapter.submitToTarget(article(), "markdown",
                target(account(), List.of(101L), Map.of("text", "ok")));

        assertTrue(result.isSuccess());
        verify(douyinTokenService).evictAccessToken(any(SelfMediaAccount.class));
        ArgumentCaptor<DouyinCreateImageTextRequest> requestCaptor = ArgumentCaptor.forClass(DouyinCreateImageTextRequest.class);
        verify(douyinClient, org.mockito.Mockito.times(2)).createImageText(requestCaptor.capture());
        assertEquals("old-token", requestCaptor.getAllValues().get(0).getAccessToken());
        assertEquals("fresh-token", requestCaptor.getAllValues().get(1).getAccessToken());
        assertEquals("under_review", result.getReviewStatus());
    }

    @Test
    void submit_authErrorOtherCodeDoesNotRetryAndMapsFailureKind() {
        when(douyinMediaService.ensureUploadedImageId(any(), any(), any())).thenReturn("image-1");
        when(douyinTokenService.getAccessToken(any(SelfMediaAccount.class))).thenReturn("access-token");
        when(douyinClient.createImageText(any()))
                .thenThrow(new DouyinAuthException(200, 10013L, "client invalid", "log", false, "{}"));

        SubmitResult result = adapter.submitToTarget(article(), "markdown",
                target(account(), List.of(101L), Map.of("text", "ok")));

        assertFalse(result.isSuccess());
        assertEquals(FailureKind.AUTH, result.getFailureKind());
        assertEquals("client invalid", result.getErrorMessage());
        verify(douyinTokenService, never()).evictAccessToken(any());
        verify(douyinClient).createImageText(any());
    }

    @Test
    void submit_permissionRateLimitValidationAndServerFailuresMapKinds() {
        assertFailureKind(new DouyinPermissionException(200, 28001014L, "no scope", "log", false, "{}"),
                FailureKind.PERMISSION);
        assertFailureKind(new DouyinRateLimitException(200, 28003017L, "rate limit", "log", true, "{}"),
                FailureKind.RATE_LIMIT);
        assertFailureKind(new DouyinValidationException(200, 2114001L, "too long", "log", false, "{}"),
                FailureKind.VALIDATION);
        assertFailureKind(new DouyinServerException(500, 10001L, "server busy", "log", true, "{}"),
                FailureKind.PLATFORM);
    }

    @Test
    void submit_bizExceptionReturnsValidationFailure() {
        when(douyinMediaService.ensureUploadedImageId(any(), any(), any()))
                .thenThrow(new BizException(400, "douyin_image_too_large"));

        SubmitResult result = adapter.submitToTarget(article(), "markdown", target());

        assertFalse(result.isSuccess());
        assertEquals(400, result.getStatusCode());
        assertEquals(FailureKind.VALIDATION, result.getFailureKind());
        assertEquals("douyin_image_too_large", result.getErrorMessage());
    }

    @Test
    void refreshReviewStatus_nonDouyinReturnsNotApplicable() {
        ReviewStatusResult result = adapter.refreshReviewStatus(new DistributionTask(), account("wechat_mp", "active"));

        assertEquals(ReviewStatusResult.ReviewStatus.NOT_APPLICABLE, result.status());
    }

    @Test
    void refreshReviewStatus_noMockOutcomeReturnsUnknown() {
        DistributionTask task = new DistributionTask();
        task.setResponsePayload("{\"item_id\":\"item-1\"}");

        ReviewStatusResult result = adapter.refreshReviewStatus(task, account());

        assertEquals(ReviewStatusResult.ReviewStatus.UNKNOWN, result.status());
    }

    @Test
    void refreshReviewStatus_mockPassedReturnsPublished() {
        assertReviewStatus("passed", ReviewStatusResult.ReviewStatus.PUBLISHED);
    }

    @Test
    void refreshReviewStatus_mockPendingReturnsUnderReview() {
        assertReviewStatus("pending", ReviewStatusResult.ReviewStatus.UNDER_REVIEW);
    }

    @Test
    void refreshReviewStatus_mockRejectedReturnsRejected() {
        assertReviewStatus("rejected", ReviewStatusResult.ReviewStatus.REJECTED);
    }

    @Test
    void refreshReviewStatus_invalidJsonReturnsUnknown() {
        DistributionTask task = new DistributionTask();
        task.setResponsePayload("{bad-json");

        ReviewStatusResult result = adapter.refreshReviewStatus(task, account());

        assertEquals(ReviewStatusResult.ReviewStatus.UNKNOWN, result.status());
        assertEquals("{bad-json", result.rawResponse());
    }

    private void assertFailureKind(RuntimeException exception, String expectedKind) {
        when(douyinMediaService.ensureUploadedImageId(any(), any(), any())).thenReturn("image-1");
        when(douyinTokenService.getAccessToken(any(SelfMediaAccount.class))).thenReturn("access-token");
        when(douyinClient.createImageText(any())).thenThrow(exception);

        SubmitResult result = adapter.submitToTarget(article(), "markdown",
                target(account(), List.of(101L), Map.of("text", "ok")));

        assertFalse(result.isSuccess());
        assertEquals(expectedKind, result.getFailureKind());
        org.mockito.Mockito.reset(douyinMediaService, douyinTokenService, douyinClient);
    }

    private void assertReviewStatus(String outcome, ReviewStatusResult.ReviewStatus status) {
        DistributionTask task = new DistributionTask();
        task.setResponsePayload("{\"_mock_review_outcome\":\"" + outcome + "\"}");

        ReviewStatusResult result = adapter.refreshReviewStatus(task, account());

        assertEquals(status, result.status());
        assertEquals(outcome, result.externalStatus());
    }

    private ArticleDraft article() {
        ArticleDraft article = new ArticleDraft();
        article.setId(1L);
        article.setTitle("Article Title");
        return article;
    }

    private TargetContext.SelfMediaTarget target() {
        return target(account(), List.of(101L, 102L), Map.of("text", "custom text"));
    }

    private TargetContext.SelfMediaTarget target(SelfMediaAccount account, List<Long> imageMaterialIds, Map<String, Object> options) {
        return new TargetContext.SelfMediaTarget(account, null, imageMaterialIds, null, 1, 2, "req-1", options);
    }

    private SelfMediaAccount account() {
        return account("douyin", "active");
    }

    private SelfMediaAccount account(String platform, String status) {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(40L);
        account.setBrandId(30L);
        account.setPlatform(platform);
        account.setPlatformAccountId("open-1");
        account.setStatus(status);
        return account;
    }

    private DouyinCreateImageTextResponse response(String itemId, String videoId, String mockOutcome) {
        return DouyinCreateImageTextResponse.builder()
                .itemId(itemId)
                .videoId(videoId)
                .mockReviewOutcome(mockOutcome)
                .build();
    }
}
