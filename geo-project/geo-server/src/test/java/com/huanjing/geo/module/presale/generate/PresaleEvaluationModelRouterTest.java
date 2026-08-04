package com.huanjing.geo.module.presale.generate;

import com.huanjing.geo.common.llm.LlmCapacityView;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PresaleEvaluationModelRouterTest {

    @Test
    void routePlatforms_excludesGloballyDegradedModels() {
        AiPlatformConfigMapper mapper = mock(AiPlatformConfigMapper.class);
        LlmCapacityView capacityView = mock(LlmCapacityView.class);
        PresaleEvaluationModelRouter router = new PresaleEvaluationModelRouter(mapper, capacityView);
        when(mapper.selectList(any())).thenReturn(List.of(
                platform("deepseek", true),
                platform("doubao", false)
        ));
        when(capacityView.activePlatformCount("doubao")).thenReturn(0L);

        List<AiPlatformConfig> result = router.routePlatforms();

        assertEquals(List.of("doubao"), result.stream().map(AiPlatformConfig::getPlatformCode).toList());
    }

    private AiPlatformConfig platform(String platformCode, boolean degraded) {
        AiPlatformConfig out = new AiPlatformConfig();
        out.setPlatformCode(platformCode);
        out.setPriorityLevel("P0");
        out.setConcurrencyLimit(5);
        out.setDegraded(degraded);
        return out;
    }
}
