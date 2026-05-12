package com.huanjing.geo.module.content.wechat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WechatOpenPlatformMessageServiceTest {

    private WechatQueryAuthCodeAsyncService queryAuthCodeAsyncService;
    private WechatOpenPlatformMessageService service;

    @BeforeEach
    void setUp() {
        queryAuthCodeAsyncService = mock(WechatQueryAuthCodeAsyncService.class);
        service = new WechatOpenPlatformMessageService(queryAuthCodeAsyncService);
    }

    @Test
    void textDetectionMessageRepliesWithOfficialCallback() {
        Map<String, String> reply = parseReply(service.handleAuthorizerMessage("wx-authorizer",
                textMessage("TESTCOMPONENT_MSG_TYPE_TEXT")));

        assertThat(reply.get("ToUserName")).isEqualTo("from-openid");
        assertThat(reply.get("FromUserName")).isEqualTo("to-appid");
        assertThat(reply.get("MsgType")).isEqualTo("text");
        assertThat(reply.get("Content")).isEqualTo("TESTCOMPONENT_MSG_TYPE_TEXT_callback");
        assertThat(reply.get("CreateTime")).isNotBlank();
    }

    @Test
    void arbitraryTextMessageRepliesWithContentCallback() {
        Map<String, String> reply = parseReply(service.handleAuthorizerMessage("wx-authorizer",
                textMessage("hello world")));

        assertThat(reply.get("Content")).isEqualTo("hello world_callback");
    }

    @Test
    void subscribeEventRepliesWithEventCallback() {
        Map<String, String> reply = parseReply(service.handleAuthorizerMessage("wx-authorizer",
                eventMessage("subscribe")));

        assertThat(reply.get("Content")).isEqualTo("subscribefrom_callback");
    }

    @Test
    void clickEventPreservesCaseInCallback() {
        Map<String, String> reply = parseReply(service.handleAuthorizerMessage("wx-authorizer",
                eventMessage("CLICK")));

        assertThat(reply.get("Content")).isEqualTo("CLICKfrom_callback");
    }

    @Test
    void viewEventPreservesCaseInCallback() {
        Map<String, String> reply = parseReply(service.handleAuthorizerMessage("wx-authorizer",
                eventMessage("VIEW")));

        assertThat(reply.get("Content")).isEqualTo("VIEWfrom_callback");
    }

    @Test
    void queryAuthCodeReturnsSuccessImmediatelyAndDelegatesAsyncHandling() {
        long startedAt = System.currentTimeMillis();

        String response = service.handleAuthorizerMessage("wx-authorizer",
                textMessage("QUERY_AUTH_CODE:queryauthcode@@@12345678"));

        assertThat(response).isEqualTo("success");
        assertThat(System.currentTimeMillis() - startedAt).isLessThan(500);
        verify(queryAuthCodeAsyncService).handle(
                eq("wx-authorizer"),
                eq("from-openid"),
                eq("queryauthcode@@@12345678"),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    private Map<String, String> parseReply(String reply) {
        return WechatXmlParser.parse(reply);
    }

    private String textMessage(String content) {
        return """
                <xml>
                  <ToUserName><![CDATA[to-appid]]></ToUserName>
                  <FromUserName><![CDATA[from-openid]]></FromUserName>
                  <CreateTime>1710000000</CreateTime>
                  <MsgType><![CDATA[text]]></MsgType>
                  <Content><![CDATA[%s]]></Content>
                </xml>
                """.formatted(content);
    }

    private String eventMessage(String event) {
        return """
                <xml>
                  <ToUserName><![CDATA[to-appid]]></ToUserName>
                  <FromUserName><![CDATA[from-openid]]></FromUserName>
                  <CreateTime>1710000000</CreateTime>
                  <MsgType><![CDATA[event]]></MsgType>
                  <Event><![CDATA[%s]]></Event>
                </xml>
                """.formatted(event);
    }
}
