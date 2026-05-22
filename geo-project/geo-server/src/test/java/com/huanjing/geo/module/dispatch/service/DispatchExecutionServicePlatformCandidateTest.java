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
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.PollBatchMapper;
import com.huanjing.geo.module.dispatch.mapper.PollBatchShardItemMapper;
import com.huanjing.geo.module.dispatch.mapper.PollDailyStatMapper;
import com.huanjing.geo.module.dispatch.mapper.PollResultMapper;
import com.huanjing.geo.module.dispatch.mapper.ProjectPollRotationMapper;
import com.huanjing.geo.common.llm.router.LlmPlatformRouter;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.mapper.ProjectKeywordGroupRelMapper;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.AiPlatformConfig;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.PlatformCredentialService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static DispatchExecutionService service(AiPlatformConfigMapper platformMapper) {
        return new DispatchExecutionService(
                platformMapper,
                mock(PlatformCredentialService.class),
                mock(PlatformRateLimiterService.class),
                mock(PlatformConcurrencyLimiterService.class),
                mock(LlmPlatformRouter.class),
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
                new DispatchProperties(),
                mock(DispatchQuestionPollPlanningService.class),
                mock(DispatchPollShardPersistenceService.class),
                mock(DispatchPollAggregationService.class)
        );
    }
}
