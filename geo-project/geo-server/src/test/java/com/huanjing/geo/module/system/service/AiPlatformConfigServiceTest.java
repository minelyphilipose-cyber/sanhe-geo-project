package com.huanjing.geo.module.system.service;

import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
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
