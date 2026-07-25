package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.dispatch.entity.AiPlatformHealthEvent;
import com.huanjing.geo.module.dispatch.mapper.AiPlatformHealthEventMapper;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPlatformHealthMonitorServiceTest {
    private AiPlatformHealthEventMapper healthEventMapper;
    private AiPlatformHealthMonitorService service;

    @BeforeEach
    void setUp() {
        healthEventMapper = mock(AiPlatformHealthEventMapper.class);
        when(healthEventMapper.selectRecentForFeature(anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of());
        service = new AiPlatformHealthMonitorService(
                healthEventMapper,
                mock(AiPlatformConfigMapper.class)
        );
    }

    @Test
    void normalLongFormArticleCallIsNotMarkedSlowAtThirtySeconds() {
        service.recordSuccess("qwen", "article", 80_000L);

        ArgumentCaptor<AiPlatformHealthEvent> eventCaptor =
                ArgumentCaptor.forClass(AiPlatformHealthEvent.class);
        verify(healthEventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType())
                .isEqualTo(AiPlatformHealthMonitorService.EVENT_SUCCESS);
    }

    @Test
    void exceptionallySlowLongFormArticleCallIsRecordedAsSlow() {
        service.recordSuccess("qwen", "article", 200_000L);

        ArgumentCaptor<AiPlatformHealthEvent> eventCaptor =
                ArgumentCaptor.forClass(AiPlatformHealthEvent.class);
        verify(healthEventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType())
                .isEqualTo(AiPlatformHealthMonitorService.EVENT_SLOW_RESPONSE);
    }
}
