package com.huanjing.geo.module.content.wechat;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.WechatCallbackEvent;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.WechatCallbackEventMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatOpenPlatformEventServiceTest {

    private WechatComponentTicketService ticketService;
    private SelfMediaAccountMapper accountMapper;
    private WechatMpAuthorizationService authorizationService;
    private WechatCallbackEventMapper callbackEventMapper;
    private WechatOpenPlatformEventService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), SelfMediaAccount.class);
        ticketService = mock(WechatComponentTicketService.class);
        accountMapper = mock(SelfMediaAccountMapper.class);
        authorizationService = mock(WechatMpAuthorizationService.class);
        callbackEventMapper = mock(WechatCallbackEventMapper.class);
        service = new WechatOpenPlatformEventService(ticketService, accountMapper, authorizationService, callbackEventMapper);
    }

    @Test
    void authorizedEventPersistsAuthorizerInfo() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setPlatformAccountId("wx-authorizer");
        account.setStatus("active");
        when(authorizationService.saveOrUpdateAuthorization("component-appid", "auth-code"))
                .thenReturn(account);

        String response = service.handleComponentEvent(componentEvent("authorized", "auth-code"));

        assertThat(response).isEqualTo("success");
        verify(authorizationService).saveOrUpdateAuthorization("component-appid", "auth-code");
        ArgumentCaptor<WechatCallbackEvent> captor = ArgumentCaptor.forClass(WechatCallbackEvent.class);
        verify(callbackEventMapper).insert(captor.capture());
        assertThat(captor.getValue().getCallbackType()).isEqualTo("component_event");
        assertThat(captor.getValue().getEventType()).isEqualTo("authorized");
        assertThat(captor.getValue().getProcessStatus()).isEqualTo("success");
    }

    @Test
    void updateAuthorizedEventPersistsRefreshTokenThroughSharedAuthorizationService() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setPlatformAccountId("wx-authorizer");
        account.setStatus("active");
        when(authorizationService.saveOrUpdateAuthorization("component-appid", "new-auth-code"))
                .thenReturn(account);

        String response = service.handleComponentEvent(componentEvent("updateauthorized", "new-auth-code"));

        assertThat(response).isEqualTo("success");
        verify(authorizationService).saveOrUpdateAuthorization("component-appid", "new-auth-code");
    }

    @Test
    void unauthorizedEventMarksAccountRevoked() {
        String response = service.handleComponentEvent(componentEvent("unauthorized", null));

        assertThat(response).isEqualTo("success");
        verify(accountMapper).update(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingAuthorizationCodeDoesNotCallQueryAuth() {
        String response = service.handleComponentEvent(componentEvent("authorized", null));

        assertThat(response).isEqualTo("success");
        verify(authorizationService, never()).saveOrUpdateAuthorization(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void authorizationPersistFailureStillReturnsSuccess() {
        when(authorizationService.saveOrUpdateAuthorization("component-appid", "auth-code"))
                .thenThrow(new RuntimeException("db down"));

        String response = service.handleComponentEvent(componentEvent("authorized", "auth-code"));

        assertThat(response).isEqualTo("success");
    }

    private String componentEvent(String infoType, String authorizationCode) {
        String codeXml = authorizationCode == null
                ? ""
                : "<AuthorizationCode><![CDATA[" + authorizationCode + "]]></AuthorizationCode>";
        return """
                <xml>
                  <AppId><![CDATA[component-appid]]></AppId>
                  <InfoType><![CDATA[%s]]></InfoType>
                  <AuthorizerAppid><![CDATA[wx-authorizer]]></AuthorizerAppid>
                  %s
                </xml>
                """.formatted(infoType, codeXml);
    }
}
