package com.huanjing.geo.module.content.douyin.client;

import com.huanjing.geo.module.content.config.DouyinClientProperties;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCodeTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextResponse;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinImageUploadRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinImageUploadResponse;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinRefreshAccessTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinTokenResponse;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinAuthException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinClientException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinErrorMapper;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinPermissionException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinRateLimitException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Mock Douyin client for Stage A closed-loop development.
 *
 * <p>Fault behavior is method-local and deterministic:</p>
 * <ul>
 *     <li>exchangeCodeForToken: always returns a mock OAuth token in A.2.</li>
 *     <li>refreshAccessToken: always returns a refreshed mock token; access-token expiry is not
 *     simulated on refresh because refresh uses refresh_token.</li>
 *     <li>uploadImage: tokenExpired takes precedence over uploadFailed.</li>
 *     <li>createImageText: tokenExpired &gt; rateLimit &gt; permissionDenied &gt; createFailed &gt; success.</li>
 * </ul>
 * <p>`reviewOutcome` never turns createImageText into a synchronous failure; it is returned as a
 * mock-only marker because real create_image_text accepts the submission before review finishes.</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.douyin.client", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockDouyinClient implements DouyinClient {
    private static final Long ERROR_TOKEN_EXPIRED = 28001008L;
    private static final Long ERROR_PERMISSION_DENIED = 28001018L;
    private static final Long ERROR_RATE_LIMIT = 28003017L;
    private static final Long ERROR_UPLOAD_FAILED = 2100005L;
    private static final Long ERROR_CREATE_FAILED = 2100004L;

    private final DouyinClientProperties properties;

    @Override
    public DouyinTokenResponse exchangeCodeForToken(DouyinCodeTokenRequest request) {
        return DouyinTokenResponse.builder()
                .accessToken("mock_douyin_access_token_" + suffix())
                .refreshToken("mock_douyin_refresh_token_" + suffix())
                .openId("mock_open_id")
                .expiresIn(7200L)
                .refreshExpiresIn(30L * 24 * 3600)
                .scope("video.create.bind")
                .errorCode(0L)
                .message("success")
                .logId(mockLogId())
                .rawBody("{\"message\":\"success\",\"data\":{\"error_code\":0}}")
                .build();
    }

    @Override
    public DouyinTokenResponse refreshAccessToken(DouyinRefreshAccessTokenRequest request) {
        return DouyinTokenResponse.builder()
                .accessToken("mock_douyin_refreshed_access_token_" + suffix())
                .refreshToken("mock_douyin_refreshed_refresh_token_" + suffix())
                .openId("mock_open_id")
                .expiresIn(7200L)
                .refreshExpiresIn(30L * 24 * 3600)
                .scope("video.create.bind")
                .errorCode(0L)
                .message("success")
                .logId(mockLogId())
                .rawBody("{\"message\":\"success\",\"data\":{\"error_code\":0}}")
                .build();
    }

    @Override
    public DouyinImageUploadResponse uploadImage(DouyinImageUploadRequest request) {
        failIfTokenExpired();
        if (fault().isUploadFailed()) {
            throw mapped(new DouyinValidationException(
                    200, ERROR_UPLOAD_FAILED, "mock image upload failed", mockLogId(), false, "{}"
            ));
        }
        return DouyinImageUploadResponse.builder()
                .imageId("mock_image_" + suffix())
                .width(1080)
                .height(1440)
                .errorCode(0L)
                .description("success")
                .logId(mockLogId())
                .now(Instant.now().getEpochSecond())
                .rawBody("{\"data\":{\"error_code\":0,\"image\":{\"image_id\":\"mock_image\"}}}")
                .build();
    }

    @Override
    public DouyinCreateImageTextResponse createImageText(DouyinCreateImageTextRequest request) {
        failIfTokenExpired();
        if (fault().isRateLimit()) {
            throw mapped(new DouyinRateLimitException(
                    200, ERROR_RATE_LIMIT, "mock quota used up", mockLogId(), false, "{}"
            ));
        }
        if (fault().isPermissionDenied()) {
            throw mapped(new DouyinPermissionException(
                    200, ERROR_PERMISSION_DENIED, "mock permission denied", mockLogId(), false, "{}"
            ));
        }
        if (fault().isCreateFailed()) {
            throw new DouyinClientException(
                    200, ERROR_CREATE_FAILED, "mock create image-text failed", mockLogId(), true, "{}"
            );
        }
        return DouyinCreateImageTextResponse.builder()
                .itemId("mock_item_" + suffix())
                .videoId("mock_video_" + suffix())
                .errorCode(0L)
                .description("success")
                .logId(mockLogId())
                .now(Instant.now().getEpochSecond())
                .mockReviewOutcome(normalizeReviewOutcome(fault().getReviewOutcome()))
                .rawBody("{\"data\":{\"error_code\":0,\"item_id\":\"mock_item\",\"video_id\":\"mock_video\"}}")
                .build();
    }

    private void failIfTokenExpired() {
        if (fault().isTokenExpired()) {
            throw mapped(new DouyinAuthException(
                    200, ERROR_TOKEN_EXPIRED, "mock access token expired", mockLogId(), false, "{}"
            ));
        }
    }

    private DouyinClientProperties.Fault fault() {
        return properties.getFault() == null ? new DouyinClientProperties.Fault() : properties.getFault();
    }

    private DouyinClientException mapped(DouyinClientException exception) {
        return DouyinErrorMapper.toException(
                exception.getHttpStatus(),
                exception.getErrorCode(),
                exception.getDescription(),
                exception.getLogId(),
                exception.getRawBody()
        );
    }

    private String normalizeReviewOutcome(String reviewOutcome) {
        if (reviewOutcome == null || reviewOutcome.isBlank()) {
            return "passed";
        }
        return reviewOutcome.trim().toLowerCase();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String mockLogId() {
        return "mock_log_" + suffix();
    }
}
