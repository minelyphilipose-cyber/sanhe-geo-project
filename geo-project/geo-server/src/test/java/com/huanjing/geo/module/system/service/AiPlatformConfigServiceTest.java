package com.huanjing.geo.module.system.service;

import com.huanjing.geo.module.system.dto.AiPlatformConfigCreateRequest;
import com.huanjing.geo.module.system.dto.AiPlatformConfigUpdateRequest;
import com.huanjing.geo.common.exception.BizException;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void shouldEncryptAndStoreWebSearchCredentialFromPage() {
        when(currentUserService.requireCurrentUser()).thenReturn(operator());
        when(platformCredentialService.encryptForStorage("web-secret"))
                .thenReturn("ENC:encrypted-web-secret");
        AiPlatformConfigCreateRequest req = webSearchCreateRequest();
        req.setApiKey("web-secret");

        aiPlatformConfigService.create(req);

        ArgumentCaptor<AiPlatformConfig> captor = ArgumentCaptor.forClass(AiPlatformConfig.class);
        verify(aiPlatformConfigMapper).insert(captor.capture());
        assertEquals("ENC:encrypted-web-secret", captor.getValue().getApiKey());
        assertNull(captor.getValue().getPrimaryKeyRef());
    }

    @Test
    void webSearchCredentialSourceMustBeUnambiguous() {
        when(currentUserService.requireCurrentUser()).thenReturn(operator());
        AiPlatformConfigCreateRequest req = webSearchCreateRequest();
        req.setApiKey("web-secret");
        req.setPrimaryKeyRef("env://ARK_API_KEY");

        BizException error = assertThrows(BizException.class,
                () -> aiPlatformConfigService.create(req));

        assertEquals(400, error.getCode());
    }

    @Test
    void updatingToPageCredentialClearsEnvironmentReference() {
        when(currentUserService.requireCurrentUser()).thenReturn(operator());
        when(platformCredentialService.encryptForStorage("new-secret"))
                .thenReturn("ENC:new-secret");
        AiPlatformConfig entity = webSearchEntity();
        entity.setId(9L);
        entity.setPrimaryKeyRef("env://ARK_API_KEY");
        when(aiPlatformConfigMapper.selectById(9L)).thenReturn(entity);
        AiPlatformConfigUpdateRequest req = webSearchUpdateRequest();
        req.setApiKey("new-secret");
        req.setClearPrimaryKeyRef(true);

        aiPlatformConfigService.update(9L, req);

        assertEquals("ENC:new-secret", entity.getApiKey());
        assertNull(entity.getPrimaryKeyRef());
        verify(aiPlatformConfigMapper).updateById(entity);
        verify(aiPlatformConfigMapper).updateCredentialSources(
                9L, "ENC:new-secret", null);
    }

    @Test
    void updatingToEnvironmentCredentialClearsStoredPageCredential() {
        when(currentUserService.requireCurrentUser()).thenReturn(operator());
        AiPlatformConfig entity = webSearchEntity();
        entity.setId(10L);
        entity.setApiKey("ENC:old-secret");
        when(aiPlatformConfigMapper.selectById(10L)).thenReturn(entity);
        AiPlatformConfigUpdateRequest req = webSearchUpdateRequest();
        req.setPrimaryKeyRef("env://ARK_API_KEY");
        req.setClearApiKey(true);

        aiPlatformConfigService.update(10L, req);

        assertNull(entity.getApiKey());
        assertEquals("env://ARK_API_KEY", entity.getPrimaryKeyRef());
        verify(aiPlatformConfigMapper).updateCredentialSources(
                10L, null, "env://ARK_API_KEY");
    }

    private AiPlatformConfigCreateRequest webSearchCreateRequest() {
        AiPlatformConfigCreateRequest req = new AiPlatformConfigCreateRequest();
        fillWebSearchRequest(req);
        return req;
    }

    private AiPlatformConfigUpdateRequest webSearchUpdateRequest() {
        AiPlatformConfigUpdateRequest req = new AiPlatformConfigUpdateRequest();
        req.setPlatformCode("doubao_web");
        req.setChannelCode("doubao");
        req.setUsageScene("QUESTION_POLL_WEB");
        req.setIntegrationType("VOLCENGINE_RESPONSES_WEB");
        req.setPlatformName("豆包联网");
        req.setPriorityLevel("P0");
        req.setApiUrl("https://example.test/responses");
        req.setModelId("doubao-model");
        req.setModelName("豆包联网模型");
        req.setConcurrencyLimit(2);
        req.setRpmLimit(60);
        req.setTpmLimit(60000);
        req.setEnabled(false);
        req.setEnabledForQuestionPoll(false);
        req.setDegraded(false);
        return req;
    }

    private void fillWebSearchRequest(AiPlatformConfigCreateRequest req) {
        req.setPlatformCode("doubao_web");
        req.setChannelCode("doubao");
        req.setUsageScene("QUESTION_POLL_WEB");
        req.setIntegrationType("VOLCENGINE_RESPONSES_WEB");
        req.setPlatformName("豆包联网");
        req.setPriorityLevel("P0");
        req.setApiUrl("https://example.test/responses");
        req.setModelId("doubao-model");
        req.setModelName("豆包联网模型");
        req.setConcurrencyLimit(2);
        req.setRpmLimit(60);
        req.setTpmLimit(60000);
        req.setEnabled(false);
        req.setEnabledForQuestionPoll(false);
        req.setDegraded(false);
    }

    private AiPlatformConfig webSearchEntity() {
        AiPlatformConfig entity = new AiPlatformConfig();
        entity.setPlatformCode("doubao_web");
        entity.setChannelCode("doubao");
        entity.setUsageScene("QUESTION_POLL_WEB");
        entity.setIntegrationType("VOLCENGINE_RESPONSES_WEB");
        entity.setPlatformName("豆包联网");
        entity.setPriorityLevel("P0");
        entity.setApiUrl("https://example.test/responses");
        entity.setModelId("doubao-model");
        entity.setModelName("豆包联网模型");
        entity.setEnabled(false);
        entity.setDegraded(false);
        return entity;
    }

    private SysUser operator() {
        SysUser operator = new SysUser();
        operator.setId(100L);
        operator.setIsActive(true);
        return operator;
    }
}
