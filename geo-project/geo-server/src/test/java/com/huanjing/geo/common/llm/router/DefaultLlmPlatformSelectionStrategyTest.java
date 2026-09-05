package com.huanjing.geo.common.llm.router;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DefaultLlmPlatformSelectionStrategyTest {
    @Test
    void excludesImageModelsFromPrimaryAndBackupTextCandidates() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiPlatformConfig.class);
        AiPlatformConfigMapper mapper = mock(AiPlatformConfigMapper.class);
        PlatformCredentialService credentials = mock(PlatformCredentialService.class);
        when(credentials.resolveApiKey(any(), any(), any())).thenReturn("test-key");
        AiPlatformConfig text = config("text", "STANDARD", "OPENAI_CHAT");
        text.setBackupProviderName("image");
        AiPlatformConfig image = config("image", "IMAGE_GENERATION", "OPENAI_IMAGE");
        when(mapper.selectOne(any())).thenReturn(image);
        DefaultLlmPlatformSelectionStrategy strategy = new DefaultLlmPlatformSelectionStrategy(
                mapper, credentials, mock(LlmRoutingRuntimeConfig.class));
        LlmRouteRequest request = new LlmRouteRequest(LlmFeature.ARTICLE, "", "", null,
                null, null, null, null, null, false, 1, 0, List.of(image, text));

        List<LlmPlatformCandidate> candidates = strategy.selectCandidates(request);

        assertEquals(2, candidates.size());
        assertEquals(List.of("text", "text"), candidates.stream().map(LlmPlatformCandidate::platformCode).toList());
    }

    private AiPlatformConfig config(String code, String scene, String integration) {
        AiPlatformConfig config = new AiPlatformConfig();
        config.setPlatformCode(code);
        config.setUsageScene(scene);
        config.setIntegrationType(integration);
        config.setApiUrl("https://api.example.test/v1");
        config.setModelId(code);
        config.setApiKey("test-key");
        return config;
    }
}
