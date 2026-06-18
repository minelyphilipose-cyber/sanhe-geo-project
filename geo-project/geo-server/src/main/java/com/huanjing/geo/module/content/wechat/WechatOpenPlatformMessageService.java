package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.module.content.entity.WechatCallbackEvent;
import com.huanjing.geo.module.content.mapper.WechatCallbackEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatOpenPlatformMessageService {
    private static final String DETECTION_TEXT = "TESTCOMPONENT_MSG_TYPE_TEXT";
    private static final String QUERY_AUTH_CODE_PREFIX = "QUERY_AUTH_CODE:";

    private final WechatQueryAuthCodeAsyncService queryAuthCodeAsyncService;
    private final WechatCallbackEventMapper callbackEventMapper;

    public String handleAuthorizerMessage(String authorizerAppid, String rawXml) {
        long startedAt = System.currentTimeMillis();
        LocalDateTime receivedAt = LocalDateTime.now();
        Map<String, String> xml = WechatXmlParser.parse(rawXml);
        String response = "success";
        String processStatus = "success";
        String processError = null;
        try {
            String msgType = xml.get("MsgType");
            if ("event".equals(msgType)) {
                String event = xml.get("Event");
                response = WechatXmlParser.textReply(xml.get("FromUserName"), xml.get("ToUserName"),
                        (event == null ? "" : event) + "from_callback");
                return response;
            }
            String content = xml.get("Content");
            if (DETECTION_TEXT.equals(content)) {
                response = WechatXmlParser.textReply(xml.get("FromUserName"), xml.get("ToUserName"),
                        DETECTION_TEXT + "_callback");
                return response;
            }
            if (StringUtils.hasText(content) && content.startsWith(QUERY_AUTH_CODE_PREFIX)) {
                queryAuthCodeAsyncService.handle(authorizerAppid, xml.get("FromUserName"),
                        content.substring(QUERY_AUTH_CODE_PREFIX.length()), startedAt);
                response = "";
                return response;
            }
            if ("text".equals(msgType) && content != null) {
                response = WechatXmlParser.textReply(xml.get("FromUserName"), xml.get("ToUserName"),
                        content + "_callback");
                return response;
            }
            return response;
        } catch (RuntimeException ex) {
            processStatus = "failed";
            processError = safeMessage(ex);
            throw ex;
        } finally {
            saveAudit(authorizerAppid, rawXml, xml, response, receivedAt, processStatus, processError);
        }
    }

    private void saveAudit(String authorizerAppid,
                           String rawXml,
                           Map<String, String> xml,
                           String response,
                           LocalDateTime receivedAt,
                           String processStatus,
                           String processError) {
        try {
            WechatCallbackEvent event = new WechatCallbackEvent();
            event.setCallbackType("authorizer_message");
            event.setAuthorizerAppid(authorizerAppid);
            event.setEventType(xml.get("Event"));
            event.setMsgType(xml.get("MsgType"));
            event.setOpenid(xml.get("FromUserName"));
            event.setRawXml(rawXml);
            event.setDecryptedXml(rawXml);
            event.setResponseBody(response);
            event.setProcessStatus(processStatus);
            event.setProcessError(processError);
            event.setReceivedAt(receivedAt);
            event.setProcessedAt(LocalDateTime.now());
            callbackEventMapper.insert(event);
        } catch (Exception ex) {
            log.warn("Failed to persist WeChat authorizer message audit");
        }
    }

    private String safeMessage(RuntimeException ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
