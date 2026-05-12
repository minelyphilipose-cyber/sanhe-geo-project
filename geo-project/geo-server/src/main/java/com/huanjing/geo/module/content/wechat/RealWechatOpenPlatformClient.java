package com.huanjing.geo.module.content.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.util.HttpClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "geo.wechat.client", name = "mode", havingValue = "real")
public class RealWechatOpenPlatformClient implements WechatOpenPlatformClient {
    private static final String API_BASE = "https://api.weixin.qq.com";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int REQUEST_TIMEOUT_MS = 15000;

    private final ObjectMapper objectMapper;
    private final WechatApiErrorHandler errorHandler;

    @Override
    public ComponentAccessTokenResult getComponentAccessToken(String componentAppid, String componentAppSecret, String ticket) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("component_appid", componentAppid);
        body.put("component_appsecret", componentAppSecret);
        body.put("component_verify_ticket", ticket);
        JsonNode root = post("/cgi-bin/component/api_component_token", null, body);
        return new ComponentAccessTokenResult(requiredText(root, "component_access_token"), root.path("expires_in").asInt(7200));
    }

    @Override
    public PreAuthCodeResult createPreAuthCode(String componentAccessToken, String componentAppid) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("component_appid", componentAppid);
        JsonNode root = post("/cgi-bin/component/api_create_preauthcode", componentAccessToken, body);
        return new PreAuthCodeResult(requiredText(root, "pre_auth_code"), root.path("expires_in").asInt(600));
    }

    @Override
    public QueryAuthResult queryAuth(String componentAccessToken, String componentAppid, String authCode) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("component_appid", componentAppid);
        body.put("authorization_code", authCode);
        JsonNode root = post("/cgi-bin/component/api_query_auth", componentAccessToken, body);
        JsonNode info = root.path("authorization_info");
        return new QueryAuthResult(
                requiredText(info, "authorizer_appid"),
                requiredText(info, "authorizer_access_token"),
                requiredText(info, "authorizer_refresh_token"),
                info.path("expires_in").asInt(7200),
                info.path("func_info").toString()
        );
    }

    @Override
    public AuthorizerInfoResult getAuthorizerInfo(String componentAccessToken, String componentAppid, String authorizerAppid) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("component_appid", componentAppid);
        body.put("authorizer_appid", authorizerAppid);
        JsonNode root = post("/cgi-bin/component/api_get_authorizer_info", componentAccessToken, body);
        JsonNode info = root.path("authorizer_info");
        JsonNode auth = root.path("authorization_info");
        return new AuthorizerInfoResult(
                info.path("nick_name").asText(null),
                info.path("head_img").asText(null),
                info.path("qrcode_url").asText(null),
                info.path("principal_name").asText(null),
                info.path("verify_type_info").toString(),
                auth.path("func_info").toString()
        );
    }

    @Override
    public AuthorizerTokenResult refreshAuthorizerToken(String componentAccessToken,
                                                        String componentAppid,
                                                        String authorizerAppid,
                                                        String refreshToken) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("component_appid", componentAppid);
        body.put("authorizer_appid", authorizerAppid);
        body.put("authorizer_refresh_token", refreshToken);
        JsonNode root = post("/cgi-bin/component/api_authorizer_token", componentAccessToken, body);
        return new AuthorizerTokenResult(
                requiredText(root, "authorizer_access_token"),
                requiredText(root, "authorizer_refresh_token"),
                root.path("expires_in").asInt(7200)
        );
    }

    private JsonNode post(String path, String accessToken, JsonNode body) {
        try {
            String url = API_BASE + path + (accessToken == null ? "" : "?component_access_token=" + accessToken);
            HttpClientUtil.HttpResult response = HttpClientUtil.postJson(
                    url,
                    Map.of("Content-Type", "application/json"),
                    objectMapper.writeValueAsString(body),
                    CONNECT_TIMEOUT_MS,
                    REQUEST_TIMEOUT_MS
            );
            JsonNode root = objectMapper.readTree(response.body() == null ? "{}" : response.body());
            errorHandler.throwIfError(path, root);
            return root;
        } catch (BizException ex) {
            log.warn("WeChat open platform api failed path={} code={} message={}", path, ex.getCode(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("WeChat open platform request error path={}", path, ex);
            throw new BizException(500, "wechat open platform request failed");
        }
    }

    private String requiredText(JsonNode root, String field) {
        String value = root.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new BizException(500, "wechat response missing " + field);
        }
        return value;
    }
}
