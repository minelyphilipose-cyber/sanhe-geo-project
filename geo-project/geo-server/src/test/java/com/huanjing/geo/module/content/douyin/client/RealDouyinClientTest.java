package com.huanjing.geo.module.content.douyin.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.config.DouyinClientProperties;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCodeTokenRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinCreateImageTextRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinImageUploadRequest;
import com.huanjing.geo.module.content.douyin.client.dto.DouyinRefreshAccessTokenRequest;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinAuthException;
import com.huanjing.geo.module.content.douyin.client.exception.DouyinRateLimitException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealDouyinClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private ExecutorService executor;
    private RealDouyinClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.start();

        DouyinClientProperties properties = new DouyinClientProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setConnectTimeoutMs(1000);
        properties.setRequestTimeoutMs(1000);
        client = new RealDouyinClient(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void exchangeCodeForToken_postsFormAndParsesNestedData() {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        respond("/oauth/access_token/", captured, 200, """
                {"message":"success","data":{"access_token":"access","refresh_token":"refresh","open_id":"open","expires_in":7200,"refresh_expires_in":"2592000","scope":"video.create.bind","error_code":0,"log_id":"log-1"}}
                """);

        var response = client.exchangeCodeForToken(DouyinCodeTokenRequest.builder()
                .clientKey("client-key")
                .clientSecret("secret")
                .code("auth-code")
                .build());

        assertEquals("POST", captured.get().method());
        assertEquals("application/x-www-form-urlencoded", captured.get().contentType());
        assertTrue(captured.get().body().contains("client_key=client-key"));
        assertTrue(captured.get().body().contains("client_secret=secret"));
        assertTrue(captured.get().body().contains("code=auth-code"));
        assertTrue(captured.get().body().contains("grant_type=authorization_code"));
        assertEquals("access", response.getAccessToken());
        assertEquals("refresh", response.getRefreshToken());
        assertEquals("open", response.getOpenId());
        assertEquals(7200L, response.getExpiresIn());
        assertEquals(2592000L, response.getRefreshExpiresIn());
        assertEquals("success", response.getMessage());
        assertTrue(response.getRawBody().contains("access_token"));
    }

    @Test
    void refreshAccessToken_errorCodeThrowsMappedAuthException() {
        respond("/oauth/refresh_token/", new AtomicReference<>(), 200, """
                {"message":"error","data":{"error_code":10010,"description":"refresh expired","log_id":"log-auth"}}
                """);

        var ex = assertThrows(DouyinAuthException.class, () -> client.refreshAccessToken(
                DouyinRefreshAccessTokenRequest.builder()
                        .clientKey("client-key")
                        .refreshToken("refresh-token")
                        .build()));

        assertEquals(10010L, ex.getErrorCode());
        assertEquals("refresh expired", ex.getDescription());
        assertEquals("log-auth", ex.getLogId());
    }

    @Test
    void uploadImage_sendsMultipartAccessTokenAndParsesImage() {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        respond("/api/douyin/v1/video/upload_image/", captured, 200, """
                {"data":{"error_code":0,"description":"success","image":{"image_id":"image-1","width":1080,"height":1440}},"extra":{"logid":"log-upload","now":"1710000000"}}
                """);

        var response = client.uploadImage(DouyinImageUploadRequest.builder()
                .accessToken("token")
                .openId("open id")
                .filename("cover.png")
                .contentType("image/png")
                .imageBytes("image-bytes".getBytes(StandardCharsets.UTF_8))
                .build());

        assertEquals("open_id=open+id", captured.get().query());
        assertEquals("token", captured.get().accessToken());
        assertTrue(captured.get().contentType().startsWith("multipart/form-data; boundary="));
        assertTrue(captured.get().body().contains("name=\"image\"; filename=\"cover.png\""));
        assertTrue(captured.get().body().contains("Content-Type: image/png"));
        assertTrue(captured.get().body().contains("image-bytes"));
        assertEquals("image-1", response.getImageId());
        assertEquals(1080, response.getWidth());
        assertEquals(1440, response.getHeight());
        assertEquals("log-upload", response.getLogId());
    }

    @Test
    void createImageText_sendsJsonWithoutLocalAuthFieldsAndParsesItem() throws Exception {
        AtomicReference<CapturedRequest> captured = new AtomicReference<>();
        respond("/api/douyin/v1/video/create_image_text/", captured, 200, """
                {"data":{"error_code":0,"description":"success","item_id":"item-1","video_id":"video-1"},"extra":{"logid":"log-create","now":1710000001}}
                """);

        var response = client.createImageText(DouyinCreateImageTextRequest.builder()
                .accessToken("token")
                .openId("open-id")
                .imageList(List.of("image-1", "image-2"))
                .text("caption")
                .privateStatus(1)
                .build());

        JsonNode body = objectMapper.readTree(captured.get().body());
        assertEquals("open_id=open-id", captured.get().query());
        assertEquals("token", captured.get().accessToken());
        assertEquals("application/json", captured.get().contentType());
        assertEquals("image-1", body.path("image_list").get(0).asText());
        assertEquals("caption", body.path("text").asText());
        assertEquals(1, body.path("private_status").asInt());
        assertFalse(body.has("accessToken"));
        assertFalse(body.has("openId"));
        assertEquals("item-1", response.getItemId());
        assertEquals("video-1", response.getVideoId());
        assertEquals("log-create", response.getLogId());
    }

    @Test
    void createImageText_rateLimitErrorThrowsMappedException() {
        respond("/api/douyin/v1/video/create_image_text/", new AtomicReference<>(), 200, """
                {"data":{"error_code":28003017,"description":"quota used up"},"extra":{"logid":"log-rate"}}
                """);

        var ex = assertThrows(DouyinRateLimitException.class, () -> client.createImageText(
                DouyinCreateImageTextRequest.builder()
                        .accessToken("token")
                        .openId("open-id")
                        .imageList(List.of("image-1"))
                        .text("caption")
                        .build()));

        assertEquals(28003017L, ex.getErrorCode());
        assertEquals("quota used up", ex.getDescription());
        assertEquals("log-rate", ex.getLogId());
        assertInstanceOf(DouyinRateLimitException.class, ex);
    }

    private void respond(String path, AtomicReference<CapturedRequest> captured, int status, String body) {
        server.createContext(path, exchange -> {
            captured.set(capture(exchange));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    private CapturedRequest capture(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getQuery(),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                exchange.getRequestHeaders().getFirst("access-token"),
                body
        );
    }

    private record CapturedRequest(String method,
                                   String query,
                                   String contentType,
                                   String accessToken,
                                   String body) {
    }
}
