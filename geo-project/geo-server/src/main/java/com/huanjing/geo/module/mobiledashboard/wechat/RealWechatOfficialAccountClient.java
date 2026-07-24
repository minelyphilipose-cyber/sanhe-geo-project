package com.huanjing.geo.module.mobiledashboard.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "geo.mobile-dashboard.wechat-js-sdk",
        name = "client-mode",
        havingValue = "real"
)
public class RealWechatOfficialAccountClient implements WechatOfficialAccountClient {
    private static final String API_BASE = "https://api.weixin.qq.com";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int REQUEST_TIMEOUT_MS = 15000;

    private final ObjectMapper objectMapper;

    @Override
    public AccessTokenResult getAccessToken(String appId, String appSecret) {
        String url = UriComponentsBuilder.fromUriString(API_BASE + "/cgi-bin/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .build()
                .encode()
                .toUriString();
        JsonNode root = get("/cgi-bin/token", url);
        return new AccessTokenResult(
                requiredText(root, "access_token"),
                root.path("expires_in").asInt(7200)
        );
    }

    @Override
    public JsapiTicketResult getJsapiTicket(String accessToken) {
        String url = UriComponentsBuilder.fromUriString(API_BASE + "/cgi-bin/ticket/getticket")
                .queryParam("access_token", accessToken)
                .queryParam("type", "jsapi")
                .build()
                .encode()
                .toUriString();
        JsonNode root = get("/cgi-bin/ticket/getticket", url);
        return new JsapiTicketResult(
                requiredText(root, "ticket"),
                root.path("expires_in").asInt(7200)
        );
    }

    private JsonNode get(String apiName, String url) {
        try {
            HttpClientUtil.HttpResult response = HttpClientUtil.get(
                    url,
                    Map.of("Accept", "application/json"),
                    CONNECT_TIMEOUT_MS,
                    REQUEST_TIMEOUT_MS
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(502, "wechat official account api returned HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            int errcode = root.path("errcode").asInt(0);
            if (errcode != 0) {
                throw new BizException(errcode, root.path("errmsg").asText("wechat api error"));
            }
            return root;
        } catch (BizException ex) {
            log.warn("WeChat official account API failed api={} code={}", apiName, ex.getCode());
            throw ex;
        } catch (Exception ex) {
            log.error("WeChat official account request failed api={}", apiName, ex);
            throw new BizException(502, "wechat official account request failed");
        }
    }

    private String requiredText(JsonNode root, String field) {
        String value = root.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new BizException(502, "wechat response missing " + field);
        }
        return value;
    }
}
