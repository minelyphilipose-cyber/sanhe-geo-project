package com.huanjing.geo.module.mobiledashboard.wechat;

import com.huanjing.geo.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatCredentialServicesTest {
    private static final String APP_ID = "wx_test";

    private MobileDashboardWechatJsSdkProperties properties;
    private WechatOfficialAccountClient client;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;
    private WechatRedisSingleFlight singleFlight;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        properties = new MobileDashboardWechatJsSdkProperties();
        properties.setAppId(APP_ID);
        properties.setAppSecret("secret");
        client = mock(WechatOfficialAccountClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        singleFlight = new WechatRedisSingleFlight(redisTemplate, ignored -> {
        });
    }

    @Test
    void accessTokenRefreshPublishesWithSafetyMargin() {
        when(values.get(anyString())).thenReturn(null);
        when(values.setIfAbsent(anyString(), anyString(), eq(WechatRedisSingleFlight.LOCK_TTL)))
                .thenReturn(true);
        when(client.getAccessToken(APP_ID, "secret"))
                .thenReturn(new WechatOfficialAccountClient.AccessTokenResult("token", 7200));
        WechatOfficialAccessTokenService service =
                new WechatOfficialAccessTokenService(properties, client, redisTemplate, singleFlight);

        assertThat(service.getAccessToken()).isEqualTo("token");
        verify(values).set(
                "wechat:mobile_dashboard:official_access_token:" + APP_ID,
                "token",
                Duration.ofSeconds(6900)
        );
    }

    @Test
    void ticketRefreshEvictsAndRetriesAnExpiredAccessTokenOnce() {
        when(values.get(anyString())).thenReturn(null);
        when(values.setIfAbsent(anyString(), anyString(), eq(WechatRedisSingleFlight.LOCK_TTL)))
                .thenReturn(true);
        WechatOfficialAccessTokenService accessTokenService = mock(WechatOfficialAccessTokenService.class);
        when(accessTokenService.getAccessToken()).thenReturn("expired-token", "fresh-token");
        when(client.getJsapiTicket("expired-token")).thenThrow(new BizException(40001, "invalid credential"));
        when(client.getJsapiTicket("fresh-token"))
                .thenReturn(new WechatOfficialAccountClient.JsapiTicketResult("ticket", 7200));
        WechatJsapiTicketService service = new WechatJsapiTicketService(
                properties,
                client,
                accessTokenService,
                redisTemplate,
                singleFlight
        );

        assertThat(service.getTicket()).isEqualTo("ticket");
        verify(accessTokenService).evict();
        verify(values).set(
                "wechat:mobile_dashboard:jsapi_ticket:" + APP_ID,
                "ticket",
                Duration.ofSeconds(6900)
        );
    }
}
