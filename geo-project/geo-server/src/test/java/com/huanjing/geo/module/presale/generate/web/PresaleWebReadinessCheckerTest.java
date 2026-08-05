package com.huanjing.geo.module.presale.generate.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.codec.WebSearchCodec;
import com.huanjing.geo.module.dispatch.websearch.enums.IntegrationType;
import com.huanjing.geo.module.dispatch.websearch.enums.UsageScene;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import com.huanjing.geo.module.presale.generate.web.provider.PresaleWebProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresaleWebReadinessCheckerTest {
    @Mock private AiPlatformConfigMapper mapper;
    @Mock private PlatformCredentialService credentialService;
    @Mock private WebSearchCodec codec;
    @Mock private PresaleWebProvider provider;

    private PresaleWebReadinessChecker checker;
    private PresaleWebQueryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PresaleWebQueryProperties();
        lenient().when(codec.integrationType()).thenReturn(IntegrationType.QIANFAN_ERNIE_CHAT_WEB);
        lenient().when(provider.integrationType()).thenReturn(IntegrationType.QIANFAN_ERNIE_CHAT_WEB);
        lenient().when(provider.requiresCodec()).thenReturn(true);
        checker = new PresaleWebReadinessChecker(mapper, credentialService,
                new ObjectMapper(), properties, List.of(codec), List.of(provider));
    }

    @Test
    void offModeDoesNotInspectDatabase() {
        PresaleWebExecutionContext context = checker.check(PresaleQueryWebMode.OFF);
        assertEquals(PresaleQueryWebMode.OFF, context.mode());
        verify(mapper, never()).selectList(any());
    }

    @Test
    void requiredModeResolvesExactlyOneHealthySameChannelCompanion() {
        AiPlatformConfig base = base();
        AiPlatformConfig companion = companion();
        when(mapper.selectList(any())).thenReturn(List.of(base), List.of(companion));
        when(credentialService.resolvePrimaryCredentialStrict("env://QIANFAN_API_KEY", null))
                .thenReturn("secret");

        PresaleWebExecutionContext context = checker.check(PresaleQueryWebMode.REQUIRED);

        ResolvedCompanionExecutionConfig resolved = context.requireCompanion("wenxin");
        assertEquals(21L, resolved.companionConfigId());
        assertEquals("wenxin_web", resolved.companionPlatformCode());
        assertEquals("env://QIANFAN_API_KEY", resolved.credentialRef());
        assertEquals("文心一言", context.reportPlatforms().get(0).getPlatformName());
        base.setPlatformCode("changed-after-readiness");
        assertEquals("wenxin", context.reportPlatforms().get(0).getPlatformCode());
        assertNull(context.reportPlatforms().get(0).getApiKey());
    }

    @Test
    void requiredModeAllowsEnabledCompanionWhenBasePresaleCapabilityIsDisabled() {
        AiPlatformConfig base = base();
        base.setEnabledForPresale(false);
        AiPlatformConfig companion = companion();
        when(mapper.selectList(any())).thenReturn(List.of(base), List.of(companion));
        when(credentialService.resolvePrimaryCredentialStrict("env://QIANFAN_API_KEY", null))
                .thenReturn("secret");

        PresaleWebExecutionContext context = checker.check(PresaleQueryWebMode.REQUIRED);

        assertEquals(1, context.reportPlatforms().size());
        assertEquals("wenxin", context.reportPlatforms().get(0).getPlatformCode());
        assertEquals("文心一言", context.reportPlatforms().get(0).getPlatformName());
        assertEquals("wenxin_web", context.requireCompanion("wenxin").companionPlatformCode());
        assertTrue(context.usesWebQuery("wenxin"));
    }

    @Test
    void requiredModeUsesNativeWhenNoEnabledCompanionExists() {
        when(mapper.selectList(any())).thenReturn(List.of(base()), List.of());

        PresaleWebExecutionContext context = checker.check(PresaleQueryWebMode.REQUIRED);

        assertFalse(context.usesWebQuery("wenxin"));
        assertEquals(1, context.reportPlatforms().size());
    }

    @Test
    void requiredModeUsesNativeWhenCompanionPresaleCapabilityIsDisabled() {
        AiPlatformConfig companion = companion();
        companion.setEnabledForPresale(false);
        when(mapper.selectList(any())).thenReturn(List.of(base()), List.of(companion));

        PresaleWebExecutionContext context = checker.check(PresaleQueryWebMode.REQUIRED);

        assertFalse(context.usesWebQuery("wenxin"));
        assertEquals("文心一言", context.reportPlatforms().get(0).getPlatformName());
        verify(credentialService, never()).resolvePrimaryCredentialStrict(any(), any());
    }

    @Test
    void requiredModeRejectsMultipleEnabledCompanionsOnSameChannel() {
        when(mapper.selectList(any())).thenReturn(
                List.of(base()), List.of(companion(), companion()));

        assertThrows(PresaleWebReadinessException.class,
                () -> checker.check(PresaleQueryWebMode.REQUIRED));
    }

    @Test
    void requiredModeRejectsEndpointOutsideIntegrationAllowlistBeforeCredentialResolution() {
        AiPlatformConfig companion = companion();
        companion.setApiUrl("https://attacker.example/v2/chat/completions");
        when(mapper.selectList(any())).thenReturn(List.of(base()), List.of(companion));

        assertThrows(PresaleWebReadinessException.class,
                () -> checker.check(PresaleQueryWebMode.REQUIRED));
        verify(credentialService, never()).resolvePrimaryCredentialStrict(any(), any());
    }

    @Test
    void requiredModeRejectsNonStandardHttpsPort() {
        AiPlatformConfig companion = companion();
        companion.setApiUrl("https://qianfan.baidubce.com:8443/v2/chat/completions");
        when(mapper.selectList(any())).thenReturn(List.of(base()), List.of(companion));

        assertThrows(PresaleWebReadinessException.class,
                () -> checker.check(PresaleQueryWebMode.REQUIRED));
        verify(credentialService, never()).resolvePrimaryCredentialStrict(any(), any());
    }

    @Test
    void requiredModeRejectsMissingExecutionProviderEvenWhenCodecExists() {
        PresaleWebReadinessChecker withoutProvider = new PresaleWebReadinessChecker(
                mapper, credentialService, new ObjectMapper(), properties, List.of(codec), List.of());
        when(mapper.selectList(any())).thenReturn(List.of(base()), List.of(companion()));

        assertThrows(PresaleWebReadinessException.class,
                () -> withoutProvider.check(PresaleQueryWebMode.REQUIRED));
    }

    @Test
    void requiredModeRejectsCredentialEmbeddedInProviderConfigSnapshot() {
        AiPlatformConfig companion = companion();
        companion.setProviderConfigJson("{\"provider\":\"qianfan\",\"api_key\":\"must-not-snapshot\"}");
        when(mapper.selectList(any())).thenReturn(List.of(base()), List.of(companion));

        assertThrows(PresaleWebReadinessException.class,
                () -> checker.check(PresaleQueryWebMode.REQUIRED));
    }

    private AiPlatformConfig base() {
        AiPlatformConfig row = new AiPlatformConfig();
        row.setId(11L);
        row.setPlatformCode("wenxin");
        row.setPlatformName("文心一言");
        row.setChannelCode("wenxin");
        row.setUsageScene(UsageScene.STANDARD_CHAT.name());
        row.setEnabled(true);
        row.setEnabledForPresale(true);
        row.setLowModelId("ernie-speed");
        return row;
    }

    private AiPlatformConfig companion() {
        AiPlatformConfig row = new AiPlatformConfig();
        row.setId(21L);
        row.setPlatformCode("wenxin_web");
        row.setChannelCode("wenxin");
        row.setUsageScene(UsageScene.QUESTION_POLL_WEB.name());
        row.setIntegrationType(IntegrationType.QIANFAN_ERNIE_CHAT_WEB.name());
        row.setProviderConfigJson("{\"provider\":\"qianfan\"}");
        row.setConfigVersion(4L);
        row.setApiUrl("https://qianfan.baidubce.com/v2/chat/completions");
        row.setModelId("ernie-4.5-turbo-32k");
        row.setModelName("文心联网");
        row.setPrimaryKeyRef("env://QIANFAN_API_KEY");
        row.setEnabled(true);
        row.setEnabledForPresale(true);
        row.setDegraded(false);
        row.setCurrentHealthStatus("normal");
        row.setTimeoutMs(120_000);
        row.setConcurrencyLimit(2);
        row.setRpmLimit(60);
        row.setTpmLimit(60_000);
        return row;
    }
}
