package com.huanjing.geo.module.content.douyin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.DouyinFeatureProperties;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.douyin.client.DouyinClient;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextResponse;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinAuthException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinClientException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinPermissionException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinRateLimitException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinServerException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinValidationException;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.service.adapter.FailureKind;
import com.huanjing.geo.module.content.service.adapter.ReviewStatusResult;
import com.huanjing.geo.module.content.service.adapter.SelfMediaAdapter;
import com.huanjing.geo.module.content.service.adapter.SubmitResult;
import com.huanjing.geo.module.content.service.adapter.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DouyinImageTextAdapter implements SelfMediaAdapter {
    public static final String PLATFORM = "douyin";
    private static final int MAX_TEXT_LENGTH = 1000;
    private static final int MAX_IMAGE_COUNT = 30;
    private static final String EXTERNAL_STATUS_ACCEPTED = "accepted";

    private final DouyinFeatureProperties featureProperties;
    private final DouyinTokenService douyinTokenService;
    private final DouyinMediaService douyinMediaService;
    private final DouyinClient douyinClient;
    private final ObjectMapper objectMapper;

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public ValidationResult validate(ArticleDraft article,
                                     String contentMarkdown,
                                     TargetContext.SelfMediaTarget target) {
        requireEnabled();
        SelfMediaAccount account = target == null ? null : target.account();
        if (account == null || !PLATFORM.equals(account.getPlatform())) {
            throw new BizException(400, "not douyin account");
        }
        if (!"active".equals(account.getStatus())) {
            throw new BizException(400, "douyin account not active");
        }
        List<Long> imageMaterialIds = target.imageMaterialIds();
        if (imageMaterialIds == null || imageMaterialIds.isEmpty()) {
            throw new BizException(400, "douyin image_list empty");
        }
        if (imageMaterialIds.size() > MAX_IMAGE_COUNT) {
            throw new BizException(400, "douyin image_list exceeds 30");
        }
        String text = extractText(article, target);
        if (text != null && text.length() > MAX_TEXT_LENGTH) {
            throw new BizException(400, "douyin text exceeds 1000");
        }
        return ValidationResult.pass();
    }

    @Override
    public SubmitResult submitToTarget(ArticleDraft article,
                                       String contentMarkdown,
                                       TargetContext.SelfMediaTarget target) {
        String requestPayload = null;
        String responseBody = null;
        try {
            validate(article, contentMarkdown, target);
            SelfMediaAccount account = target.account();
            List<String> imageIdList = new ArrayList<>();
            for (Long materialId : target.imageMaterialIds()) {
                String imageId = douyinMediaService.ensureUploadedImageId(account, account.getBrandId(), materialId);
                imageIdList.add(imageId);
            }
            String text = extractText(article, target);
            DouyinCreateImageTextRequest createRequest = buildCreateRequest(account, imageIdList, text, target);
            requestPayload = serializeRequestPayload(account, imageIdList, text, target);
            DouyinCreateImageTextResponse response = createWithTokenRetry(account, createRequest);
            responseBody = objectMapper.writeValueAsString(response);
            return buildSuccessResult(response, requestPayload, responseBody);
        } catch (DouyinClientException ex) {
            return buildFailureResult(ex, requestPayload, responseBody);
        } catch (BizException ex) {
            return SubmitResult.failure(ex.getCode(), requestPayload, responseBody, ex.getMessage(), FailureKind.VALIDATION, false);
        } catch (Exception ex) {
            return SubmitResult.failure(500, requestPayload, responseBody, trimError(ex.getMessage()), FailureKind.UNKNOWN, false);
        }
    }

    @Override
    public ReviewStatusResult refreshReviewStatus(DistributionTask task, SelfMediaAccount account) {
        if (account == null || !PLATFORM.equals(account.getPlatform())) {
            return ReviewStatusResult.notApplicable();
        }
        String responsePayload = task == null ? null : task.getResponsePayload();
        if (!StringUtils.hasText(responsePayload)) {
            return ReviewStatusResult.unknown(null, null, false, null);
        }
        try {
            JsonNode root = objectMapper.readTree(responsePayload);
            JsonNode mockOutcomeNode = root.get("_mock_review_outcome");
            if (mockOutcomeNode == null || mockOutcomeNode.isNull()) {
                return ReviewStatusResult.unknown(null, null, false, responsePayload);
            }
            String mockOutcome = mockOutcomeNode.asText();
            ReviewStatusResult.ReviewStatus status = mapMockOutcomeToReviewStatus(mockOutcome);
            return new ReviewStatusResult(status, mockOutcome, null, false, responsePayload);
        } catch (Exception ex) {
            log.warn("Failed to parse Douyin response payload for review status", ex);
            return ReviewStatusResult.unknown(null, null, false, responsePayload);
        }
    }

    private void requireEnabled() {
        if (featureProperties == null
                || featureProperties.getImageText() == null
                || !featureProperties.getImageText().isEnabled()) {
            throw new BizException(503, "douyin image-text feature disabled");
        }
    }

    private DouyinCreateImageTextRequest buildCreateRequest(SelfMediaAccount account,
                                                            List<String> imageIdList,
                                                            String text,
                                                            TargetContext.SelfMediaTarget target) {
        return DouyinCreateImageTextRequest.builder()
                .openId(account.getPlatformAccountId())
                .imageList(imageIdList)
                .text(text)
                .privateStatus(target.privateStatus())
                .downloadType(target.downloadType())
                .build();
    }

    private DouyinCreateImageTextResponse createWithTokenRetry(SelfMediaAccount account,
                                                               DouyinCreateImageTextRequest request) {
        String accessToken = douyinTokenService.getAccessToken(account);
        try {
            return douyinClient.createImageText(withAccessToken(request, accessToken));
        } catch (DouyinAuthException ex) {
            if (!isAccessTokenInvalid(ex)) {
                throw ex;
            }
            douyinTokenService.evictAccessToken(account);
            String freshToken = douyinTokenService.getAccessToken(account);
            return douyinClient.createImageText(withAccessToken(request, freshToken));
        }
    }

    private DouyinCreateImageTextRequest withAccessToken(DouyinCreateImageTextRequest request, String accessToken) {
        return DouyinCreateImageTextRequest.builder()
                .accessToken(accessToken)
                .openId(request.getOpenId())
                .imageList(request.getImageList())
                .text(request.getText())
                .atUsers(request.getAtUsers())
                .downloadType(request.getDownloadType())
                .privateStatus(request.getPrivateStatus())
                .microAppId(request.getMicroAppId())
                .microAppTitle(request.getMicroAppTitle())
                .microAppUrl(request.getMicroAppUrl())
                .musicId(request.getMusicId())
                .poiCommerce(request.getPoiCommerce())
                .poiId(request.getPoiId())
                .taskId(request.getTaskId())
                .agentClientKey(request.getAgentClientKey())
                .build();
    }

    private SubmitResult buildSuccessResult(DouyinCreateImageTextResponse response,
                                            String requestPayload,
                                            String responseBody) {
        SubmitResult result = SubmitResult.success(200, requestPayload, responseBody, null, response.getItemId());
        result.setExternalStatus(EXTERNAL_STATUS_ACCEPTED);
        result.setReviewStatus(mapMockOutcomeToReviewStatus(response.getMockReviewOutcome()));
        return result;
    }

    private SubmitResult buildFailureResult(DouyinClientException ex, String requestPayload, String responseBody) {
        return SubmitResult.failure(
                ex.getHttpStatus() == 0 ? 500 : ex.getHttpStatus(),
                requestPayload,
                responseBody,
                trimError(StringUtils.hasText(ex.getDescription()) ? ex.getDescription() : ex.getMessage()),
                mapExceptionToFailureKind(ex),
                ex.isRetryable()
        );
    }

    private String mapExceptionToFailureKind(DouyinClientException ex) {
        if (ex instanceof DouyinAuthException) {
            return FailureKind.AUTH;
        }
        if (ex instanceof DouyinPermissionException) {
            return FailureKind.PERMISSION;
        }
        if (ex instanceof DouyinRateLimitException) {
            return FailureKind.RATE_LIMIT;
        }
        if (ex instanceof DouyinValidationException) {
            return FailureKind.VALIDATION;
        }
        if (ex instanceof DouyinServerException) {
            return FailureKind.PLATFORM;
        }
        return FailureKind.UNKNOWN;
    }

    private ReviewStatusResult.ReviewStatus mapMockOutcomeToReviewStatus(String mockOutcome) {
        if ("passed".equalsIgnoreCase(mockOutcome)) {
            return ReviewStatusResult.ReviewStatus.PUBLISHED;
        }
        if ("rejected".equalsIgnoreCase(mockOutcome)) {
            return ReviewStatusResult.ReviewStatus.REJECTED;
        }
        return ReviewStatusResult.ReviewStatus.UNDER_REVIEW;
    }

    private boolean isAccessTokenInvalid(DouyinAuthException ex) {
        Long code = ex.getErrorCode();
        return Long.valueOf(28001003L).equals(code) || Long.valueOf(28001008L).equals(code);
    }

    private String extractText(ArticleDraft article, TargetContext.SelfMediaTarget target) {
        Map<String, Object> options = target == null ? null : target.platformOptions();
        if (options != null) {
            Object text = options.get("text");
            if (text != null && StringUtils.hasText(String.valueOf(text))) {
                return String.valueOf(text).trim();
            }
        }
        String title = article == null ? null : article.getTitle();
        return StringUtils.hasText(title) ? title.trim() : "";
    }

    private String serializeRequestPayload(SelfMediaAccount account,
                                           List<String> imageIdList,
                                           String text,
                                           TargetContext.SelfMediaTarget target) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("platform", PLATFORM);
        root.put("selfMediaAccountId", account.getId());
        root.put("openId", account.getPlatformAccountId());
        root.put("text", text);
        if (target.privateStatus() != null) {
            root.put("privateStatus", target.privateStatus());
        }
        if (target.downloadType() != null) {
            root.put("downloadType", target.downloadType());
        }
        ArrayNode images = root.putArray("imageIdList");
        for (String imageId : imageIdList) {
            images.add(imageId);
        }
        return objectMapper.writeValueAsString(root);
    }

    private String trimError(String value) {
        if (!StringUtils.hasText(value)) {
            return "douyin image-text submit failed";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}
