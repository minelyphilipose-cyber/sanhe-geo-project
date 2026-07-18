package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.module.dispatch.entity.PollBatch;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import com.huanjing.geo.module.dispatch.entity.PollDailyStat;
import com.huanjing.geo.module.dispatch.entity.PollResult;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.module.dispatch.mapper.PollDailyStatMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DispatchPollAggregationServiceTest {

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PollResult.class);
        initTableInfo(PollDailyStat.class);
    }

    @Test
    void aggregatesOnlyFinalizedEffectiveWebSearchResultsAndPersistsChannel() {
        PollBatchMapper batchMapper = mock(PollBatchMapper.class);
        PollBatchShardMapper shardMapper = mock(PollBatchShardMapper.class);
        PollResultMapper resultMapper = mock(PollResultMapper.class);
        PollDailyStatMapper dailyStatMapper = mock(PollDailyStatMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        PollSummaryRecomputeService summaryService = mock(PollSummaryRecomputeService.class);
        DispatchAlertService alertService = mock(DispatchAlertService.class);

        DispatchPollAggregationService service = new DispatchPollAggregationService(
                batchMapper,
                shardMapper,
                resultMapper,
                dailyStatMapper,
                mock(StringRedisTemplate.class),
                projectMapper,
                summaryService,
                alertService
        );

        LocalDate batchDate = LocalDate.of(2026, 7, 16);
        PollBatch batch = new PollBatch();
        batch.setId(100L);
        batch.setProjectId(200L);
        batch.setBatchDate(batchDate);
        batch.setBatchNo(1);
        batch.setQuestionTier("A");
        batch.setTotalQuestionCount(3);
        batch.setTotalPlatformCount(1);
        batch.setTotalShardCount(1);
        batch.setStatus("ready");

        PollBatchShard shard = new PollBatchShard();
        shard.setBatchId(batch.getId());
        shard.setPlatformId(55L);
        shard.setPlatformCode("doubao_web");
        shard.setChannelCode("doubao");
        shard.setPlatformName("豆包联网问答");
        shard.setExpectedCount(3);
        shard.setStatus("completed");

        PollResult effective = completedResult(1L);
        effective.setExecutionFinalized(true);
        effective.setEffectiveAttemptId(9001L);
        effective.setSearchRequested(true);
        effective.setSearchTriggered(true);
        effective.setBrandInSearch(true);
        effective.setBrandInAnswer(true);
        effective.setConfirmedCitationExposure(true);

        PollResult unfinishedProjection = completedResult(2L);
        unfinishedProjection.setExecutionFinalized(false);
        unfinishedProjection.setEffectiveAttemptId(null);
        unfinishedProjection.setSearchRequested(true);
        unfinishedProjection.setSearchTriggered(true);
        unfinishedProjection.setBrandInSearch(true);
        unfinishedProjection.setBrandInAnswer(true);
        unfinishedProjection.setConfirmedCitationExposure(true);

        PollResult searchNotTriggered = completedResult(3L);
        searchNotTriggered.setExecutionFinalized(true);
        searchNotTriggered.setEffectiveAttemptId(9002L);
        searchNotTriggered.setSearchRequested(true);
        searchNotTriggered.setSearchTriggered(false);
        searchNotTriggered.setBrandInSearch(true);
        searchNotTriggered.setBrandInAnswer(true);
        searchNotTriggered.setConfirmedCitationExposure(true);

        Project project = new Project();
        project.setId(batch.getProjectId());
        project.setProjectName("测试项目");

        when(batchMapper.selectByIdForUpdate(batch.getId())).thenReturn(batch);
        when(shardMapper.countTerminalByBatchId(batch.getId())).thenReturn(1L);
        when(shardMapper.selectByBatchId(batch.getId())).thenReturn(List.of(shard));
        when(resultMapper.selectList(any())).thenReturn(List.of(effective, unfinishedProjection, searchNotTriggered));
        when(projectMapper.selectById(batch.getProjectId())).thenReturn(project);
        when(dailyStatMapper.selectOne(any())).thenReturn(null);
        when(summaryService.recomputeSlice(batch.getProjectId(), batchDate, "A"))
                .thenReturn(new PollSummaryRecomputeService.RecomputeResult(
                        batch.getProjectId(), batchDate, "A", false, null,
                        2, 0, 0, 0, 0, 0, 0
                ));

        service.aggregateBatchIfReady(batch.getId());

        ArgumentCaptor<PollDailyStat> captor = ArgumentCaptor.forClass(PollDailyStat.class);
        verify(dailyStatMapper).insert(captor.capture());
        PollDailyStat stat = captor.getValue();
        assertEquals("doubao", stat.getChannelCode());
        assertEquals(1, stat.getSearchConfirmedCount());
        assertEquals(1, stat.getBrandSearchCount());
        assertEquals(1, stat.getBrandAnswerCount());
        assertEquals(1, stat.getConfirmedCitationExposureCount());
        assertEquals(new BigDecimal("0.3333"), stat.getConfirmedCitationExposureRate());
        verify(alertService).createOrRefreshAlert(
                any(),
                eq(batch.getProjectId()),
                contains("web_search_trigger_rate:"),
                eq(com.huanjing.geo.module.dispatch.enums.DispatchAlertSeverity.WARN),
                anyString(),
                anyString(),
                eq(0),
                anyString()
        );
    }

    @Test
    void manualBatchFinishesWithoutWritingFormalStatisticsOrAlerts() {
        PollBatchMapper batchMapper = mock(PollBatchMapper.class);
        PollBatchShardMapper shardMapper = mock(PollBatchShardMapper.class);
        PollResultMapper resultMapper = mock(PollResultMapper.class);
        PollDailyStatMapper dailyStatMapper = mock(PollDailyStatMapper.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        PollSummaryRecomputeService summaryService = mock(PollSummaryRecomputeService.class);
        DispatchAlertService alertService = mock(DispatchAlertService.class);
        DispatchPollAggregationService service = new DispatchPollAggregationService(
                batchMapper,
                shardMapper,
                resultMapper,
                dailyStatMapper,
                mock(StringRedisTemplate.class),
                projectMapper,
                summaryService,
                alertService
        );

        PollBatch batch = new PollBatch();
        batch.setId(101L);
        batch.setProjectId(200L);
        batch.setBatchDate(LocalDate.of(2026, 7, 16));
        batch.setBatchNo(1_000_000);
        batch.setQuestionTier("A");
        batch.setTriggerType("MANUAL");
        batch.setTotalQuestionCount(1);
        batch.setTotalPlatformCount(1);
        batch.setTotalShardCount(1);
        batch.setStatus("ready");

        PollBatchShard shard = new PollBatchShard();
        shard.setBatchId(batch.getId());
        shard.setPlatformId(55L);
        shard.setPlatformCode("doubao_web");
        shard.setChannelCode("doubao");
        shard.setPlatformName("豆包联网问答");
        shard.setExpectedCount(1);
        shard.setStatus("completed");

        PollResult result = completedResult(3L);
        when(batchMapper.selectByIdForUpdate(batch.getId())).thenReturn(batch);
        when(shardMapper.countTerminalByBatchId(batch.getId())).thenReturn(1L);
        when(shardMapper.selectByBatchId(batch.getId())).thenReturn(List.of(shard));
        when(resultMapper.selectList(any())).thenReturn(List.of(result));
        when(projectMapper.selectById(batch.getProjectId())).thenReturn(new Project());

        service.aggregateBatchIfReady(batch.getId());

        assertEquals("finished", batch.getStatus());
        assertEquals(1, batch.getCompletedCount());
        verify(batchMapper).updateById(batch);
        verify(dailyStatMapper, never()).insert(any());
        verify(dailyStatMapper, never()).updateById(any());
        verifyNoInteractions(summaryService);
        verifyNoInteractions(alertService);
    }

    private static PollResult completedResult(long id) {
        PollResult result = new PollResult();
        result.setId(id);
        result.setPlatformId(55L);
        result.setStatus("completed");
        result.setRequestCount(1);
        return result;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
