package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateResponse;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaAutoScheduleRequest;
import com.huanjing.geo.module.content.dto.SelfMediaPublishScheduleCreateRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleBatch;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleConfig;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleRequest;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleBatchMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleConfigMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleRequestMapper;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformCapabilityContract;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformPublishChannel;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleMode;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleRules;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleCreateResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaScheduleBatchDetailVO;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
import com.huanjing.geo.module.project.entity.KeywordGroupResult;
import com.huanjing.geo.module.project.mapper.KeywordGroupResultMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectSelfMediaScheduleServiceTest {
    private ProjectMapper projectMapper;
    private BrandMapper brandMapper;
    private ProjectSelfMediaScheduleConfigMapper configMapper;
    private ProjectSelfMediaScheduleBatchMapper batchMapper;
    private ArticleDraftMapper articleDraftMapper;
    private SelfMediaAccountMapper selfMediaAccountMapper;
    private SelfMediaPublishScheduleMapper selfMediaPublishScheduleMapper;
    private SelfMediaPublishScheduleRequestMapper selfMediaPublishScheduleRequestMapper;
    private BatchArticleGenerationTaskMapper generationTaskMapper;
    private KeywordGroupResultMapper keywordGroupResultMapper;
    private CompanyChannelQuotaService companyChannelQuotaService;
    private SelfMediaPublishAutoScheduleService autoScheduleService;
    private SelfMediaPublishScheduleService scheduleService;
    private BatchArticleGenerationService generationService;
    private TemplatePerspectiveService perspectiveService;
    private ArticleTemplateAllocationService templateAllocationService;
    private ArticleCoverSelectionService coverSelectionService;
    private BusinessCalendarService businessCalendarService;
    private BrandAccessService brandAccessService;
    private SelfMediaPlatformScheduleAdapterRouter scheduleAdapterRouter;
    private LocalAgentSessionMapper localAgentSessionMapper;
    private ProjectSelfMediaScheduleService service;

    @BeforeEach
    void setUp() {
        projectMapper = mock(ProjectMapper.class);
        brandMapper = mock(BrandMapper.class);
        configMapper = mock(ProjectSelfMediaScheduleConfigMapper.class);
        batchMapper = mock(ProjectSelfMediaScheduleBatchMapper.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        selfMediaPublishScheduleMapper = mock(SelfMediaPublishScheduleMapper.class);
        selfMediaPublishScheduleRequestMapper = mock(SelfMediaPublishScheduleRequestMapper.class);
        generationTaskMapper = mock(BatchArticleGenerationTaskMapper.class);
        keywordGroupResultMapper = mock(KeywordGroupResultMapper.class);
        companyChannelQuotaService = mock(CompanyChannelQuotaService.class);
        autoScheduleService = mock(SelfMediaPublishAutoScheduleService.class);
        scheduleService = mock(SelfMediaPublishScheduleService.class);
        generationService = mock(BatchArticleGenerationService.class);
        perspectiveService = mock(TemplatePerspectiveService.class);
        templateAllocationService = mock(ArticleTemplateAllocationService.class);
        coverSelectionService = mock(ArticleCoverSelectionService.class);
        businessCalendarService = mock(BusinessCalendarService.class);
        brandAccessService = mock(BrandAccessService.class);
        scheduleAdapterRouter = mock(SelfMediaPlatformScheduleAdapterRouter.class);
        localAgentSessionMapper = mock(LocalAgentSessionMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser user = new SysUser();
        user.setId(99L);
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(perspectiveService.resolve(any(), anyString(), anyString()))
                .thenReturn(TemplatePerspectiveService.ResolvedPerspective.customer());
        when(businessCalendarService.allPublishSlots(any(), anyBoolean()))
                .thenReturn(List.of(slot(11, 9), slot(12, 9), slot(13, 9), slot(14, 9), slot(15, 9)));
        when(scheduleAdapterRouter.rules(anyString(), anyString()))
                .thenReturn(SelfMediaPlatformScheduleRules.defaults());

        service = new ProjectSelfMediaScheduleService(
                projectMapper,
                brandMapper,
                configMapper,
                batchMapper,
                articleDraftMapper,
                selfMediaAccountMapper,
                selfMediaPublishScheduleMapper,
                selfMediaPublishScheduleRequestMapper,
                generationTaskMapper,
                keywordGroupResultMapper,
                autoScheduleService,
                scheduleService,
                generationService,
                perspectiveService,
                templateAllocationService,
                coverSelectionService,
                businessCalendarService,
                companyChannelQuotaService,
                brandAccessService,
                currentUserService,
                localAgentSessionMapper,
                new ObjectMapper(),
                scheduleAdapterRouter
        );
        service.setClock(Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.systemDefault()));
    }

    @Test
    void createForProjectRejectsWhenSwitchDisabled() {
        ProjectSelfMediaScheduleConfig config = config(false);
        when(configMapper.selectByProjectId(7L)).thenReturn(config);

        assertThrows(BizException.class, () -> service.createForProject(7L, request(), "manual"));
        verify(brandAccessService).requireBrandAccess(8L, 99L, BrandAccessAction.OPERATE);
    }

    @Test
    void createForProjectRejectsWhenMonthAlreadyCreated() {
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setStatus("processing");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);

        assertThrows(BizException.class, () -> service.createForProject(7L, request(), "manual"));
    }

    @Test
    void createForProjectReusesFailedMonthBatch() {
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setId(33L);
        batch.setProjectId(7L);
        batch.setBrandId(8L);
        batch.setCompanyId(6L);
        batch.setTargetMonth("2026-06");
        batch.setStatus("failed");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(6L, "toutiao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 1));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        KeywordGroupResult question = new KeywordGroupResult();
        question.setKeywordText("测试问题");
        question.setSceneCode("news_brief");
        when(keywordGroupResultMapper.selectProjectQuestionsByTiers(7L, "'A'")).thenReturn(List.of(question));
        BatchArticleGenerateResponse generation = new BatchArticleGenerateResponse(44L, 1, "pending", false, false, List.of());
        when(generationService.createSystemBatch(any(), eq(99L))).thenReturn(generation);
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(55L);
        task.setBatchId(44L);
        when(generationTaskMapper.selectList(any())).thenReturn(List.of(task));

        service.createForProject(7L, request(), "manual");

        ArgumentCaptor<ProjectSelfMediaScheduleBatch> captor = ArgumentCaptor.forClass(ProjectSelfMediaScheduleBatch.class);
        verify(batchMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        ProjectSelfMediaScheduleBatch updated = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(33L, updated.getId());
        assertEquals("processing", updated.getStatus());
        assertEquals(1, updated.getPlannedCount());
        assertEquals(0, updated.getCreatedCount());
        assertEquals(0, updated.getRejectedCount());
    }

    @Test
    void createForProjectUsesTemplateSceneTopicForThirdPartyPerspective() {
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(6L, "toutiao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 1));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        KeywordGroupResult brandQuestion = new KeywordGroupResult();
        brandQuestion.setKeywordText("现在的行业趋势到底怎么样");
        brandQuestion.setSceneCode("brand");
        KeywordGroupResult decisionQuestion = new KeywordGroupResult();
        decisionQuestion.setKeywordText("这类服务应该怎么选");
        decisionQuestion.setSceneCode("decision");
        when(keywordGroupResultMapper.selectProjectQuestionsByTiers(7L, "'A'"))
                .thenReturn(List.of(brandQuestion, decisionQuestion));
        when(perspectiveService.resolve(8L, "self_media", "toutiao"))
                .thenReturn(new TemplatePerspectiveService.ResolvedPerspective("review_recommend", "channel", 99L));
        when(templateAllocationService.activeTemplates("self_media", "toutiao", null, "review_recommend"))
                .thenReturn(List.of(templateWithScene("decision")));
        BatchArticleGenerateResponse generation = new BatchArticleGenerateResponse(44L, 1, "pending", false, false, List.of());
        when(generationService.createSystemBatch(any(), eq(99L))).thenReturn(generation);
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(55L);
        task.setBatchId(44L);
        when(generationTaskMapper.selectList(any())).thenReturn(List.of(task));

        service.createForProject(7L, request(), "manual");

        ArgumentCaptor<BatchArticleGenerateRequest> captor = ArgumentCaptor.forClass(BatchArticleGenerateRequest.class);
        verify(generationService).createSystemBatch(captor.capture(), eq(99L));
        assertEquals("decision", captor.getValue().getTopics().get(0).getQuestionSceneCode());
        assertEquals("这类服务应该怎么选", captor.getValue().getTopics().get(0).getTopic());
    }

    @Test
    void createForProjectDoesNotRequireSourceQuestionsForThirdPartyPerspective() {
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(6L, "toutiao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 1));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(keywordGroupResultMapper.selectProjectQuestionsByTiers(7L, "'A'")).thenReturn(List.of());
        when(perspectiveService.resolve(8L, "self_media", "toutiao"))
                .thenReturn(new TemplatePerspectiveService.ResolvedPerspective("review_recommend", "channel", 99L));
        when(templateAllocationService.activeTemplates("self_media", "toutiao", null, "review_recommend"))
                .thenReturn(List.of(templateWithScene("brand")));
        BatchArticleGenerateResponse generation = new BatchArticleGenerateResponse(44L, 1, "pending", false, false, List.of());
        when(generationService.createSystemBatch(any(), eq(99L))).thenReturn(generation);
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(55L);
        task.setBatchId(44L);
        when(generationTaskMapper.selectList(any())).thenReturn(List.of(task));

        service.createForProject(7L, request(), "manual");

        ArgumentCaptor<BatchArticleGenerateRequest> captor = ArgumentCaptor.forClass(BatchArticleGenerateRequest.class);
        verify(generationService).createSystemBatch(captor.capture(), eq(99L));
        assertEquals("brand", captor.getValue().getTopics().get(0).getQuestionSceneCode());
        assertEquals("这个品牌是否适合当前需求", captor.getValue().getTopics().get(0).getTopic());
    }

    @Test
    void createDueEnabledProjectsContinuesAfterSingleProjectFailure() {
        ProjectSelfMediaScheduleConfig first = config(true);
        first.setProjectId(1L);
        ProjectSelfMediaScheduleConfig second = config(true);
        second.setProjectId(2L);
        when(configMapper.selectEnabled(2)).thenReturn(List.of(first, second));
        when(batchMapper.selectByProjectAndMonth(1L, "2026-06")).thenThrow(new RuntimeException("broken project"));
        ProjectSelfMediaScheduleBatch existing = new ProjectSelfMediaScheduleBatch();
        existing.setId(22L);
        when(batchMapper.selectByProjectAndMonth(2L, "2026-06")).thenReturn(existing);

        assertEquals(0, service.createDueEnabledProjects("2026-06", 2));
        verify(batchMapper).selectByProjectAndMonth(1L, "2026-06");
        verify(batchMapper).selectByProjectAndMonth(2L, "2026-06");
    }

    @Test
    void previewForProjectUsesQuotaWithoutArticleInventory() {
        ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
        request.setTargetMonth("2026-06");
        request.setSelfMediaAccountIds(List.of(20L));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(6L, "toutiao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 2, 5));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(1L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(11, 9), slot(12, 9), slot(13, 9)));
        when(scheduleAdapterRouter.rules("toutiao", "platform_schedule"))
                .thenReturn(SelfMediaPlatformScheduleRules.defaults());

        assertEquals(3, service.previewForProject(7L, request).getPlannedCount());
    }

    @Test
    void previewForProjectReportsInsufficientFutureSlots() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 21, 9, 0)));
        ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
        request.setTargetMonth("2026-06");
        request.setSelfMediaAccountIds(List.of(20L));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(6L, "toutiao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 2));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(LocalDateTime.of(2026, 6, 21, 9, 10))));
        when(scheduleAdapterRouter.rules("toutiao", "platform_schedule"))
                .thenReturn(new SelfMediaPlatformScheduleRules(10, 120, 4, 7 * 24 * 60));

        var response = service.previewForProject(7L, request);

        assertEquals(2, response.getRequestedCount());
        assertEquals(2, response.getRejectedCount());
        assertEquals(1, response.getSlotGroups().size());
        assertEquals(false, response.getSlotGroups().get(0).getEnough());
        assertTrue(response.getSlotGroups().get(0).getMessage().contains("本月剩余可自动排期时间不足"));
    }

    @Test
    void retryAbnormalScheduleItemsRetriesExistingFailedSchedules() {
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        batch.setStatus("partial_failed");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(generationTaskMapper.selectById(55L)).thenReturn(generationTask(55L, 66L));
        SelfMediaPublishSchedule schedule = new SelfMediaPublishSchedule();
        schedule.setId(88L);
        schedule.setStatus("manual_required");
        schedule.setPlannedPublishAt(LocalDateTime.of(2026, 6, 1, 9, 10));
        when(selfMediaPublishScheduleMapper.selectLatestByArticleAccountAndPlatform(66L, 20L, "toutiao"))
                .thenReturn(schedule);
        SelfMediaPublishScheduleVO retried = new SelfMediaPublishScheduleVO();
        retried.setId(88L);
        when(scheduleService.retryNow(88L)).thenReturn(retried);

        ProjectSelfMediaScheduleBatchDetailVO detail = service.retryAbnormalScheduleItems(7L, "2026-06");

        assertEquals(2, detail.getItems().size());
        verify(scheduleService).retryNow(88L);
    }

    @Test
    void markAbnormalScheduleItemsManualRequiredMarksFailedSchedules() {
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        batch.setStatus("partial_failed");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(generationTaskMapper.selectById(55L)).thenReturn(generationTask(55L, 66L));
        SelfMediaPublishSchedule schedule = new SelfMediaPublishSchedule();
        schedule.setId(88L);
        schedule.setStatus("schedule_failed");
        schedule.setPlannedPublishAt(LocalDateTime.of(2026, 6, 1, 9, 10));
        when(selfMediaPublishScheduleMapper.selectLatestByArticleAccountAndPlatform(66L, 20L, "toutiao"))
                .thenReturn(schedule);
        SelfMediaPublishScheduleVO marked = new SelfMediaPublishScheduleVO();
        marked.setId(88L);
        when(scheduleService.markManualRequired(eq(88L), anyString())).thenReturn(marked);

        ProjectSelfMediaScheduleBatchDetailVO detail =
                service.markAbnormalScheduleItemsManualRequired(7L, "2026-06");

        assertEquals(2, detail.getItems().size());
        verify(scheduleService).markManualRequired(eq(88L), anyString());
        verify(scheduleService, never()).retryNow(88L);
    }

    @Test
    void progressProcessingBatchesKeepsSuccessfulSchedulesWhenOnePlanFails() {
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setId(33L);
        batch.setProjectId(7L);
        batch.setBrandId(8L);
        batch.setCompanyId(6L);
        batch.setTargetMonth("2026-06");
        batch.setStatus("processing");
        batch.setCreatedBy(99L);
        batch.setRequestPayload("""
                {
                  "targetMonth": "2026-06",
                  "scheduleStrategy": "platform_schedule",
                  "includeAdjustedWorkdays": false,
                  "plans": [
                    {"generationBatchId": 44, "generationTaskId": 55, "selfMediaAccountId": 20, "platform": "toutiao"},
                    {"generationBatchId": 44, "generationTaskId": 56, "selfMediaAccountId": 21, "platform": "toutiao"}
                  ]
                }
                """);
        when(batchMapper.selectProcessing(5)).thenReturn(List.of(batch));

        BatchArticleGenerationTask firstTask = generationTask(55L, 66L);
        BatchArticleGenerationTask secondTask = generationTask(56L, 67L);
        when(generationTaskMapper.selectById(55L)).thenReturn(firstTask);
        when(generationTaskMapper.selectById(56L)).thenReturn(secondTask);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(11, 10), slot(12, 15)));
        when(scheduleAdapterRouter.rules("toutiao", "platform_schedule"))
                .thenReturn(SelfMediaPlatformScheduleRules.defaults());

        SelfMediaPublishScheduleCreateResponse created = new SelfMediaPublishScheduleCreateResponse();
        SelfMediaPublishScheduleVO createdSchedule = new SelfMediaPublishScheduleVO();
        createdSchedule.setId(88L);
        created.getCreatedSchedules().add(createdSchedule);
        when(scheduleService.createSystemSchedules(any(), eq("project-auto-33-55"), eq(99L))).thenReturn(created);
        when(scheduleService.createSystemSchedules(any(), eq("project-auto-33-56"), eq(99L)))
                .thenThrow(new BizException(70040, "account not ready"));

        int processed = service.progressProcessingBatches(5);

        assertEquals(1, processed);
        ArgumentCaptor<ProjectSelfMediaScheduleBatch> captor = ArgumentCaptor.forClass(ProjectSelfMediaScheduleBatch.class);
        verify(batchMapper).updateById(captor.capture());
        assertEquals("partial_failed", captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getCreatedCount());
        assertEquals(1, captor.getValue().getRejectedCount());
        verify(scheduleService).createSystemSchedules(any(), eq("project-auto-33-55"), eq(99L));
        verify(scheduleService).createSystemSchedules(any(), eq("project-auto-33-56"), eq(99L));
    }

    @Test
    void progressProcessingBatchesFillsRandomCoverBeforeCreatingScheduleWhenPlatformRequiresCover() {
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setId(33L);
        batch.setProjectId(7L);
        batch.setBrandId(8L);
        batch.setCompanyId(6L);
        batch.setTargetMonth("2026-06");
        batch.setStatus("processing");
        batch.setCreatedBy(99L);
        batch.setRequestPayload("""
                {
                  "targetMonth": "2026-06",
                  "scheduleStrategy": "platform_schedule",
                  "includeAdjustedWorkdays": false,
                  "plans": [
                    {"generationBatchId": 44, "generationTaskId": 55, "selfMediaAccountId": 20, "platform": "toutiao"}
                  ]
                }
                """);
        when(batchMapper.selectProcessing(5)).thenReturn(List.of(batch));
        when(generationTaskMapper.selectById(55L)).thenReturn(generationTask(55L, 66L));
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(11, 10)));
        when(scheduleAdapterRouter.contract("toutiao")).thenReturn(Optional.of(new SelfMediaPlatformCapabilityContract(
                "toutiao",
                "头条",
                SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION,
                SelfMediaPlatformScheduleMode.PLATFORM_NATIVE,
                SelfMediaPlatformScheduleRules.defaults(),
                true,
                false,
                false,
                true
        )));
        when(scheduleAdapterRouter.rules("toutiao", "platform_schedule"))
                .thenReturn(SelfMediaPlatformScheduleRules.defaults());
        ArticleDraft article = new ArticleDraft();
        article.setId(66L);
        article.setProjectId(7L);
        article.setSubjectBrandId(18L);
        article.setTitle("测试文章");
        when(articleDraftMapper.selectById(66L)).thenReturn(article);
        when(coverSelectionService.selectRandomCoverUrl(18L)).thenReturn("https://example.com/cover.jpg");

        SelfMediaPublishScheduleCreateResponse created = new SelfMediaPublishScheduleCreateResponse();
        SelfMediaPublishScheduleVO createdSchedule = new SelfMediaPublishScheduleVO();
        createdSchedule.setId(88L);
        created.getCreatedSchedules().add(createdSchedule);
        ArgumentCaptor<SelfMediaPublishScheduleCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(SelfMediaPublishScheduleCreateRequest.class);
        when(scheduleService.createSystemSchedules(requestCaptor.capture(), eq("project-auto-33-55"), eq(99L)))
                .thenReturn(created);

        int processed = service.progressProcessingBatches(5);

        assertEquals(1, processed);
        assertEquals("https://example.com/cover.jpg", article.getCoverImageUrl());
        assertEquals(LocalDateTime.of(2026, 6, 11, 10, 10), requestCaptor.getValue().getWindowStart());
        assertEquals(LocalDateTime.of(2026, 6, 11, 10, 10), requestCaptor.getValue().getWindowEnd());
        assertEquals(3, requestCaptor.getValue().getMinIntervalMinutes());
        verify(coverSelectionService).selectRandomCoverUrl(18L);
        verify(articleDraftMapper).updateById(article);
        verify(scheduleService).createSystemSchedules(any(), eq("project-auto-33-55"), eq(99L));
    }

    @Test
    void progressProcessingBatchesSpreadsSlotsPerPlatform() {
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setId(33L);
        batch.setProjectId(7L);
        batch.setBrandId(8L);
        batch.setCompanyId(6L);
        batch.setTargetMonth("2026-06");
        batch.setStatus("processing");
        batch.setCreatedBy(99L);
        batch.setRequestPayload("""
                {
                  "targetMonth": "2026-06",
                  "scheduleStrategy": "platform_schedule",
                  "includeAdjustedWorkdays": false,
                  "plans": [
                    {"generationBatchId": 44, "generationTaskId": 55, "selfMediaAccountId": 20, "platform": "toutiao"},
                    {"generationBatchId": 44, "generationTaskId": 56, "selfMediaAccountId": 21, "platform": "xiaohongshu"},
                    {"generationBatchId": 44, "generationTaskId": 57, "selfMediaAccountId": 20, "platform": "toutiao"},
                    {"generationBatchId": 44, "generationTaskId": 58, "selfMediaAccountId": 21, "platform": "xiaohongshu"}
                  ]
                }
                """);
        when(batchMapper.selectProcessing(5)).thenReturn(List.of(batch));
        when(generationTaskMapper.selectById(55L)).thenReturn(generationTask(55L, 66L));
        when(generationTaskMapper.selectById(56L)).thenReturn(generationTask(56L, 67L));
        when(generationTaskMapper.selectById(57L)).thenReturn(generationTask(57L, 68L));
        when(generationTaskMapper.selectById(58L)).thenReturn(generationTask(58L, 69L));
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(11, 9), slot(12, 9), slot(29, 14), slot(30, 14)));
        when(scheduleAdapterRouter.rules("toutiao", "platform_schedule"))
                .thenReturn(SelfMediaPlatformScheduleRules.defaults());
        when(scheduleAdapterRouter.rules("xiaohongshu", "platform_schedule"))
                .thenReturn(SelfMediaPlatformScheduleRules.defaults());

        SelfMediaPublishScheduleCreateResponse created = new SelfMediaPublishScheduleCreateResponse();
        SelfMediaPublishScheduleVO createdSchedule = new SelfMediaPublishScheduleVO();
        createdSchedule.setId(88L);
        created.getCreatedSchedules().add(createdSchedule);
        ArgumentCaptor<SelfMediaPublishScheduleCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(SelfMediaPublishScheduleCreateRequest.class);
        when(scheduleService.createSystemSchedules(requestCaptor.capture(), anyString(), eq(99L))).thenReturn(created);

        int processed = service.progressProcessingBatches(5);

        assertEquals(1, processed);
        assertEquals(LocalDateTime.of(2026, 6, 11, 9, 10), requestCaptor.getAllValues().get(0).getWindowStart());
        assertEquals(LocalDateTime.of(2026, 6, 11, 9, 10), requestCaptor.getAllValues().get(1).getWindowStart());
        assertEquals(LocalDateTime.of(2026, 6, 29, 14, 10), requestCaptor.getAllValues().get(2).getWindowStart());
        assertEquals(LocalDateTime.of(2026, 6, 29, 14, 10), requestCaptor.getAllValues().get(3).getWindowStart());
    }

    @Test
    void progressProcessingBatchesSkipsExpiredAndTooCloseSlots() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 21, 9, 0)));
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        batch.setRequestPayload("""
                {
                  "targetMonth": "2026-06",
                  "scheduleStrategy": "platform_schedule",
                  "includeAdjustedWorkdays": false,
                  "plans": [
                    {"generationBatchId": 44, "generationTaskId": 55, "selfMediaAccountId": 20, "platform": "toutiao"}
                  ]
                }
                """);
        when(batchMapper.selectProcessing(5)).thenReturn(List.of(batch));
        when(generationTaskMapper.selectById(55L)).thenReturn(generationTask(55L, 66L));
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(
                        slot(LocalDateTime.of(2026, 6, 1, 9, 0)),
                        slot(LocalDateTime.of(2026, 6, 21, 9, 10)),
                        slot(LocalDateTime.of(2026, 6, 25, 9, 0))
                ));
        when(scheduleAdapterRouter.rules("toutiao", "platform_schedule"))
                .thenReturn(new SelfMediaPlatformScheduleRules(10, 120, 4, 7 * 24 * 60));

        SelfMediaPublishScheduleCreateResponse created = new SelfMediaPublishScheduleCreateResponse();
        SelfMediaPublishScheduleVO createdSchedule = new SelfMediaPublishScheduleVO();
        createdSchedule.setId(88L);
        created.getCreatedSchedules().add(createdSchedule);
        ArgumentCaptor<SelfMediaPublishScheduleCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(SelfMediaPublishScheduleCreateRequest.class);
        when(scheduleService.createSystemSchedules(requestCaptor.capture(), anyString(), eq(99L))).thenReturn(created);

        int processed = service.progressProcessingBatches(5);

        assertEquals(1, processed);
        assertEquals(LocalDateTime.of(2026, 6, 25, 9, 10), requestCaptor.getValue().getWindowStart());
        assertEquals(LocalDateTime.of(2026, 6, 25, 9, 0), requestCaptor.getValue().getExecutionWindowStart());
    }

    @Test
    void progressProcessingBatchesMarksBatchFailedWhenAllGenerationTasksFailed() {
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        when(batchMapper.selectProcessing(5)).thenReturn(List.of(batch));

        BatchArticleGenerationTask firstTask = failedGenerationTask(55L, "invalid api key");
        BatchArticleGenerationTask secondTask = failedGenerationTask(56L, "invalid api key");
        when(generationTaskMapper.selectById(55L)).thenReturn(firstTask);
        when(generationTaskMapper.selectById(56L)).thenReturn(secondTask);

        int processed = service.progressProcessingBatches(5);

        assertEquals(1, processed);
        ArgumentCaptor<ProjectSelfMediaScheduleBatch> captor = ArgumentCaptor.forClass(ProjectSelfMediaScheduleBatch.class);
        verify(batchMapper).updateById(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getCreatedCount());
        assertEquals(2, captor.getValue().getRejectedCount());
        assertEquals("自动排期文章生成全部失败", captor.getValue().getFailureMessage());
    }

    @Test
    void getBatchDetailReturnsGenerationAndScheduleStatus() {
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setId(33L);
        batch.setProjectId(7L);
        batch.setBrandId(8L);
        batch.setCompanyId(6L);
        batch.setTargetMonth("2026-06");
        batch.setStatus("created");
        batch.setRequestPayload("""
                {
                  "targetMonth": "2026-06",
                  "scheduleStrategy": "platform_schedule",
                  "includeAdjustedWorkdays": false,
                  "plans": [
                    {
                      "generationBatchId": 44,
                      "generationTaskId": 55,
                      "selfMediaAccountId": 20,
                      "platform": "toutiao"
                    }
                  ]
                }
                """);
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());

        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(55L);
        task.setBatchId(44L);
        task.setStatus("success");
        task.setArticleId(66L);
        when(generationTaskMapper.selectById(55L)).thenReturn(task);

        ArticleDraft article = new ArticleDraft();
        article.setId(66L);
        article.setTitle("排期文章标题");
        when(articleDraftMapper.selectById(66L)).thenReturn(article);

        SelfMediaPublishScheduleRequest scheduleRequest = new SelfMediaPublishScheduleRequest();
        scheduleRequest.setId(77L);
        when(selfMediaPublishScheduleRequestMapper.selectByRequestKey(8L, "project-auto-33-55"))
                .thenReturn(scheduleRequest);

        SelfMediaPublishSchedule schedule = new SelfMediaPublishSchedule();
        schedule.setId(88L);
        schedule.setStatus("pending");
        schedule.setPlannedPublishAt(LocalDateTime.of(2026, 6, 11, 10, 0));
        when(selfMediaPublishScheduleMapper.selectByRequestId(77L)).thenReturn(List.of(schedule));

        ProjectSelfMediaScheduleBatchDetailVO detail = service.getBatchDetail(7L, "2026-06");

        assertEquals(33L, detail.getBatch().getId());
        assertEquals(1, detail.getItems().size());
        ProjectSelfMediaScheduleBatchDetailVO.Item item = detail.getItems().get(0);
        assertEquals(44L, item.getGenerationBatchId());
        assertEquals(55L, item.getGenerationTaskId());
        assertEquals("success", item.getGenerationStatus());
        assertEquals(66L, item.getArticleId());
        assertEquals("排期文章标题", item.getArticleTitle());
        assertEquals(20L, item.getSelfMediaAccountId());
        assertEquals("toutiao", item.getPlatform());
        assertEquals(88L, item.getScheduleId());
        assertEquals("pending", item.getScheduleStatus());
        assertEquals(LocalDateTime.of(2026, 6, 11, 10, 0), item.getPlannedPublishAt());
    }

    @Test
    void getBatchDetailReturnsScheduleRejectedReasonFromSnapshot() {
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setId(33L);
        batch.setProjectId(7L);
        batch.setBrandId(8L);
        batch.setCompanyId(6L);
        batch.setTargetMonth("2026-06");
        batch.setStatus("partial_failed");
        batch.setRequestPayload("""
                {
                  "targetMonth": "2026-06",
                  "scheduleStrategy": "platform_schedule",
                  "includeAdjustedWorkdays": false,
                  "plans": [
                    {
                      "generationBatchId": 44,
                      "generationTaskId": 55,
                      "selfMediaAccountId": 20,
                      "platform": "toutiao"
                    }
                  ]
                }
                """);
        batch.setResultSnapshot("""
                {
                  "rejectedItems": [
                    {
                      "articleId": 66,
                      "selfMediaAccountId": 20,
                      "platform": "toutiao",
                      "code": "ARTICLE_SELF_MEDIA_SCHEDULE_ACTIVE",
                      "message": "文章已有自媒体排期正在处理，不能重复创建分发任务"
                    }
                  ]
                }
                """);
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());

        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(55L);
        task.setBatchId(44L);
        task.setStatus("success");
        task.setArticleId(66L);
        when(generationTaskMapper.selectById(55L)).thenReturn(task);

        ArticleDraft article = new ArticleDraft();
        article.setId(66L);
        article.setTitle("排期文章标题");
        when(articleDraftMapper.selectById(66L)).thenReturn(article);
        when(selfMediaPublishScheduleRequestMapper.selectByRequestKey(8L, "project-auto-33-55"))
                .thenReturn(null);

        ProjectSelfMediaScheduleBatchDetailVO detail = service.getBatchDetail(7L, "2026-06");

        ProjectSelfMediaScheduleBatchDetailVO.Item item = detail.getItems().get(0);
        assertEquals("rejected", item.getScheduleStatus());
        assertEquals("ARTICLE_SELF_MEDIA_SCHEDULE_ACTIVE", item.getScheduleFailureCode());
        assertEquals("文章已有自媒体排期正在处理，不能重复创建分发任务", item.getScheduleFailureMessage());
        assertEquals(1, detail.getFailureSummaries().size());
        assertEquals("ARTICLE_SELF_MEDIA_SCHEDULE_ACTIVE", detail.getFailureSummaries().get(0).getCode());
        assertEquals("schedule_rejected", detail.getFailureSummaries().get(0).getCategory());
        assertEquals(1, detail.getFailureSummaries().get(0).getCount());
    }

    @Test
    void getBatchDetailShowsOperatorFriendlyMessageForReplacedSchedule() {
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setId(33L);
        batch.setProjectId(7L);
        batch.setBrandId(8L);
        batch.setCompanyId(6L);
        batch.setTargetMonth("2026-06");
        batch.setStatus("partial_failed");
        batch.setRequestPayload("""
                {
                  "targetMonth": "2026-06",
                  "scheduleStrategy": "platform_schedule",
                  "includeAdjustedWorkdays": false,
                  "plans": [
                    {
                      "generationBatchId": 44,
                      "generationTaskId": 55,
                      "selfMediaAccountId": 20,
                      "platform": "toutiao"
                    }
                  ]
                }
                """);
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());

        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(55L);
        task.setBatchId(44L);
        task.setStatus("success");
        task.setArticleId(66L);
        when(generationTaskMapper.selectById(55L)).thenReturn(task);

        SelfMediaPublishScheduleRequest scheduleRequest = new SelfMediaPublishScheduleRequest();
        scheduleRequest.setId(77L);
        when(selfMediaPublishScheduleRequestMapper.selectByRequestKey(8L, "project-auto-33-55"))
                .thenReturn(scheduleRequest);

        SelfMediaPublishSchedule schedule = new SelfMediaPublishSchedule();
        schedule.setId(88L);
        schedule.setStatus("cancelled");
        schedule.setFailureCode("REPLACED_BY_OPERATOR_QUICK_DISPATCH");
        schedule.setFailureMessage("运营点击平台快速分发时安全替换");
        when(selfMediaPublishScheduleMapper.selectByRequestId(77L)).thenReturn(List.of(schedule));

        ProjectSelfMediaScheduleBatchDetailVO detail = service.getBatchDetail(7L, "2026-06");

        ProjectSelfMediaScheduleBatchDetailVO.Item item = detail.getItems().get(0);
        assertEquals("当前任务排期已由手动触发占用", item.getScheduleFailureMessage());
        assertEquals("REPLACED_BY_NEW_TASK", item.getFailureGroupCode());
        assertEquals("手动触发已占用", item.getFailureGroupLabel());
    }

    @Test
    void getBatchDetailSettlesProcessingBatchWhenAllGenerationTasksFailed() {
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        SelfMediaAccount secondAccount = account();
        secondAccount.setId(21L);
        when(selfMediaAccountMapper.selectById(21L)).thenReturn(secondAccount);
        when(generationTaskMapper.selectById(55L)).thenReturn(failedGenerationTask(55L, "invalid api key"));
        when(generationTaskMapper.selectById(56L)).thenReturn(failedGenerationTask(56L, "invalid api key"));

        ProjectSelfMediaScheduleBatchDetailVO detail = service.getBatchDetail(7L, "2026-06");

        assertEquals("failed", detail.getBatch().getStatus());
        assertEquals(2, detail.getBatch().getRejectedCount());
        assertEquals(2, detail.getItems().size());
        assertEquals("failed", detail.getItems().get(0).getGenerationStatus());
        verify(batchMapper).updateById(batch);
    }

    @Test
    void retryFailedItemsRetriesFailedGenerationBatchesOnly() {
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        batch.setStatus("partial_failed");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(generationTaskMapper.selectById(55L)).thenReturn(failedGenerationTask(55L, "invalid api key"));
        when(generationTaskMapper.selectById(56L)).thenReturn(generationTask(56L, 66L));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        SelfMediaAccount secondAccount = account();
        secondAccount.setId(21L);
        when(selfMediaAccountMapper.selectById(21L)).thenReturn(secondAccount);

        ProjectSelfMediaScheduleBatchDetailVO detail = service.retryFailedItems(7L, "2026-06");

        assertEquals("processing", detail.getBatch().getStatus());
        assertEquals("失败项已重新入队，等待文章生成完成", detail.getBatch().getFailureMessage());
        verify(generationService).retryFailedSystem(44L);
        verify(batchMapper).updateById(batch);
    }

    @Test
    void retryFailedItemsRetriesRejectedScheduleCreation() {
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        batch.setStatus("partial_failed");
        batch.setCreatedCount(0);
        batch.setRejectedCount(1);
        batch.setResultSnapshot("""
                {
                  "rejectedItems": [
                    {
                      "articleId": 66,
                      "selfMediaAccountId": 20,
                      "platform": "toutiao",
                      "code": "PLATFORM_SCHEDULE_TIME_TOO_CLOSE",
                      "message": "平台定时发布时间需至少晚于执行填充时间 130 分钟"
                    }
                  ]
                }
                """);
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(generationTaskMapper.selectById(55L)).thenReturn(generationTask(55L, 66L));
        BatchArticleGenerationTask pendingTask = new BatchArticleGenerationTask();
        pendingTask.setId(56L);
        pendingTask.setBatchId(44L);
        pendingTask.setStatus("running");
        when(generationTaskMapper.selectById(56L)).thenReturn(pendingTask);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        SelfMediaAccount secondAccount = account();
        secondAccount.setId(21L);
        when(selfMediaAccountMapper.selectById(21L)).thenReturn(secondAccount);
        when(businessCalendarService.allPublishSlots(any(), eq(false)))
                .thenReturn(List.of(slot(15, 9)));
        when(scheduleAdapterRouter.rules(eq("toutiao"), anyString()))
                .thenReturn(new SelfMediaPlatformScheduleRules(130, 120, 4, 7 * 24 * 60));
        SelfMediaPublishScheduleCreateResponse created = new SelfMediaPublishScheduleCreateResponse();
        SelfMediaPublishScheduleVO schedule = new SelfMediaPublishScheduleVO();
        schedule.setId(88L);
        created.setCreatedSchedules(List.of(schedule));
        when(scheduleService.createSystemSchedules(any(), org.mockito.ArgumentMatchers.startsWith("project-auto-33-55-retry-"), eq(99L)))
                .thenReturn(created);

        ProjectSelfMediaScheduleBatchDetailVO detail = service.retryFailedItems(7L, "2026-06");

        assertEquals("created", detail.getBatch().getStatus());
        verify(scheduleService).createSystemSchedules(any(), org.mockito.ArgumentMatchers.startsWith("project-auto-33-55-retry-"), eq(99L));
        verify(generationService, never()).retryFailedSystem(any());
        verify(batchMapper).updateById(batch);
        assertEquals(1, batch.getCreatedCount());
        assertEquals(0, batch.getRejectedCount());
    }

    @Test
    void retryFailedItemsCreatesScheduleForGeneratedButUnscheduledHistory() {
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        batch.setStatus("partial_failed");
        batch.setCreatedCount(0);
        batch.setRejectedCount(1);
        batch.setRequestPayload("""
                {
                  "targetMonth": "2026-06",
                  "scheduleStrategy": "platform_schedule",
                  "includeAdjustedWorkdays": false,
                  "plans": [
                    {"generationBatchId": 44, "generationTaskId": 55, "selfMediaAccountId": 20, "platform": "toutiao"}
                  ]
                }
                """);
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(generationTaskMapper.selectById(55L)).thenReturn(generationTask(55L, 66L));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(selfMediaPublishScheduleRequestMapper.selectByRequestKey(8L, "project-auto-33-55"))
                .thenReturn(null);
        when(selfMediaPublishScheduleMapper.selectLatestByArticleAccountAndPlatform(66L, 20L, "toutiao"))
                .thenReturn(null);
        when(businessCalendarService.allPublishSlots(any(), eq(false)))
                .thenReturn(List.of(slot(15, 9)));
        when(scheduleAdapterRouter.rules(eq("toutiao"), anyString()))
                .thenReturn(SelfMediaPlatformScheduleRules.defaults());
        SelfMediaPublishScheduleCreateResponse created = new SelfMediaPublishScheduleCreateResponse();
        SelfMediaPublishScheduleVO schedule = new SelfMediaPublishScheduleVO();
        schedule.setId(88L);
        created.setCreatedSchedules(List.of(schedule));
        when(scheduleService.createSystemSchedules(any(), org.mockito.ArgumentMatchers.startsWith("project-auto-33-55-retry-"), eq(99L)))
                .thenReturn(created);

        ProjectSelfMediaScheduleBatchDetailVO detail = service.retryFailedItems(7L, "2026-06");

        assertEquals("created", detail.getBatch().getStatus());
        verify(scheduleService).createSystemSchedules(any(), org.mockito.ArgumentMatchers.startsWith("project-auto-33-55-retry-"), eq(99L));
        verify(generationService, never()).retryFailedSystem(any());
        verify(batchMapper).updateById(batch);
        assertEquals(1, batch.getCreatedCount());
        assertEquals(0, batch.getRejectedCount());
    }

    @Test
    void getBatchDetailAggregatesGenerationFailureSummaries() {
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        SelfMediaAccount secondAccount = account();
        secondAccount.setId(21L);
        when(selfMediaAccountMapper.selectById(21L)).thenReturn(secondAccount);
        when(generationTaskMapper.selectById(55L)).thenReturn(failedGenerationTask(55L, "模板缺失"));
        when(generationTaskMapper.selectById(56L)).thenReturn(failedGenerationTask(56L, "模型超时"));

        ProjectSelfMediaScheduleBatchDetailVO detail = service.getBatchDetail(7L, "2026-06");

        assertEquals(1, detail.getFailureSummaries().size());
        assertEquals("GENERATION_FAILED", detail.getFailureSummaries().get(0).getCode());
        assertEquals("generation", detail.getFailureSummaries().get(0).getCategory());
        assertEquals(2, detail.getFailureSummaries().get(0).getCount());
    }

    @Test
    void compensateRetryableAbnormalSchedulesRetriesOnlySafeProjectAutoCandidates() {
        SelfMediaPublishSchedule retryable = projectAutoSchedule(88L, "LOCAL_AGENT_HEARTBEAT_TIMEOUT", 2);
        SelfMediaPublishSchedule exhausted = projectAutoSchedule(89L, "PAGE_LOAD_TIMEOUT", 3);
        SelfMediaPublishSchedule notRetryable = projectAutoSchedule(90L, "LOGIN_REQUIRED", 1);
        when(selfMediaPublishScheduleMapper.selectProjectAutoCompensationCandidates(anyList(), any(), eq(10)))
                .thenReturn(List.of(retryable, exhausted, notRetryable));

        int processed = service.compensateRetryableAbnormalSchedules(10);

        assertEquals(1, processed);
        verify(scheduleService).retryNowSystem(88L, "项目自动排期补偿任务已重新计算安全执行时间并重试");
    }

    private ProjectSelfMediaScheduleBatch processingBatchWithTwoGenerationPlans() {
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setId(33L);
        batch.setProjectId(7L);
        batch.setBrandId(8L);
        batch.setCompanyId(6L);
        batch.setTargetMonth("2026-06");
        batch.setStatus("processing");
        batch.setCreatedBy(99L);
        batch.setRequestPayload("""
                {
                  "targetMonth": "2026-06",
                  "scheduleStrategy": "platform_schedule",
                  "includeAdjustedWorkdays": false,
                  "plans": [
                    {"generationBatchId": 44, "generationTaskId": 55, "selfMediaAccountId": 20, "platform": "toutiao"},
                    {"generationBatchId": 44, "generationTaskId": 56, "selfMediaAccountId": 21, "platform": "toutiao"}
                  ]
                }
                """);
        return batch;
    }

    private SelfMediaPublishSchedule projectAutoSchedule(Long id, String failureCode, int attempts) {
        SelfMediaPublishSchedule schedule = new SelfMediaPublishSchedule();
        schedule.setId(id);
        schedule.setRequestIdempotencyKey("project-auto-33-55");
        schedule.setStatus("manual_required");
        schedule.setFailureCode(failureCode);
        schedule.setAttemptCount(attempts);
        return schedule;
    }

    private BatchArticleGenerationTask generationTask(Long taskId, Long articleId) {
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(taskId);
        task.setBatchId(44L);
        task.setStatus("success");
        task.setArticleId(articleId);
        return task;
    }

    private BatchArticleGenerationTask failedGenerationTask(Long taskId, String message) {
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(taskId);
        task.setBatchId(44L);
        task.setStatus("failed");
        task.setErrorMessage(message);
        return task;
    }

    private ArticleTemplateAllocationService.TemplateWithVersion templateWithScene(String sceneCode) {
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setQuestionSceneCode(sceneCode);
        ArticlePromptTemplateVersion version = new ArticlePromptTemplateVersion();
        return new ArticleTemplateAllocationService.TemplateWithVersion(template, version);
    }

    private BusinessCalendarService.PublishSlot slot(int day, int hour) {
        return slot(LocalDateTime.of(2026, 6, day, hour, 0));
    }

    private BusinessCalendarService.PublishSlot slot(LocalDateTime plannedAt) {
        return new BusinessCalendarService.PublishSlot(
                plannedAt.toLocalDate(),
                "上午",
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                plannedAt,
                0,
                "工作日",
                2,
                false
        );
    }

    private Clock fixedClock(LocalDateTime value) {
        ZoneId zone = ZoneId.systemDefault();
        return Clock.fixed(value.atZone(zone).toInstant(), zone);
    }

    private ProjectSelfMediaAutoScheduleRequest request() {
        ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
        request.setTargetMonth("2026-06");
        request.setArticleIds(List.of(10L));
        request.setSelfMediaAccountIds(List.of(20L));
        return request;
    }

    private ProjectSelfMediaScheduleConfig config(boolean enabled) {
        ProjectSelfMediaScheduleConfig config = new ProjectSelfMediaScheduleConfig();
        config.setProjectId(7L);
        config.setBrandId(8L);
        config.setCompanyId(6L);
        config.setAutoScheduleEnabled(enabled);
        config.setDefaultScheduleStrategy("platform_schedule");
        config.setIncludeAdjustedWorkdays(false);
        return config;
    }

    private Project project() {
        Project project = new Project();
        project.setId(7L);
        project.setBrandId(8L);
        project.setCompanyId(6L);
        project.setCreatedBy(99L);
        return project;
    }

    private SelfMediaAccount account() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(20L);
        account.setBrandId(8L);
        account.setPlatform("toutiao");
        account.setStatus("active");
        return account;
    }
}
