package com.huanjing.geo.module.system.modeldiagnostic.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticModelTier;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDiagnosticPlatformCatalogServiceTest {

    private final AiPlatformConfigMapper platformMapper = mock(AiPlatformConfigMapper.class);
    private final PlatformCredentialService credentialService = mock(PlatformCredentialService.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final ModelDiagnosticPlatformCatalogService service =
            new ModelDiagnosticPlatformCatalogService(
                    platformMapper, credentialService, currentUserService);

    @Test
    @SuppressWarnings("unchecked")
    void exposesOnlySafeCapabilityMetadataAndStrictCredentialAvailability() {
        AiPlatformConfig ready = config(1L, "OPENAI_CHAT", "https://api.example.com/v1/chat", "model-a");
        ready.setLowModelId("model-a-low");
        ready.setApiKey("secret-value");
        ready.setEnabledForQuestionPoll(true);
        AiPlatformConfig blocked = config(2L, "VOLCENGINE_RESPONSES_WEB", "bad-url", "model-b");
        when(platformMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(ready, blocked));
        when(credentialService.resolvePrimaryCredentialStrict(any(), any()))
                .thenReturn("resolved-secret");

        List<ModelDiagnosticPlatformOption> options = service.list();

        assertEquals(3, options.size());
        assertEquals(List.of("BASIC_CHAT"), options.get(0).supportedModes());
        assertTrue(options.get(0).selectable());
        assertTrue(options.get(0).enabledForQuestionPoll());
        assertEquals(ModelDiagnosticModelTier.PRIMARY, options.get(0).modelTier());
        assertEquals("model-a", options.get(0).modelId());
        assertEquals(ModelDiagnosticModelTier.LOW, options.get(1).modelTier());
        assertEquals("model-a-low", options.get(1).modelId());
        assertTrue(options.get(1).selectable());
        assertEquals(List.of("WEB_SEARCH"), options.get(2).supportedModes());
        assertFalse(options.get(2).selectable());
        assertEquals("未配置有效接口地址", options.get(2).unavailableReason());
        verify(currentUserService).ensurePermission("ai.platform.diagnose");
    }

    private AiPlatformConfig config(Long id, String integrationType, String apiUrl, String modelId) {
        AiPlatformConfig config = new AiPlatformConfig();
        config.setId(id);
        config.setPlatformCode("platform-" + id);
        config.setChannelCode("channel-" + id);
        config.setPlatformName("Platform " + id);
        config.setIntegrationType(integrationType);
        config.setApiUrl(apiUrl);
        config.setModelId(modelId);
        config.setEnabled(true);
        config.setEnabledForQuestionPoll(false);
        return config;
    }
}
