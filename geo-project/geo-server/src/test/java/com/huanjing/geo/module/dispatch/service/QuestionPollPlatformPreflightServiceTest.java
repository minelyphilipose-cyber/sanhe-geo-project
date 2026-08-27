package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.LlmCallRequest;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionPollPlatformPreflightServiceTest {

    @Test
    void preflightUsesPinnedSingleAttemptProbe() throws Exception {
        AiPlatformConfigMapper mapper = mock(AiPlatformConfigMapper.class);
        LlmCallFacade facade = mock(LlmCallFacade.class);
        DispatchProperties properties = new DispatchProperties();
        AiPlatformConfig platform = platform();
        when(mapper.selectList(any())).thenReturn(List.of(platform));
        QuestionPollPlatformPreflightService service =
                new QuestionPollPlatformPreflightService(mapper, facade, properties);

        service.runPreflight();

        ArgumentCaptor<LlmCallRequest> captor = ArgumentCaptor.forClass(LlmCallRequest.class);
        verify(facade).execute(captor.capture());
        assertNotNull(captor.getValue().routeRequest());
        assertEquals(0, captor.getValue().routeRequest().maxRetry());
        assertEquals(1, captor.getValue().routeRequest().platformConfigs().size());
        verify(mapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void failedPreflightMarksPlatformUnavailableForPlanning() throws Exception {
        AiPlatformConfigMapper mapper = mock(AiPlatformConfigMapper.class);
        LlmCallFacade facade = mock(LlmCallFacade.class);
        DispatchProperties properties = new DispatchProperties();
        when(mapper.selectList(any())).thenReturn(List.of(platform()));
        when(facade.execute(any())).thenThrow(new IllegalStateException("quota exhausted"));
        QuestionPollPlatformPreflightService service =
                new QuestionPollPlatformPreflightService(mapper, facade, properties);

        service.runPreflight();

        verify(mapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    private AiPlatformConfig platform() {
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setId(1L);
        platform.setPlatformCode("kimi");
        platform.setPlatformName("Kimi");
        platform.setUsageScene("STANDARD_CHAT");
        return platform;
    }
}
