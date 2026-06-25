package com.huanjing.geo.module.dispatch.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.entity.PollBatch;
import com.huanjing.geo.module.dispatch.entity.PollBatchShard;
import com.huanjing.geo.module.dispatch.entity.PollBatchShardItem;
import com.huanjing.geo.module.dispatch.entity.ProjectPollRotation;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardItemMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardMapper;
import com.huanjing.geo.module.dispatch.mapper.ProjectPollRotationMapper;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.entity.ProjectKeywordGroupRel;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchQuestionPollPlanningServiceTest {

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ProjectKeywordGroupRel.class);
        initTableInfo(KeywordGroupResult.class);
        initTableInfo(AiPlatformConfig.class);
        initTableInfo(PollBatch.class);
        initTableInfo(PollBatchShard.class);
        initTableInfo(PollBatchShardItem.class);
    }

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

    @Test
    void cycleDaysDefaultsToLegacyFullDailyPollingAndHandlesUnlimitedPlanCap() {
        DispatchProperties properties = new DispatchProperties();
        DispatchQuestionPollPlanningService service = service(properties);

        assertEquals(50, service.resolveDailyTakeCount(50, 0, "A"));
        assertEquals(50, service.resolveDailyTakeCount(50, -1, "A"));
        assertEquals(30, service.resolveDailyTakeCount(30, 50, "A"));
    }

    @Test
    void cycleDaysSpreadTierAByActualEffectiveTotal() {
        DispatchProperties properties = new DispatchProperties();
        properties.setQuestionPollCycleDays(7);
        DispatchQuestionPollPlanningService service = service(properties);

        assertEquals(8, service.resolveDailyTakeCount(50, 0, "A"));
        assertEquals(8, service.resolveDailyTakeCount(200, 50, "A"));
        assertEquals(5, service.resolveDailyTakeCount(30, 50, "A"));
        assertEquals(50, service.resolveDailyTakeCount(50, 0, "B"));
    }

    @Test
    void plannedQuestionSliceIsSharedByAllQuestionPollPlatforms() {
        DispatchProperties properties = new DispatchProperties();
        properties.setQuestionPollCycleDays(2);
        properties.setQuestionPollShardSize(20);
        properties.setQuestionPollMaxShardSize(20);

        PollBatchMapper batchMapper = mock(PollBatchMapper.class);
        PollBatchShardMapper shardMapper = mock(PollBatchShardMapper.class);
        PollBatchShardItemMapper itemMapper = mock(PollBatchShardItemMapper.class);
        ProjectPollRotationMapper rotationMapper = mock(ProjectPollRotationMapper.class);
        ProjectKeywordGroupRelMapper relMapper = mock(ProjectKeywordGroupRelMapper.class);
        KeywordGroupResultMapper resultMapper = mock(KeywordGroupResultMapper.class);
        AiPlatformConfigMapper platformMapper = mock(AiPlatformConfigMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DispatchTaskService> provider = mock(ObjectProvider.class);
        DispatchTaskService taskService = mock(DispatchTaskService.class);

        AtomicLong ids = new AtomicLong(10);
        List<PollBatchShard> insertedShards = new ArrayList<>();
        List<PollBatchShardItem> insertedItems = new ArrayList<>();

        doAnswer(invocation -> {
            PollBatch batch = invocation.getArgument(0);
            batch.setId(ids.getAndIncrement());
            return 1;
        }).when(batchMapper).insert(any(PollBatch.class));
        doAnswer(invocation -> {
            PollBatchShard shard = invocation.getArgument(0);
            shard.setId(ids.getAndIncrement());
            insertedShards.add(copyShard(shard));
            return 1;
        }).when(shardMapper).insert(any(PollBatchShard.class));
        doAnswer(invocation -> {
            PollBatchShardItem item = invocation.getArgument(0);
            insertedItems.add(copyItem(item));
            return 1;
        }).when(itemMapper).insert(any(PollBatchShardItem.class));
        when(provider.getObject()).thenReturn(taskService);
        when(taskService.createTaskWithoutEnqueue(
                eq(1L),
                eq(DispatchTaskType.BI_DAILY_POLL),
                eq(LocalDate.of(2026, 6, 18)),
                eq(LocalDate.of(2026, 6, 24)),
                any(LocalDateTime.class),
                anyMap(),
                any(),
                eq(null),
                eq(null)
        )).thenAnswer(invocation -> {
            DispatchTask task = new DispatchTask();
            task.setId(ids.getAndIncrement());
            task.setProjectId(1L);
            task.setTaskType(DispatchTaskType.BI_DAILY_POLL.name());
            return task;
        });

        when(batchMapper.selectOne(any())).thenReturn(null);
        when(relMapper.selectList(any())).thenReturn(List.of(rel(100L)));
        when(resultMapper.selectList(any())).thenReturn(List.of(
                keyword(1L, "q1"),
                keyword(2L, "q2"),
                keyword(3L, "q3"),
                keyword(4L, "q4"),
                keyword(5L, "q5")
        ));
        when(platformMapper.selectList(any())).thenReturn(List.of(
                platform(1L, "deepseek"),
                platform(2L, "hunyuan"),
                platform(3L, "doubao"),
                platform(4L, "qwen")
        ));
        ProjectPollRotation rotation = new ProjectPollRotation();
        rotation.setProjectId(1L);
        rotation.setPriorityLevel("A");
        rotation.setRotationOffset(0);
        when(rotationMapper.selectForUpdate(1L, "A")).thenReturn(rotation);

        DispatchQuestionPollPlanningService service = new DispatchQuestionPollPlanningService(
                batchMapper,
                shardMapper,
                itemMapper,
                rotationMapper,
                relMapper,
                resultMapper,
                platformMapper,
                properties,
                provider
        );

        Project project = new Project();
        project.setId(1L);
        project.setPlanKeywordGroupLimitA(0);

        service.planProjectTierPoll(project, LocalDate.of(2026, 6, 24),
                LocalDate.of(2026, 6, 18), "A", 1);

        Map<String, List<Long>> keywordIdsByPlatform = insertedShards.stream()
                .collect(Collectors.toMap(
                        PollBatchShard::getPlatformCode,
                        shard -> insertedItems.stream()
                                .filter(item -> item.getShardId().equals(shard.getId()))
                                .sorted(Comparator.comparing(PollBatchShardItem::getSortOrder))
                                .map(PollBatchShardItem::getKeywordResultId)
                                .toList()
                ));
        assertEquals(List.of(1L, 2L, 3L), keywordIdsByPlatform.get("deepseek"));
        assertEquals(keywordIdsByPlatform.get("deepseek"), keywordIdsByPlatform.get("hunyuan"));
        assertEquals(keywordIdsByPlatform.get("deepseek"), keywordIdsByPlatform.get("doubao"));
        assertEquals(keywordIdsByPlatform.get("deepseek"), keywordIdsByPlatform.get("qwen"));
        assertEquals(3, rotation.getRotationOffset());
        verify(taskService).enqueueQuestionPollShardTasksWithStagger(any());
    }

    private DispatchQuestionPollPlanningService service(DispatchProperties properties) {
        return new DispatchQuestionPollPlanningService(
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
    }

    private ProjectKeywordGroupRel rel(Long groupId) {
        ProjectKeywordGroupRel rel = new ProjectKeywordGroupRel();
        rel.setKeywordGroupId(groupId);
        return rel;
    }

    private KeywordGroupResult keyword(Long id, String text) {
        KeywordGroupResult result = new KeywordGroupResult();
        result.setId(id);
        result.setKeywordText(text);
        result.setQuestionTier("A");
        return result;
    }

    private AiPlatformConfig platform(Long id, String code) {
        AiPlatformConfig platform = new AiPlatformConfig();
        platform.setId(id);
        platform.setPlatformCode(code);
        platform.setPlatformName(code);
        return platform;
    }

    private static PollBatchShard copyShard(PollBatchShard source) {
        PollBatchShard copy = new PollBatchShard();
        copy.setId(source.getId());
        copy.setPlatformCode(source.getPlatformCode());
        return copy;
    }

    private static PollBatchShardItem copyItem(PollBatchShardItem source) {
        PollBatchShardItem copy = new PollBatchShardItem();
        copy.setShardId(source.getShardId());
        copy.setKeywordResultId(source.getKeywordResultId());
        copy.setSortOrder(source.getSortOrder());
        return copy;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }
}
