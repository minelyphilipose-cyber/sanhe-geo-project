package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticInputMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticTestMode;
import com.huanjing.geo.module.system.modeldiagnostic.execution.ModelDiagnosticExecutionCommand;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticRequestFactoryTest {

    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private ModelDiagnosticRequestFactory factory;

    @BeforeEach
    void setUp() {
        SysUser operator = new SysUser();
        operator.setId(7L);
        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        factory = new ModelDiagnosticRequestFactory(
                currentUserService, new ModelDiagnosticProbeCatalog());
    }

    @Test
    void freeChatUsesClientTextWithoutProbeFields() {
        ModelDiagnosticExecutionCommand command = factory.create(request(
                ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.FREE_CHAT,
                null, "  system  ", "  hello  "));

        assertEquals(7L, command.operatorId());
        assertEquals("system", command.systemPrompt());
        assertEquals("hello", command.resolvedUserMessage());
        assertNull(command.clientUserMessage());
        assertNull(command.probeVersion());
        verify(currentUserService).ensurePermission("ai.platform.diagnose");
    }

    @Test
    void standardProbeIsResolvedEntirelyByServer() {
        ModelDiagnosticExecutionCommand command = factory.create(request(
                ModelDiagnosticMode.WEB_SEARCH, ModelDiagnosticTestMode.STANDARD_PROBE,
                "web_search_news", null, null));

        assertEquals("v1", command.probeVersion());
        assertNull(command.templateVersion());
        assertNull(command.inputMode());
        assertNull(command.clientUserMessage());
        assertEquals("请联网搜索今天的热点新闻，列出三条，并为每条内容标注可访问的来源。",
                command.resolvedUserMessage());
    }

    @Test
    void productionTemplateKeepsClientIdentitySeparateFromResolvedText() {
        ModelDiagnosticExecutionCommand command = factory.create(request(
                ModelDiagnosticMode.WEB_SEARCH,
                ModelDiagnosticTestMode.PRODUCTION_POLL_TEMPLATE,
                "production_poll_question", null, "  用户问题  "));

        assertEquals(ModelDiagnosticInputMode.USER_REQUIRED, command.inputMode());
        assertEquals("poll-template-v1", command.templateVersion());
        assertEquals("用户问题", command.clientUserMessage());
        assertEquals("用户问题", command.resolvedUserMessage());
    }

    @Test
    void incompatibleOrClientOverriddenProbeIsRejectedAsBadRequest() {
        BizException incompatible = assertThrows(BizException.class, () -> factory.create(request(
                ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.STANDARD_PROBE,
                "web_search_news", null, null)));
        assertEquals(400, incompatible.getHttpStatus());

        BizException overridden = assertThrows(BizException.class, () -> factory.create(request(
                ModelDiagnosticMode.WEB_SEARCH, ModelDiagnosticTestMode.STANDARD_PROBE,
                "web_search_news", "client system", null)));
        assertEquals(400, overridden.getHttpStatus());
    }

    @Test
    void malformedCanonicalUuidIsRejectedAsBadRequest() {
        ModelDiagnosticRunRequest invalid = new ModelDiagnosticRunRequest(
                "not-a-uuid", UUID.randomUUID().toString(), 2L,
                ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticTestMode.FREE_CHAT,
                null, null, "hello");

        BizException error = assertThrows(BizException.class, () -> factory.create(invalid));

        assertEquals(400, error.getHttpStatus());
    }

    private ModelDiagnosticRunRequest request(ModelDiagnosticMode mode,
                                              ModelDiagnosticTestMode testMode,
                                              String probeCode,
                                              String systemPrompt,
                                              String userMessage) {
        return new ModelDiagnosticRunRequest(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), 2L,
                mode, testMode, probeCode, systemPrompt, userMessage);
    }
}
