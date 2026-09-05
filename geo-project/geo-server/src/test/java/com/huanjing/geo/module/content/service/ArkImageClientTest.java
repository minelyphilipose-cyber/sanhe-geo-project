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
