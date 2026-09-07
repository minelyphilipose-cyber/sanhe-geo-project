package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.io.ByteArrayInputStream;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArkImageClientTest {
    @Test
    void preservesActionableUpstreamErrorWithoutCredentialsOrSignedUrls() throws Exception {
        String message = failedGeneration(400, """
                {"error":{"code":"InputTextSensitiveContentDetected","message":"Request rejected test-secret https://images.volces.com/a?signature=secret-signature","param":"prompt"},"request_id":"request-body-123"}
                """, java.util.Map.of());
        assertTrue(message.contains("HTTP 400"));
        assertTrue(message.contains("InputTextSensitiveContentDetected"));
        assertTrue(message.contains("参数：prompt"));
        assertTrue(message.contains("Request ID：request-body-123"));
        assertFalse(message.contains("test-secret"));
        assertFalse(message.contains("secret-signature"));
    }

    @Test
    void preservesHttpStatusAndHeaderRequestIdWhenBodyIsNotUsable() throws Exception {
        for (String body : new String[]{"", "<html>proxy failure test-secret</html>", "x".repeat(9000)}) {
            String message = failedGeneration(400, body, java.util.Map.of("x-tt-logid", java.util.List.of("header-request-456")));
            assertEquals("豆包生图失败，HTTP 400；Request ID：header-request-456", message);
        }
    }

    @Test
    void boundsLongDiagnosticMessagesToFitPersistedTaskError() throws Exception {
        String message = failedGeneration(429, "{\"error\":{\"code\":\"RateLimitExceeded\",\"message\":\"" + "a".repeat(2000) + "\"}}",
                java.util.Map.of("x-request-id", java.util.List.of("request-789")));
        assertTrue(message.contains("HTTP 429"));
        assertTrue(message.contains("RateLimitExceeded"));
        assertTrue(message.contains("request-789"));
        assertTrue(message.length() < 1000);
    }

    @SuppressWarnings("unchecked")
    private String failedGeneration(int status, String body, java.util.Map<String, java.util.List<String>> headers) throws Exception {
        var credentials = mock(PlatformCredentialService.class);
        when(credentials.resolvePrimaryCredentialStrict(any(), any())).thenReturn("test-secret");
        HttpClient http = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        AiPlatformConfig config = new AiPlatformConfig();
        config.setApiUrl("https://ark.cn-beijing.volces.com/api/v3"); config.setModelId("test-model");
        BizException error = assertThrows(BizException.class,
                () -> new ArkImageClient(new ObjectMapper(), credentials, http).generate(config, "test prompt"));
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        return error.getMessage();
    }

    @Test
    void usesImageEndpointExplicitNoWatermarkAndPortraitSize() {
        assertEquals("https://ark.cn-beijing.volces.com/api/v3/images/generations",
                ArkImageClient.endpoint("https://ark.cn-beijing.volces.com/api/v3/").toString());
        var payload = ArkImageClient.payload("doubao-seedream-5-0-260128", "准确文字");
        assertEquals(false, payload.get("watermark"));
        assertEquals("1440x2560", payload.get("size"));
        assertEquals("disabled", payload.get("sequential_image_generation"));
        assertThrows(BizException.class, () -> ArkImageClient.endpoint("https://ark.cn-beijing.volces.com/api/v3/chat/completions"));
    }

    @Test
    void rejectsUntrustedDownloadsAndNonImages() {
        for (String url : new String[]{"http://localhost/a", "https://127.0.0.1/a", "https://volces.com.evil.test/a", "file:///a"}) {
            assertThrows(BizException.class, () -> ArkImageClient.downloadUri(url));
        }
        assertThrows(BizException.class, () -> ArkImageClient.imageType("<html>error</html>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void downloadsReturnedImageWithoutForwardingApiCredential() throws Exception {
        var credentials = mock(PlatformCredentialService.class);
        when(credentials.resolvePrimaryCredentialStrict(any(), any())).thenReturn("test-secret");
        HttpClient http = mock(HttpClient.class);
        HttpResponse<java.io.InputStream> generated = mock(HttpResponse.class);
        HttpResponse<java.io.InputStream> downloaded = mock(HttpResponse.class);
        when(generated.statusCode()).thenReturn(200);
        when(downloaded.statusCode()).thenReturn(200);
        when(generated.body()).thenReturn(new ByteArrayInputStream("{\"data\":[{\"url\":\"https://images.volces.com/test.png\"}]}".getBytes(StandardCharsets.UTF_8)));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        when(downloaded.body()).thenReturn(new ByteArrayInputStream(png));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(generated, downloaded);
        AiPlatformConfig config = new AiPlatformConfig();
        config.setApiUrl("https://ark.cn-beijing.volces.com/api/v3");
        config.setModelId("doubao-seedream-5-0-260128");
        var image = new ArkImageClient(new ObjectMapper(), credentials, http).generate(config, "标题：材料怎么选");
        assertArrayEquals(png, image.bytes());
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("Bearer test-secret", requests.getAllValues().get(0).headers().firstValue("Authorization").orElseThrow());
        assertTrue(requests.getAllValues().get(1).headers().firstValue("Authorization").isEmpty());
    }
}
