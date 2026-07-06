package com.huanjing.geo.module.system.service;

import com.huanjing.geo.module.system.dto.AiPlatformConfigCreateRequest;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPlatformConfigServiceTest {

    @Mock
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private PlatformCredentialService platformCredentialService;

    @InjectMocks
    private AiPlatformConfigService aiPlatformConfigService;

    @Test
    void shouldAllowGeoQuestionForAnyEnabledPlatform() {
        when(currentUserService.requireCurrentUser()).thenReturn(operator());
        when(platformCredentialService.encryptForStorage("secret")).thenReturn("encrypted-secret");

        AiPlatformConfigCreateRequest req = new AiPlatformConfigCreateRequest();
        req.setPlatformCode("customllm");
        req.setPlatformName("Custom LLM");
        req.setPriorityLevel("P1");
        req.setRpmLimit(60);
        req.setTpmLimit(60000);
        req.setApiKey("secret");
        req.setApiUrl("https://example.test/v1");
        req.setModelId("custom-model");
        req.setModelName("Custom Model");
        req.setConcurrencyLimit(1);
        req.setEnabled(true);
        req.setEnabledForPresale(false);
        req.setPresaleEvaluateEnabled(false);
        req.setEnabledForArticle(false);
        req.setEnabledForGeoQuestion(true);
        req.setEnabledForQuestionPoll(false);
        req.setDegraded(false);

        aiPlatformConfigService.create(req);

        ArgumentCaptor<AiPlatformConfig> captor = ArgumentCaptor.forClass(AiPlatformConfig.class);
        verify(aiPlatformConfigMapper).insert(captor.capture());
        assertEquals("customllm", captor.getValue().getPlatformCode());
        assertTrue(captor.getValue().getEnabledForGeoQuestion());
    }

    @Test
    void shouldDeletePlatformConfig() {
        AiPlatformConfig entity = new AiPlatformConfig();
        entity.setId(1L);
        entity.setPlatformCode("deepseek");
        entity.setPlatformName("DeepSeek");

        when(currentUserService.requireCurrentUser()).thenReturn(operator());
        when(aiPlatformConfigMapper.selectById(1L)).thenReturn(entity);

        aiPlatformConfigService.delete(1L);

        verify(aiPlatformConfigMapper).deleteById(1L);
        verify(activityLogService).logAction(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private SysUser operator() {
        SysUser operator = new SysUser();
        operator.setId(100L);
        operator.setIsActive(true);
        return operator;
    }
}
