package com.huanjing.geo.module.content.authoritymedia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.util.HttpClientUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeititejiaClientTest {

    private MeititejiaProperties properties;
    private TestClient client;

    @BeforeEach
    void setUp() {
        properties = new MeititejiaProperties();
        properties.setEnabled(true);
        properties.setMockMode(false);
        properties.setBaseUrl("https://vendor.example/root/");
        properties.setSecretId("sid");
        properties.setSecretKey("key");
        properties.setRateLimitQps(1000);
        properties.setRetryMaxAttempts(1);
        properties.setRetryBackoffMs(0);
        client = new TestClient(properties);
    }

    @Test
    void listResources_postsSignedFormToTypeSpecificPath() {
        JsonNode response = client.listResources(MeititejiaResourceType.NEWS_MEDIA, 2, 100, 99L, 1710000000L);

        assertThat(response.path("code").asInt()).isEqualTo(200);
        assertThat(client.lastUrl).isEqualTo("https://vendor.example/root/media_lst");
        assertThat(client.lastBody).contains("page=2");
        assertThat(client.lastBody).contains("limit=100");
        assertThat(client.lastBody).contains("id=99");
        assertThat(client.lastBody).contains("uptime=1710000000");
        assertThat(client.lastBody).contains("secret_id=sid");
        assertThat(client.lastBody).contains("signature=");
    }

    @Test
    void getIds_usesResourceTypeMapping() {
        client.getIds(MeititejiaResourceType.OVERSEAS);

        assertThat(client.lastUrl).isEqualTo("https://vendor.example/root/get_ids");
        assertThat(client.lastBody).contains("status=1");
        assertThat(client.lastBody).contains("type=7");
    }

    @Test
    void createNewsMediaOrder_encodesTextFieldsBeforeSigningAndDoesNotDoubleEncodeBody() {
        client.createNewsMediaOrder(new MeititejiaClient.NewsMediaOrderRequest(
                "标题 A*~",
                "稿件链接 : <a href=\"https://p.example/a b\">https://p.example/a b</a>",
                135L,
                "AM-1",
                "备注 A",
                "2026-05-08 18:00:00",
                new BigDecimal("100.00")
        ));

        assertThat(client.lastUrl).isEqualTo("https://vendor.example/root/create_media_order");
        assertThat(client.lastBody).contains("title=%E6%A0%87%E9%A2%98+A*~");
        assertThat(client.lastBody).contains("remark=%E5%A4%87%E6%B3%A8+A");
        assertThat(client.lastBody).contains("published_at=2026-05-08+18%3A00%3A00");
        assertThat(client.lastBody).contains("saling_price=100");
        assertThat(client.lastCanonical).contains("published_at=2026-05-08+18%3A00%3A00");
        assertThat(client.lastBody).doesNotContain("%25E6%25A0%2587");
    }

    @Test
    void queryOrders_joinsExternalNos() {
        client.queryOrders(MeititejiaResourceType.NEWS_MEDIA, java.util.List.of("AM-1", " AM-2 "));

        assertThat(client.lastUrl).isEqualTo("https://vendor.example/root/query_media_order");
        assertThat(client.lastBody).contains("nostr=AM-1%2CAM-2");
    }

    @Test
    void queryOrders_rejectsEmptyNoList() {
        assertThatThrownBy(() -> client.queryOrders(MeititejiaResourceType.NEWS_MEDIA, java.util.List.of(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("externalNos");
    }

    @Test
    void sanitizeForAudit_removesSigningCredentials() {
        Map<String, String> signed = new LinkedHashMap<>();
        signed.put("title", "encoded-title");
        signed.put("secret_id", "sid");
        signed.put("timestamp", "1710000000");
        signed.put("signature", "abc");

        assertThat(MeititejiaClient.sanitizeForAudit(signed))
                .containsEntry("title", "encoded-title")
                .doesNotContainKeys("secret_id", "timestamp", "signature");
    }

    @Test
    void buildAuditPayload_encodesBusinessFieldsWithoutSigningCredentials() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", "标题 A");
        params.put("saling_price", new BigDecimal("100.00"));

        Map<String, String> payload = client.buildAuditPayload(params);

        assertThat(payload)
                .containsEntry("title", "%E6%A0%87%E9%A2%98+A")
                .containsEntry("saling_price", "100")
                .doesNotContainKeys("secret_id", "timestamp", "signature");
    }

    @Test
    void postSigned_http500ThrowsRetryableApiException() {
        client.nextStatus = 500;
        client.nextBody = "server error";

        assertThatThrownBy(() -> client.userInfo())
                .isInstanceOfSatisfying(MeititejiaApiException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(500);
                    assertThat(ex.isRetryable()).isTrue();
                    assertThat(ex.getRequestPath()).isEqualTo("userInfo");
                    assertThat(ex.getResponseBody()).isEqualTo("server error");
                });
    }

    @Test
    void postSigned_http200BusinessErrorThrowsNonRetryableApiException() {
        client.nextStatus = 200;
        client.nextBody = "{\"code\":201,\"msg\":\"签名错误\"}";

        assertThatThrownBy(() -> client.userInfo())
                .isInstanceOfSatisfying(MeititejiaApiException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(200);
                    assertThat(ex.getBizCode()).isEqualTo(201);
                    assertThat(ex.getBizMsg()).isEqualTo("签名错误");
                    assertThat(ex.isRetryable()).isFalse();
                });
    }

    @Test
    void postSigned_networkExceptionThrowsRetryableApiException() {
        client.nextException = new java.io.IOException("connection reset");

        assertThatThrownBy(() -> client.userInfo())
                .isInstanceOfSatisfying(MeititejiaApiException.class, ex -> {
                    assertThat(ex.getHttpStatus()).isEqualTo(0);
                    assertThat(ex.isRetryable()).isTrue();
                    assertThat(ex.getBizMsg()).contains("connection reset");
                });
    }

    private static class TestClient extends MeititejiaClient {
        private String lastUrl;
        private String lastBody;
        private String lastCanonical;
        private int nextStatus = 200;
        private String nextBody = "{\"code\":200,\"msg\":\"success\",\"data\":{}}";
        private Exception nextException;

        TestClient(MeititejiaProperties properties) {
            super(properties, null, new ObjectMapper());
        }

        @Override
        protected HttpClientUtil.HttpResult postForm(String url,
                                                     Map<String, String> headers,
                                                     String body,
                                                     int connectTimeoutMs,
                                                     int requestTimeoutMs) throws Exception {
            this.lastUrl = url;
            this.lastBody = body;
            if (nextException != null) {
                throw nextException;
            }
            return new HttpClientUtil.HttpResult(nextStatus, nextBody, Map.of());
        }

        @Override
        protected void throttle() {
        }

        @Override
        protected Map<String, String> signedParametersForRequest(Map<String, ?> params) {
            Map<String, String> signed = super.signedParametersForRequest(params);
            this.lastCanonical = MeititejiaSigner.canonicalString(signed);
            return signed;
        }
    }
}
