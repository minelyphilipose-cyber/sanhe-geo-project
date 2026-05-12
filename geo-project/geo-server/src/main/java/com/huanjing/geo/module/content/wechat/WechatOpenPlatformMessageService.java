package com.huanjing.geo.module.content.wechat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class WechatOpenPlatformMessageService {
    private static final String DETECTION_TEXT = "TESTCOMPONENT_MSG_TYPE_TEXT";
    private static final String QUERY_AUTH_CODE_PREFIX = "QUERY_AUTH_CODE:";

    private final WechatQueryAuthCodeAsyncService queryAuthCodeAsyncService;

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
            queryAuthCodeAsyncService.handle(authorizerAppid, xml.get("FromUserName"),
                    content.substring(QUERY_AUTH_CODE_PREFIX.length()), startedAt);
            return "success";
        }
        if ("text".equals(msgType) && content != null) {
            return WechatXmlParser.textReply(xml.get("FromUserName"), xml.get("ToUserName"),
                    content + "_callback");
        }
        return "success";
    }
}
