package com.huanjing.geo.module.project.service;

import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.capacity.LlmCapacityFailureClassifier;
import com.huanjing.geo.common.llm.limiter.PlatformConcurrencyLimiterService;
import com.huanjing.geo.common.llm.pool.LlmPoolProperties;
import com.huanjing.geo.common.llm.router.LlmRouteException;
import com.huanjing.geo.common.llm.router.LlmRouteFailureKind;
import com.huanjing.geo.module.project.entity.BaselineCollectionTask;
import com.huanjing.geo.module.project.entity.BaselineObservation;
import com.huanjing.geo.module.project.entity.BaselineQuestionSnapshot;
import com.huanjing.geo.module.project.entity.BaselineSnapshot;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.BaselineCollectionTaskMapper;
import com.huanjing.geo.module.project.mapper.BaselineCompetitorMentionMapper;
import com.huanjing.geo.module.project.mapper.BaselineCompetitorSourceMapper;
import com.huanjing.geo.module.project.mapper.BaselineHighlightSpanMapper;
import com.huanjing.geo.module.project.mapper.BaselineObservationMapper;
import com.huanjing.geo.module.project.mapper.BaselineObservationScoreMapper;
import com.huanjing.geo.module.project.mapper.BaselineQuestionSnapshotMapper;
import com.huanjing.geo.module.project.mapper.BaselineSnapshotMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaselineObservationCollectionWorkerTest {

    @Test
    void isFreshRunning_onlyBlocksRecentlyUpdatedRunningTasks() {
        LocalDateTime staleBefore = LocalDateTime.of(2026, 6, 18, 10, 0);
        BaselineCollectionTask fresh = task(BaselineObservationCollectionService.TASK_STATUS_RUNNING,
                staleBefore.plusMinutes(1));
        BaselineCollectionTask stale = task(BaselineObservationCollectionService.TASK_STATUS_RUNNING,
                staleBefore.minusSeconds(1));
        BaselineCollectionTask pending = task(BaselineObservationCollectionService.TASK_STATUS_PENDING,
                staleBefore.plusMinutes(1));

        assertThat(BaselineObservationCollectionWorker.isFreshRunning(fresh, staleBefore)).isTrue();
        assertThat(BaselineObservationCollectionWorker.isFreshRunning(stale, staleBefore)).isFalse();
        assertThat(BaselineObservationCollectionWorker.isFreshRunning(pending, staleBefore)).isFalse();
    }

    @Test
    void capacityFailureDeferDoesNotCreateFailedObservation() throws Exception {
        LlmCallFacade facade = mock(LlmCallFacade.class);
        when(facade.execute(any())).thenThrow(new LlmRouteException(
                LlmRouteFailureKind.ALL_PERMIT_BUSY,
                "All LLM candidates are waiting for permits",
                0,
                null
        ));
        BaselineObservationCollectionWorker worker = worker(facade);
        ReflectionTestUtils.setField(worker, "capacityFailureDeferEnabled", true);

        BaselineSnapshot snapshot = new BaselineSnapshot();
        snapshot.setId(10L);
        snapshot.setProjectId(20L);
        BaselineQuestionSnapshot question = new BaselineQuestionSnapshot();
        question.setId(30L);
        question.setQuestionText("test question");
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setPlatformCode("qwen");
        platform.setPlatformName("Qwen");
        platform.setMaxRetry(0);

        BaselineObservation observation = ReflectionTestUtils.invokeMethod(
                worker,
                "collectOne",
                snapshot,
                question,
                platform,
                1
        );

        assertThat(observation).isNotNull();
        assertThat(observation.getCallStatus()).isEqualTo("CAPACITY_DEFERRED");
        assertThat(observation.getErrorCode()).isEqualTo("ALL_PERMIT_BUSY");
    }

    @Test
    void capacityDeferredSampleIsNotPersistedOrJudged() throws Exception {
        LlmCallFacade facade = mock(LlmCallFacade.class);
        when(facade.execute(any())).thenThrow(new LlmRouteException(
                LlmRouteFailureKind.ALL_PERMIT_BUSY,
                "All LLM candidates are waiting for permits",
                0,
                null
        ));
        BaselineCollectionTaskMapper taskMapper = mock(BaselineCollectionTaskMapper.class);
        BaselineCollectionTask runningTask = task(BaselineObservationCollectionService.TASK_STATUS_RUNNING, LocalDateTime.now());
        when(taskMapper.selectById(100L)).thenReturn(runningTask);
        BaselineObservationMapper observationMapper = mock(BaselineObservationMapper.class);
        when(observationMapper.selectCount(any())).thenReturn(0L);
        BaselineSemanticJudgeService judgeService = mock(BaselineSemanticJudgeService.class);
        BaselineObservationCollectionWorker worker = worker(facade, taskMapper, observationMapper, judgeService);
        ReflectionTestUtils.setField(worker, "capacityFailureDeferEnabled", true);

        BaselineSnapshot snapshot = new BaselineSnapshot();
        snapshot.setId(10L);
        snapshot.setProjectId(20L);
        BaselineQuestionSnapshot question = new BaselineQuestionSnapshot();
        question.setId(30L);
        question.setQuestionText("test question");
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setPlatformCode("qwen");
        platform.setPlatformName("Qwen");
        platform.setConcurrencyLimit(1);
        platform.setMaxRetry(0);
        Project project = new Project();
        project.setId(20L);

        ReflectionTestUtils.invokeMethod(
                worker,
                "collectAndPersistSample",
                100L,
                snapshot,
                question,
                platform,
                project,
                List.of("alias"),
                List.of(),
                1
        );

        verify(observationMapper, never()).insert(any(BaselineObservation.class));
        verify(judgeService, never()).judge(any(), any(), any(), any(), any(), any());
    }

    private BaselineCollectionTask task(String status, LocalDateTime updatedAt) {
        BaselineCollectionTask task = new BaselineCollectionTask();
        task.setStatus(status);
        task.setUpdatedAt(updatedAt);
        return task;
    }

    private BaselineObservationCollectionWorker worker(LlmCallFacade facade) {
        return worker(
                facade,
                mock(BaselineCollectionTaskMapper.class),
                mock(BaselineObservationMapper.class),
                mock(BaselineSemanticJudgeService.class)
        );
    }

    private BaselineObservationCollectionWorker worker(LlmCallFacade facade,
                                                       BaselineCollectionTaskMapper taskMapper,
                                                       BaselineObservationMapper observationMapper,
                                                       BaselineSemanticJudgeService judgeService) {
        return new BaselineObservationCollectionWorker(
                taskMapper,
                mock(BaselineSnapshotMapper.class),
                mock(BaselineQuestionSnapshotMapper.class),
                observationMapper,
                mock(BaselineObservationScoreMapper.class),
                mock(BaselineHighlightSpanMapper.class),
                mock(BaselineCompetitorSourceMapper.class),
                mock(BaselineCompetitorMentionMapper.class),
                mock(ProjectMapper.class),
                mock(AiPlatformConfigMapper.class),
                facade,
                new LlmPoolProperties(),
                mock(TransactionTemplate.class),
                new PlatformConcurrencyLimiterService(),
                judgeService,
                new LlmCapacityFailureClassifier()
        );
    }
}
