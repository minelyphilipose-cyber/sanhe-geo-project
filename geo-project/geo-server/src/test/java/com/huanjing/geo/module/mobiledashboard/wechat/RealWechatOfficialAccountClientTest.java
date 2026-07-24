package com.huanjing.geo.module.mobiledashboard.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.util.HttpClientUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class RealWechatOfficialAccountClientTest {
    private final RealWechatOfficialAccountClient client =
            new RealWechatOfficialAccountClient(new ObjectMapper());

    @Test
    void mapsWechatCredentialErrorWithoutLeakingCredentials() {
        try (MockedStatic<HttpClientUtil> http = mockStatic(HttpClientUtil.class)) {
            http.when(() -> HttpClientUtil.get(anyString(), anyMap(), eq(5000), eq(15000)))
                    .thenReturn(new HttpClientUtil.HttpResult(
                            200,
                            "{\"errcode\":40013,\"errmsg\":\"invalid appid\"}",
                            Map.of()
                    ));

            BizException exception = assertThrows(
                    BizException.class,
                    () -> client.getAccessToken("wx_invalid", "top-secret")
            );

            assertThat(exception.getCode()).isEqualTo(40013);
            assertThat(exception.getMessage()).doesNotContain("top-secret");
        }
    }

    @Test
    void mapsNonSuccessHttpStatusToBadGateway() {
        try (MockedStatic<HttpClientUtil> http = mockStatic(HttpClientUtil.class)) {
            http.when(() -> HttpClientUtil.get(anyString(), anyMap(), eq(5000), eq(15000)))
                    .thenReturn(new HttpClientUtil.HttpResult(503, "unavailable", Map.of()));

            BizException exception = assertThrows(
                    BizException.class,
                    () -> client.getJsapiTicket("token")
            );

            assertThat(exception.getCode()).isEqualTo(502);
        }
    }

    @Test
    void rejectsSuccessfulResponseMissingRequiredCredential() {
        try (MockedStatic<HttpClientUtil> http = mockStatic(HttpClientUtil.class)) {
            http.when(() -> HttpClientUtil.get(anyString(), anyMap(), eq(5000), eq(15000)))
                    .thenReturn(new HttpClientUtil.HttpResult(200, "{\"expires_in\":7200}", Map.of()));

            BizException exception = assertThrows(
                    BizException.class,
                    () -> client.getAccessToken("wx_test", "secret")
            );

            assertThat(exception.getCode()).isEqualTo(502);
            assertThat(exception.getMessage()).contains("access_token");
        }
    }
}
