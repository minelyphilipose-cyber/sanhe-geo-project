package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardItemMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.module.dispatch.mapper.ProjectPollRotationMapper;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DispatchQuestionPollPlanningServiceTest {

    @Test
    void effectiveShardSizeIsCappedByMaxShardSize() {
        DispatchProperties properties = new DispatchProperties();
        properties.setQuestionPollShardSize(200);
        properties.setQuestionPollMaxShardSize(20);

        DispatchQuestionPollPlanningService service = new DispatchQuestionPollPlanningService(
                mock(PollBatchMapper.class),
                mock(PollBatchShardMapper.class),
                mock(PollBatchShardItemMapper.class),
                mock(ProjectPollRotationMapper.class),
                mock(ProjectKeywordGroupRelMapper.class),
                mock(KeywordGroupResultMapper.class),
                mock(AiPlatformConfigMapper.class),
                properties,
                mock(ObjectProvider.class)
        );

        assertEquals(20, service.resolveEffectiveShardSize());
    }
}
