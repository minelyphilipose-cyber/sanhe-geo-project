package com.huanjing.geo.module.content.controller;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.wechat.WechatMessageCryptoService;
import com.huanjing.geo.module.content.wechat.WechatMpAuthorizationService;
import com.huanjing.geo.module.content.wechat.WechatOpenPlatformEventService;
import com.huanjing.geo.module.content.wechat.WechatOpenPlatformMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatOpenPlatformCallbackControllerTest {

    private WechatOpenPlatformEventService eventService;
    private WechatOpenPlatformMessageService messageService;
    private WechatMessageCryptoService cryptoService;
    private WechatOpenPlatformCallbackController controller;

    @BeforeEach
    void setUp() {
        eventService = mock(WechatOpenPlatformEventService.class);
        messageService = mock(WechatOpenPlatformMessageService.class);
        cryptoService = mock(WechatMessageCryptoService.class);
        controller = new WechatOpenPlatformCallbackController(
                eventService,
                messageService,
                cryptoService,
                mock(WechatMpAuthorizationService.class)
        );
    }

    @Test
    void receiveEventReturnsOriginalResponseOnSuccess() {
        when(cryptoService.decryptIfNeeded("<xml/>", "sig", "ts", "nonce")).thenReturn("<xml><InfoType>ok</InfoType></xml>");
        when(eventService.handleComponentEvent("<xml><InfoType>ok</InfoType></xml>")).thenReturn("success");

        String response = controller.receiveEvent("sig", "ts", "nonce", "<xml/>");

        assertThat(response).isEqualTo("success");
    }

    @Test
    void verifyEventUrlReturnsEchoStringWhenSignatureIsValid() {
        String response = controller.verifyEventUrl("sig", null, "ts", "nonce", "echo");

        assertThat(response).isEqualTo("echo");
        verify(cryptoService).verifyUrlSignature("sig", "ts", "nonce", "echo");
    }

    @Test
    void verifyMessageUrlReturnsBlankWhenSignatureIsInvalid() {
        org.mockito.Mockito.doThrow(new BizException(400, "bad signature"))
                .when(cryptoService).verifyUrlSignature("bad", "ts", "nonce", "echo");

        String response = controller.verifyMessageUrl("wx-authorizer", "bad", null, "ts", "nonce", "echo");

        assertThat(response).isBlank();
    }

    @Test
    void receiveEventReturnsSuccessWhenXmlParsingFails() {
        when(cryptoService.decryptIfNeeded("<bad", "sig", "ts", "nonce")).thenReturn("<bad");
        when(eventService.handleComponentEvent("<bad")).thenThrow(new IllegalArgumentException("invalid xml"));

        String response = controller.receiveEvent("sig", "ts", "nonce", "<bad");

        assertThat(response).isEqualTo("success");
    }

    @Test
    void receiveEventReturnsSuccessWhenDecryptFails() {
        when(cryptoService.decryptIfNeeded("<encrypted/>", "bad", "ts", "nonce"))
                .thenThrow(new BizException(400, "wechat message decrypt failed"));

        String response = controller.receiveEvent("bad", "ts", "nonce", "<encrypted/>");

        assertThat(response).isEqualTo("success");
    }

    @Test
    void receiveAuthorizerMessageReturnsOriginalResponseOnSuccess() {
        when(cryptoService.isEncrypted("<xml/>")).thenReturn(false);
        when(cryptoService.decryptIfNeeded("<xml/>", null, "ts", "nonce")).thenReturn("<xml><MsgType>text</MsgType></xml>");
        when(messageService.handleAuthorizerMessage("wx-authorizer", "<xml><MsgType>text</MsgType></xml>"))
                .thenReturn("<xml>reply</xml>");

        String response = controller.receiveAuthorizerMessage("wx-authorizer", null, "ts", "nonce", "<xml/>");

        assertThat(response).isEqualTo("<xml>reply</xml>");
    }

    @Test
    void receiveAuthorizerMessageReturnsSuccessWhenBusinessThrowsBizException() {
        when(cryptoService.isEncrypted("<xml/>")).thenReturn(false);
        when(cryptoService.decryptIfNeeded("<xml/>", null, "ts", "nonce")).thenReturn("<xml><MsgType>text</MsgType></xml>");
        when(messageService.handleAuthorizerMessage("wx-authorizer", "<xml><MsgType>text</MsgType></xml>"))
                .thenThrow(new BizException(500, "business failed"));

        String response = controller.receiveAuthorizerMessage("wx-authorizer", null, "ts", "nonce", "<xml/>");

        assertThat(response).isEqualTo("success");
    }

    @Test
    void receiveAuthorizerMessageReturnsSuccessWhenBusinessThrowsNpe() {
        when(cryptoService.isEncrypted("<xml/>")).thenReturn(false);
        when(cryptoService.decryptIfNeeded("<xml/>", null, "ts", "nonce")).thenReturn("<xml><MsgType>text</MsgType></xml>");
        when(messageService.handleAuthorizerMessage("wx-authorizer", "<xml><MsgType>text</MsgType></xml>"))
                .thenThrow(new NullPointerException("boom"));

        String response = controller.receiveAuthorizerMessage("wx-authorizer", null, "ts", "nonce", "<xml/>");

        assertThat(response).isEqualTo("success");
    }
}
