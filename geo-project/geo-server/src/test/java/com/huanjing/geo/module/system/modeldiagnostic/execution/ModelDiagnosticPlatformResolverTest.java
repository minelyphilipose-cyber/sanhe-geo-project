package com.huanjing.geo.module.system.modeldiagnostic.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.dispatch.websearch.enums.ErrorCategory;
import com.huanjing.geo.module.dispatch.websearch.transport.PollPayloadProtector;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticMode;
import com.huanjing.geo.module.system.modeldiagnostic.enums.ModelDiagnosticModelTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelDiagnosticPlatformResolverTest {

    private final AiPlatformConfigMapper mapper = mock(AiPlatformConfigMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ModelDiagnosticPlatformResolver resolver = new ModelDiagnosticPlatformResolver(
            mapper, new PollPayloadProtector(objectMapper, ""), objectMapper);

    @Test
    void disabledConfigurationIsAllowedAndSnapshotExcludesCredentialValuesAndBackup() throws Exception {
        AiPlatformConfig config = config("OPENAI_CHAT");
        config.setEnabled(false);
        config.setApiKey("database-secret");
        config.setBackupKeyRef("env://BACKUP_KEY");
        config.setProviderConfigJson("{\"token\":\"provider-secret\",\"temperature\":0.2}");
        when(mapper.selectById(1L)).thenReturn(config);

        ModelDiagnosticPlatformProfile profile = resolver.resolve(1L, ModelDiagnosticMode.BASIC_CHAT);

        assertEquals(false, objectMapper.readTree(profile.configSnapshotJson()).path("enabled").asBoolean());
        assertFalse(profile.configSnapshotJson().contains("database-secret"));
        assertFalse(profile.configSnapshotJson().contains("provider-secret"));
        assertFalse(profile.configSnapshotJson().contains("BACKUP_KEY"));
    }

    @Test
    void modeMustMatchIntegrationType() {
        when(mapper.selectById(1L)).thenReturn(config("OPENAI_CHAT"));

        ModelDiagnosticExecutionException error = assertThrows(
                ModelDiagnosticExecutionException.class,
                () -> resolver.resolve(1L, ModelDiagnosticMode.WEB_SEARCH));

        assertEquals(ErrorCategory.INVALID_REQUEST, error.category());
    }

    @Test
    void lowTierUsesLowModelAndFreezesTheSelectedTierInSnapshot() throws Exception {
        AiPlatformConfig config = config("OPENAI_CHAT");
        config.setLowModelId("model-low");
        when(mapper.selectById(1L)).thenReturn(config);

        ModelDiagnosticPlatformProfile profile = resolver.resolve(
                1L, ModelDiagnosticMode.BASIC_CHAT, ModelDiagnosticModelTier.LOW);

        assertEquals("model-low", profile.requestedModelId());
        assertEquals("LOW", objectMapper.readTree(profile.configSnapshotJson())
                .path("modelTier").asText());
        assertEquals("model-low", objectMapper.readTree(profile.configSnapshotJson())
                .path("modelId").asText());
    }

    private AiPlatformConfig config(String integrationType) {
        AiPlatformConfig config = new AiPlatformConfig();
        config.setId(1L);
        config.setPlatformCode("platform");
        config.setChannelCode("channel");
        config.setPlatformName("Platform");
        config.setUsageScene("BASIC_CHAT");
        config.setIntegrationType(integrationType);
        config.setApiUrl("https://example.test/v1");
        config.setModelId("model");
        config.setConfigVersion(2L);
        config.setTimeoutMs(60_000);
        return config;
    }
}
