package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import com.huanjing.geo.module.content.wechat.WechatOpenPlatformClient.QueryAuthResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatOpenPlatformMessageService {
    private static final String DETECTION_TEXT = "TESTCOMPONENT_MSG_TYPE_TEXT";
    private static final String QUERY_AUTH_CODE_PREFIX = "QUERY_AUTH_CODE:";

    private final WechatMpClient wechatMpClient;
    private final WechatOpenPlatformClient openPlatformClient;
    private final WechatComponentAccessTokenService componentAccessTokenService;
    private final WechatOpenPlatformProperties properties;

    public String handleAuthorizerMessage(String authorizerAppid, String rawXml) {
        long startedAt = System.currentTimeMillis();
        Map<String, String> xml = WechatXmlParser.parse(rawXml);
        String msgType = xml.get("MsgType");
        if ("event".equals(msgType)) {
            String event = xml.get("Event");
            return WechatXmlParser.textReply(xml.get("FromUserName"), xml.get("ToUserName"),
                    (event == null ? "" : event) + "from_callback");
        }
        String content = xml.get("Content");
        if (DETECTION_TEXT.equals(content)) {
            return WechatXmlParser.textReply(xml.get("FromUserName"), xml.get("ToUserName"),
                    DETECTION_TEXT + "_callback");
        }
        if (StringUtils.hasText(content) && content.startsWith(QUERY_AUTH_CODE_PREFIX)) {
            handleQueryAuthCode(authorizerAppid, xml.get("FromUserName"),
                    content.substring(QUERY_AUTH_CODE_PREFIX.length()), startedAt);
            return "success";
        }
        if ("text".equals(msgType) && content != null) {
            return WechatXmlParser.textReply(xml.get("FromUserName"), xml.get("ToUserName"),
                    content + "_callback");
        }
        return "success";
    }

    private void handleQueryAuthCode(String authorizerAppid, String openid, String authCode, long startedAt) {
        long queryStart = System.currentTimeMillis();
        String componentToken = componentAccessTokenService.getAccessToken();
        QueryAuthResult auth = openPlatformClient.queryAuth(componentToken, properties.getComponentAppid(), authCode);
        long queryEnd = System.currentTimeMillis();
        wechatMpClient.sendCustomTextMessage(auth.authorizerAccessToken(), openid, authCode + "_from_api");
        long end = System.currentTimeMillis();
        log.info("WeChat QUERY_AUTH_CODE handled authorizerAppid={} openid={} queryAuthMs={} customMsgMs={} totalMs={}",
                authorizerAppid, openid, queryEnd - queryStart, end - queryEnd, end - startedAt);
    }
}
