package com.huanjing.geo.module.content.douyin.client;

import com.huanjing.geo.module.content.config.DouyinClientProperties;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCodeTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextResponse;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinImageUploadRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinRefreshAccessTokenRequest;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinAuthException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinClientException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinPermissionException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinRateLimitException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockDouyinClientTest {

    @Test
    void exchangeCodeForToken_success_returnsMockToken() {
        MockDouyinClient client = client(new DouyinClientProperties.Fault());

        var response = client.exchangeCodeForToken(DouyinCodeTokenRequest.builder()
                .clientKey("client-key")
                .clientSecret("secret")
                .code("code")
                .build());

        assertEquals(0L, response.getErrorCode());
        assertTrue(response.getAccessToken().startsWith("mock_douyin_access_token_"));
        assertTrue(response.getRefreshToken().startsWith("mock_douyin_refresh_token_"));
        assertEquals("mock_open_id", response.getOpenId());
        assertEquals(7200L, response.getExpiresIn());
        assertEquals("video.create.bind", response.getScope());
    }

    @Test
    void refreshAccessToken_success_returnsMockToken() {
        MockDouyinClient client = client(new DouyinClientProperties.Fault());

        var response = client.refreshAccessToken(DouyinRefreshAccessTokenRequest.builder()
                .clientKey("client-key")
                .refreshToken("refresh-token")
                .build());

        assertEquals(0L, response.getErrorCode());
        assertTrue(response.getAccessToken().startsWith("mock_douyin_refreshed_access_token_"));
        assertTrue(response.getRefreshToken().startsWith("mock_douyin_refreshed_refresh_token_"));
    }

    @Test
    void uploadImage_success_returnsMockImage() {
        MockDouyinClient client = client(new DouyinClientProperties.Fault());

        var response = client.uploadImage(DouyinImageUploadRequest.builder()
                .accessToken("access-token")
                .openId("open-id")
                .filename("cover.png")
                .contentType("image/png")
                .imageBytes("image".getBytes(StandardCharsets.UTF_8))
                .build());

        assertEquals(0L, response.getErrorCode());
        assertTrue(response.getImageId().startsWith("mock_image_"));
        assertEquals(1080, response.getWidth());
        assertEquals(1440, response.getHeight());
    }

    @Test
    void createImageText_success_returnsMockItemAndReviewMarker() {
        DouyinClientProperties.Fault fault = new DouyinClientProperties.Fault();
        fault.setReviewOutcome("rejected");
        MockDouyinClient client = client(fault);

        DouyinCreateImageTextResponse response = client.createImageText(DouyinCreateImageTextRequest.builder()
                .accessToken("access-token")
                .openId("open-id")
                .imageList(List.of("image-1"))
                .text("caption")
                .build());

        assertEquals(0L, response.getErrorCode());
        assertTrue(response.getItemId().startsWith("mock_item_"));
        assertTrue(response.getVideoId().startsWith("mock_video_"));
        assertEquals("rejected", response.getMockReviewOutcome());
    }

    @Test
    void uploadImage_uploadFailed_throwsValidationException() {
        DouyinClientProperties.Fault fault = new DouyinClientProperties.Fault();
        fault.setUploadFailed(true);

        DouyinClientException ex = assertThrows(DouyinValidationException.class,
                () -> client(fault).uploadImage(DouyinImageUploadRequest.builder().build()));

        assertEquals(2100005L, ex.getErrorCode());
        assertEquals(false, ex.isRetryable());
    }

    @Test
    void createImageText_permissionDenied_throwsPermissionException() {
        DouyinClientProperties.Fault fault = new DouyinClientProperties.Fault();
        fault.setPermissionDenied(true);

        DouyinClientException ex = assertThrows(DouyinPermissionException.class,
                () -> client(fault).createImageText(DouyinCreateImageTextRequest.builder().build()));

        assertEquals(28001018L, ex.getErrorCode());
        assertEquals(false, ex.isRetryable());
    }

    @Test
    void createImageText_rateLimit_throwsRateLimitException() {
        DouyinClientProperties.Fault fault = new DouyinClientProperties.Fault();
        fault.setRateLimit(true);

        DouyinClientException ex = assertThrows(DouyinRateLimitException.class,
                () -> client(fault).createImageText(DouyinCreateImageTextRequest.builder().build()));

        assertEquals(28003017L, ex.getErrorCode());
        assertEquals(false, ex.isRetryable());
    }

    @Test
    void createImageText_createFailed_throwsGenericClientException() {
        DouyinClientProperties.Fault fault = new DouyinClientProperties.Fault();
        fault.setCreateFailed(true);

        DouyinClientException ex = assertThrows(DouyinClientException.class,
                () -> client(fault).createImageText(DouyinCreateImageTextRequest.builder().build()));

        assertEquals(DouyinClientException.class, ex.getClass());
        assertEquals(2100004L, ex.getErrorCode());
        assertEquals(true, ex.isRetryable());
    }

    @Test
    void tokenExpired_precedesOtherFaults() {
        DouyinClientProperties.Fault fault = new DouyinClientProperties.Fault();
        fault.setTokenExpired(true);
        fault.setRateLimit(true);
        fault.setPermissionDenied(true);
        fault.setCreateFailed(true);
        fault.setUploadFailed(true);

        DouyinClientException createEx = assertThrows(DouyinAuthException.class,
                () -> client(fault).createImageText(DouyinCreateImageTextRequest.builder().build()));
        DouyinClientException uploadEx = assertThrows(DouyinAuthException.class,
                () -> client(fault).uploadImage(DouyinImageUploadRequest.builder().build()));

        assertEquals(28001008L, createEx.getErrorCode());
        assertEquals(28001008L, uploadEx.getErrorCode());
    }

    @Test
    void beanImplementsDouyinClientInterface() {
        assertInstanceOf(DouyinClient.class, client(new DouyinClientProperties.Fault()));
    }

    private MockDouyinClient client(DouyinClientProperties.Fault fault) {
        DouyinClientProperties properties = new DouyinClientProperties();
        properties.setFault(fault);
        return new MockDouyinClient(properties);
    }
}
