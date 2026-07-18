package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.module.content.mapper.ArticleBatchMapper;
import com.huanjing.geo.module.content.mapper.ArticleGenerationLogMapper;
import com.huanjing.geo.module.content.mapper.PackageContentConfigMapper;
import com.huanjing.geo.module.content.service.ArticleGenerationPersistenceService;
import com.huanjing.geo.module.content.service.ContentArticleService;
import com.huanjing.geo.module.content.service.GeoPromptBuilder;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.customer.service.BrandStatementService;
import com.huanjing.geo.module.customer.service.CompanyPackageBindingService;
import com.huanjing.geo.module.dispatch.config.DispatchProperties;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardItemMapper;
import com.huanjing.geo.module.dispatch.mapper.PollDailyStatMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.dispatch.mapper.ProjectPollRotationMapper;
import com.huanjing.geo.common.llm.LlmCallFacade;
import com.huanjing.geo.common.llm.capacity.LlmCapacityFailureClassifier;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DispatchExecutionServicePlatformCandidateTest {

    @Test
    void brandStatementUsesRandomEnabledPlatformCandidates() throws Exception {
        AiPlatformConfigMapper platformMapper = mock(AiPlatformConfigMapper.class);

        AiPlatformConfig p0 = platform(1L, "p0", "P0");
        AiPlatformConfig p1 = platform(2L, "p1", "P1");
        when(platformMapper.selectList(any())).thenReturn(List.of(p0, p1));

        DispatchExecutionService service = service(platformMapper);

        List<AiPlatformConfig> candidates = resolvePlatformCandidates(
                service,
                100L,
                DispatchTaskType.BRAND_STATEMENT_GENERATION
        );

        assertEquals(Set.of("p0", "p1"), candidates.stream().map(AiPlatformConfig::getPlatformCode).collect(Collectors.toSet()));
    }

    @Test
    void biDailyPollUsesQuestionPollPlatformPool() throws Exception {
        AiPlatformConfigMapper platformMapper = mock(AiPlatformConfigMapper.class);
        AiPlatformConfig qwen = platform(3L, "qwen", "P1");
        qwen.setEnabledForQuestionPoll(true);
        when(platformMapper.selectList(any())).thenReturn(List.of(qwen));

        DispatchExecutionService service = service(platformMapper);

        List<AiPlatformConfig> candidates = resolvePlatformCandidates(
                service,
                100L,
                DispatchTaskType.BI_DAILY_POLL
        );

        assertEquals(List.of(qwen), candidates);
    }

    @Test
    void contentGenerationArticleCandidatesApplyExcludedPlatformFilter() throws Exception {
        AiPlatformConfigMapper platformMapper = mock(AiPlatformConfigMapper.class);
        AiPlatformConfig hunyuan = platform(2L, "hunyuan", "P0");
        hunyuan.setEnabledForArticle(true);
        AiPlatformConfig qwen = platform(3L, "qwen", "P0");
        qwen.setEnabledForArticle(true);
        when(platformMapper.selectList(any())).thenReturn(List.of(hunyuan, qwen));

        DispatchExecutionService service = service(platformMapper);
        ReflectionTestUtils.setField(service, "articleExcludedPlatformCodes", "hunyuan,yuanbao");

        List<AiPlatformConfig> candidates = resolvePlatformCandidates(
                service,
                100L,
                DispatchTaskType.CONTENT_GENERATION
        );

        assertEquals(List.of(qwen), candidates);
    }

    @Test
    void monitoringRequestTimeoutIsCappedBelowShardBudget() throws Exception {
        DispatchProperties properties = new DispatchProperties();
        properties.setModelRequestTimeoutMs(180_000);
        DispatchExecutionService service = service(mock(AiPlatformConfigMapper.class), properties);
        AiPlatformConfig platform = platform(3L, "qwen", "P1");
        platform.setTimeoutMs(60_000);
        DispatchTask task = new DispatchTask();
        task.setTimeoutAt(LocalDateTime.now().plusMinutes(61));

        Integer timeoutMs = resolveMonitoringRequestTimeoutMs(service, platform, task, 20);

        assertEquals(120_000, timeoutMs);
    }

    @Test
    void monitoringRequestTimeoutShrinksWithRemainingTaskBudget() throws Exception {
        DispatchProperties properties = new DispatchProperties();
        properties.setModelRequestTimeoutMs(180_000);
        DispatchExecutionService service = service(mock(AiPlatformConfigMapper.class), properties);
        AiPlatformConfig platform = platform(3L, "qwen", "P1");
        platform.setTimeoutMs(180_000);
        DispatchTask task = new DispatchTask();
        task.setTimeoutAt(LocalDateTime.now().plusMinutes(15));

        Integer timeoutMs = resolveMonitoringRequestTimeoutMs(service, platform, task, 20);

        org.junit.jupiter.api.Assertions.assertTrue(timeoutMs <= 43_000 && timeoutMs >= 40_000);
    }

    @Test
    void monitoringRequestTimeoutReturnsNullWhenTaskBudgetIsExhausted() throws Exception {
        DispatchExecutionService service = service(mock(AiPlatformConfigMapper.class));
        AiPlatformConfig platform = platform(3L, "qwen", "P1");
        DispatchTask task = new DispatchTask();
        task.setTimeoutAt(LocalDateTime.now().plusSeconds(30));

        Integer timeoutMs = resolveMonitoringRequestTimeoutMs(service, platform, task, 1);

        assertNull(timeoutMs);
    }

    @Test
    void questionPollModelTierDefaultsToPrimaryModel() throws Exception {
        DispatchExecutionService service = service(mock(AiPlatformConfigMapper.class));
        AiPlatformConfig platform = platform(3L, "qwen", "P1");
        platform.setModelId("qwen-max");
        platform.setLowModelId("qwen-turbo");

        AiPlatformConfig resolved = resolveQuestionPollModelConfig(service, platform);

        assertEquals("qwen-max", resolved.getModelId());
    }

    @Test
    void questionPollModelTierCanOptIntoLowModel() throws Exception {
        DispatchProperties properties = new DispatchProperties();
        properties.setQuestionPollModelTier("low");
        DispatchExecutionService service = service(mock(AiPlatformConfigMapper.class), properties);
        AiPlatformConfig platform = platform(3L, "qwen", "P1");
        platform.setModelId("qwen-max");
        platform.setLowModelId("qwen-turbo");

        AiPlatformConfig resolved = resolveQuestionPollModelConfig(service, platform);

        assertEquals("qwen-turbo", resolved.getModelId());
        assertEquals("qwen-max", platform.getModelId());
    }

    @SuppressWarnings("unchecked")
    private static List<AiPlatformConfig> resolvePlatformCandidates(DispatchExecutionService service,
                                                                    Long projectId,
                                                                    DispatchTaskType type) throws Exception {
        Method method = DispatchExecutionService.class.getDeclaredMethod(
                "resolvePlatformCandidates",
                Long.class,
                DispatchTaskType.class
        );
        method.setAccessible(true);
        return (List<AiPlatformConfig>) method.invoke(service, projectId, type);
    }

    private static AiPlatformConfig platform(Long id, String code, String level) {
        AiPlatformConfig config = new AiPlatformConfig();
        config.setId(id);
        config.setPlatformCode(code);
        config.setPriorityLevel(level);
        config.setEnabled(true);
        config.setEnabledForPresale(true);
        return config;
    }

    private static Integer resolveMonitoringRequestTimeoutMs(DispatchExecutionService service,
                                                             AiPlatformConfig platform,
                                                             DispatchTask task,
                                                             int remainingItems) throws Exception {
        Method method = DispatchExecutionService.class.getDeclaredMethod(
                "resolveMonitoringRequestTimeoutMs",
                AiPlatformConfig.class,
                DispatchTask.class,
                int.class
        );
        method.setAccessible(true);
        return (Integer) method.invoke(service, platform, task, remainingItems);
    }

    private static AiPlatformConfig resolveQuestionPollModelConfig(DispatchExecutionService service,
                                                                   AiPlatformConfig platform) throws Exception {
        Method method = DispatchExecutionService.class.getDeclaredMethod(
                "resolveQuestionPollModelConfig",
                AiPlatformConfig.class
        );
        method.setAccessible(true);
        return (AiPlatformConfig) method.invoke(service, platform);
    }

    private static DispatchExecutionService service(AiPlatformConfigMapper platformMapper) {
        return service(platformMapper, new DispatchProperties());
    }

    private static DispatchExecutionService service(AiPlatformConfigMapper platformMapper, DispatchProperties dispatchProperties) {
        return new DispatchExecutionService(
                platformMapper,
                mock(PlatformCredentialService.class),
                mock(LlmCallFacade.class),
                mock(ProjectMapper.class),
                mock(PackageContentConfigMapper.class),
                mock(ArticleBatchMapper.class),
                mock(ArticleGenerationLogMapper.class),
                mock(ContentArticleService.class),
                mock(GeoPromptBuilder.class),
                mock(ArticleGenerationPersistenceService.class),
                mock(ArticleGenerationWindowLockService.class),
                mock(PollBatchMapper.class),
                mock(PollBatchShardItemMapper.class),
                mock(PollResultMapper.class),
                mock(CompanyMapper.class),
                mock(BrandMapper.class),
                mock(CompanyPackageBindingService.class),
                mock(BrandStatementService.class),
                mock(SysDictItemMapper.class),
                dispatchProperties,
                mock(DispatchQuestionPollPlanningService.class),
                mock(DispatchPollShardPersistenceService.class),
                mock(DispatchPollAggregationService.class),
                mock(com.huanjing.geo.module.dispatch.websearch.WebSearchPollExecutionService.class),
                new LlmCapacityFailureClassifier()
        );
    }
}
