package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateRequest;
import com.huanjing.geo.module.content.dto.BatchArticleGenerateResponse;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplate;
import com.huanjing.geo.module.content.entity.ArticlePromptTemplateVersion;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaAutoScheduleRequest;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaScheduleItemActionRequest;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaScheduleItemUpdateRequest;
import com.huanjing.geo.module.content.entity.CompanyChannelQuotaLedger;
import com.huanjing.geo.module.content.entity.DouyinArticleImageBatch;
import com.huanjing.geo.module.content.dto.SelfMediaPublishScheduleCreateRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BatchArticleGenerationTask;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleBatch;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleCarryOver;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleConfig;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaSchedulePlan;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaScheduleItem;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleRequest;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.BatchArticleGenerationTaskMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleBatchMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleCarryOverMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleConfigMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaSchedulePlanMapper;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaScheduleItemMapper;
import com.huanjing.geo.module.content.mapper.ArticlePromptTemplateMapper;
import com.huanjing.geo.module.content.mapper.CompanyChannelQuotaLedgerMapper;
import com.huanjing.geo.module.content.mapper.DouyinArticleImageBatchMapper;
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
import com.huanjing.geo.module.system.service.SystemAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectSelfMediaScheduleServiceTest {
    private ProjectMapper projectMapper;
    private BrandMapper brandMapper;
    private ProjectSelfMediaScheduleConfigMapper configMapper;
    private ProjectSelfMediaScheduleBatchMapper batchMapper;
    private ProjectSelfMediaScheduleCarryOverMapper carryOverMapper;
    private ProjectSelfMediaSchedulePlanMapper planMapper;
    private ProjectSelfMediaScheduleItemMapper scheduleItemMapper;
    private ArticleDraftMapper articleDraftMapper;
    private SelfMediaAccountMapper selfMediaAccountMapper;
    private SelfMediaPublishScheduleMapper selfMediaPublishScheduleMapper;
    private SelfMediaPublishScheduleRequestMapper selfMediaPublishScheduleRequestMapper;
    private BatchArticleGenerationTaskMapper generationTaskMapper;
    private KeywordGroupResultMapper keywordGroupResultMapper;
    private ArticlePromptTemplateMapper promptTemplateMapper;
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
    private CurrentUserService currentUserService;
    private SystemAlertService systemAlertService;
    private CompanyChannelQuotaLedgerMapper quotaLedgerMapper;
    private DouyinArticleImageBatchMapper douyinArticleImageBatchMapper;
    private ProjectSelfMediaScheduleMetrics metrics;
    private DouyinArticleImageService douyinArticleImageService;
    private ProjectSelfMediaScheduleService service;

    @org.junit.jupiter.api.BeforeAll
    static void metadata() {
        for (Class<?> type : List.of(
                ProjectSelfMediaScheduleBatch.class,
                ProjectSelfMediaSchedulePlan.class,
                ProjectSelfMediaScheduleItem.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }

    @BeforeEach
    void setUp() {
        projectMapper = mock(ProjectMapper.class);
        brandMapper = mock(BrandMapper.class);
        configMapper = mock(ProjectSelfMediaScheduleConfigMapper.class);
        batchMapper = mock(ProjectSelfMediaScheduleBatchMapper.class);
        when(batchMapper.selectByProjectAndMonthForUpdate(any(), anyString()))
                .thenAnswer(invocation -> batchMapper.selectByProjectAndMonth(
                        invocation.getArgument(0), invocation.getArgument(1)));
        carryOverMapper = mock(ProjectSelfMediaScheduleCarryOverMapper.class);
        planMapper = mock(ProjectSelfMediaSchedulePlanMapper.class);
        scheduleItemMapper = mock(ProjectSelfMediaScheduleItemMapper.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
        selfMediaAccountMapper = mock(SelfMediaAccountMapper.class);
        selfMediaPublishScheduleMapper = mock(SelfMediaPublishScheduleMapper.class);
        selfMediaPublishScheduleRequestMapper = mock(SelfMediaPublishScheduleRequestMapper.class);
        generationTaskMapper = mock(BatchArticleGenerationTaskMapper.class);
        keywordGroupResultMapper = mock(KeywordGroupResultMapper.class);
        promptTemplateMapper = mock(ArticlePromptTemplateMapper.class);
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
        currentUserService = mock(CurrentUserService.class);
        systemAlertService = mock(SystemAlertService.class);
        quotaLedgerMapper = mock(CompanyChannelQuotaLedgerMapper.class);
        douyinArticleImageBatchMapper = mock(DouyinArticleImageBatchMapper.class);
        metrics = new ProjectSelfMediaScheduleMetrics(new SimpleMeterRegistry());
        java.util.concurrent.atomic.AtomicLong planItemIds = new java.util.concurrent.atomic.AtomicLong(1000L);
        doAnswer(invocation -> {
            ProjectSelfMediaScheduleItem item = invocation.getArgument(0);
            if (item.getId() == null) {
                item.setId(planItemIds.incrementAndGet());
            }
            return 1;
        }).when(scheduleItemMapper).insert(any(ProjectSelfMediaScheduleItem.class));
        when(scheduleItemMapper.claimDueGeneration(any(), anyString(), any(), any())).thenReturn(1);
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
        when(scheduleAdapterRouter.contract(anyString())).thenReturn(Optional.empty());

        douyinArticleImageService = mock(DouyinArticleImageService.class);
        org.mockito.Mockito.lenient().when(douyinArticleImageService.prepareAutomatic(any(), any())).thenReturn(true);
        service = new ProjectSelfMediaScheduleService(
                projectMapper,
                brandMapper,
                configMapper,
                batchMapper,
                carryOverMapper,
                planMapper,
                scheduleItemMapper,
                articleDraftMapper,
                selfMediaAccountMapper,
                selfMediaPublishScheduleMapper,
                selfMediaPublishScheduleRequestMapper,
                generationTaskMapper,
                keywordGroupResultMapper,
                promptTemplateMapper,
                autoScheduleService,
                scheduleService,
                generationService,
                douyinArticleImageService,
                perspectiveService,
                templateAllocationService,
                coverSelectionService,
                businessCalendarService,
                companyChannelQuotaService,
                brandAccessService,
                currentUserService,
                systemAlertService,
                localAgentSessionMapper,
                new ObjectMapper().findAndRegisterModules(),
                scheduleAdapterRouter,
                quotaLedgerMapper,
                douyinArticleImageBatchMapper,
                metrics
        );
        service.setClock(Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneId.systemDefault()));
    }

    @Test
    void updatePlanItemRejectsDateOutsideBusinessCalendar() {
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setId(301L);
        item.setBatchId(11L);
        item.setSelfMediaAccountId(41L);
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setId(11L);
        SelfMediaAccount account = account(41L, "active");
        account.setPlatform("zhihu");
        when(scheduleItemMapper.selectById(301L)).thenReturn(item);
        when(batchMapper.selectById(11L)).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(41L)).thenReturn(account);
        ProjectSelfMediaScheduleItemUpdateRequest request = new ProjectSelfMediaScheduleItemUpdateRequest();
        request.setSelfMediaAccountId(41L);
        request.setPlannedExecutionAt(LocalDateTime.of(2026, 6, 10, 9, 0));
        request.setPlannedPublishAt(LocalDateTime.of(2026, 6, 10, 10, 0));

        assertThrows(BizException.class, () -> service.updatePlanItem(7L, 301L, request));
        verify(scheduleItemMapper, never()).updateById(item);
    }

    @Test
    void updatePlanItemAllowsAdditionalArticlesOnSameDay() {
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setId(302L);
        item.setBatchId(11L);
        item.setSelfMediaAccountId(41L);
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setId(11L);
        SelfMediaAccount account = account(41L, "active");
        account.setPlatform("zhihu");
        when(scheduleItemMapper.selectById(302L)).thenReturn(item);
        when(batchMapper.selectById(11L)).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(41L)).thenReturn(account);
        when(scheduleItemMapper.countActivePlanItemsForAccountDay(eq(41L), eq(302L), any(), any()))
                .thenReturn(3L);
        ProjectSelfMediaScheduleItemUpdateRequest request = new ProjectSelfMediaScheduleItemUpdateRequest();
        request.setSelfMediaAccountId(41L);
        request.setPlannedExecutionAt(LocalDateTime.of(2026, 6, 11, 9, 0));
        request.setPlannedPublishAt(LocalDateTime.of(2026, 6, 11, 10, 0));

        service.updatePlanItem(7L, 302L, request);

        assertEquals(request.getPlannedExecutionAt(), item.getPlannedExecutionAt());
        assertEquals(request.getPlannedPublishAt(), item.getPlannedPublishAt());
        verify(scheduleItemMapper).updateById(item);
    }

    @Test
    void updatePlanItemStillRejectsOccupiedExecutionTime() {
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setId(302L);
        item.setBatchId(11L);
        item.setSelfMediaAccountId(41L);
        SelfMediaAccount account = account(41L, "active");
        account.setPlatform("zhihu");
        when(scheduleItemMapper.selectById(302L)).thenReturn(item);
        when(batchMapper.selectById(11L)).thenReturn(storedBatch());
        when(selfMediaAccountMapper.selectById(41L)).thenReturn(account);
        when(scheduleItemMapper.countActivePlanItemsAtExecution(eq(41L), eq(302L), any())).thenReturn(1L);
        ProjectSelfMediaScheduleItemUpdateRequest request = new ProjectSelfMediaScheduleItemUpdateRequest();
        request.setSelfMediaAccountId(41L);
        request.setPlannedExecutionAt(LocalDateTime.of(2026, 6, 11, 9, 0));
        request.setPlannedPublishAt(LocalDateTime.of(2026, 6, 11, 10, 0));

        assertThrows(BizException.class, () -> service.updatePlanItem(7L, 302L, request));
        verify(scheduleItemMapper, never()).updateById(item);
    }

    @ParameterizedTest
    @ValueSource(strings = {"planned", "processing"})
    void createForProjectStillRejectsDuplicateCreationOrSupplementWhileProcessing(String status) {
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setStatus(status);
        batch.setRequestPayload(null);
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setSupplementExistingBatch("processing".equals(status));

        assertThrows(BizException.class, () -> service.createForProject(7L, request, "manual", "new-key"));
        verify(scheduleItemMapper, never()).insert(any(ProjectSelfMediaScheduleItem.class));
        verify(companyChannelQuotaService, never()).reserveProjectSelfMediaPlan(any(), any(), any(), any(), any());
    }

    @Test
    void supplementPlannedBatchStillRejectsExhaustedQuota() {
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setStatus("planned");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 2, 2));
        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setSupplementExistingBatch(true);

        assertThrows(BizException.class, () -> service.createForProject(7L, request, "manual", "new-key"));
        verify(scheduleItemMapper, never()).insert(any(ProjectSelfMediaScheduleItem.class));
        verify(batchMapper, never()).updateById(batch);
    }

    @Test
    void reconcilePlanQuotaReservationsRefundsTerminalAndOrphanCandidates() {
        CompanyChannelQuotaLedger terminal = new CompanyChannelQuotaLedger();
        terminal.setId(1L);
        terminal.setBizId("401");
        CompanyChannelQuotaLedger orphan = new CompanyChannelQuotaLedger();
        orphan.setId(2L);
        orphan.setBizId("999");
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setId(401L);
        item.setStatus("generation_failed");
        item.setQuotaReservationStatus("reserved");
        when(quotaLedgerMapper.selectProjectPlanReconciliationCandidates(any(), eq(0L), eq(10)))
                .thenReturn(List.of(terminal, orphan));
        when(scheduleItemMapper.selectById(401L)).thenReturn(item);

        assertEquals(2, service.reconcilePlanQuotaReservations(10, 72));
        verify(companyChannelQuotaService).refundProjectSelfMediaPlan(401L);
        verify(companyChannelQuotaService).refundProjectSelfMediaPlan(999L);
        assertEquals("refunded", item.getQuotaReservationStatus());
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
    void createForProjectRejectsWithCapacityCodeBeforeGenerating() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 21, 9, 0)));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(null);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 2));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(LocalDateTime.of(2026, 6, 21, 9, 10))));
        when(scheduleAdapterRouter.rules("toutiao", "platform_schedule"))
                .thenReturn(new SelfMediaPlatformScheduleRules(10, 120, 4, 7 * 24 * 60));

        BizException ex = assertThrows(BizException.class, () -> service.createForProject(7L, request(), "manual"));

        assertEquals(400, ex.getHttpStatus());
        assertTrue(String.valueOf(ex.getData()).contains("SELF_MEDIA_SCHEDULE_CAPACITY_INSUFFICIENT"));
        verify(generationService, never()).createSystemBatch(any(), any());
    }

    @Test
    void createForProjectCarriesOverDeficitAfterLeadDecision() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 21, 9, 0)));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(null);
        doAnswer(invocation -> {
            ProjectSelfMediaScheduleBatch batch = invocation.getArgument(0);
            batch.setId(66L);
            return 1;
        }).when(batchMapper).insert(any(ProjectSelfMediaScheduleBatch.class));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 2));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(LocalDateTime.of(2026, 6, 21, 12, 0))));
        when(scheduleAdapterRouter.rules("toutiao", "platform_schedule"))
                .thenReturn(new SelfMediaPlatformScheduleRules(10, 120, 4, 7 * 24 * 60));
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
        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setDecisionStrategy("carry_over");
        request.setDecisionReason("容量不足，结转补排");

        var response = service.createForProject(7L, request, "manual");

        assertEquals(1, response.getPlannedCount());
        assertEquals(1, response.getCarryOverCount());
        assertEquals("2026-07", response.getCarryOverTargetMonth());
        verify(generationService, never()).createSystemBatch(any(), any());
        ArgumentCaptor<ProjectSelfMediaScheduleBatch> batchCaptor =
                ArgumentCaptor.forClass(ProjectSelfMediaScheduleBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        assertEquals(2, batchCaptor.getValue().getRequestedCount());
        assertEquals(1, batchCaptor.getValue().getDeficitCount());
        assertEquals(1, batchCaptor.getValue().getCarryOverCount());
        assertEquals(99L, batchCaptor.getValue().getDecisionOperatorId());
        assertEquals("容量不足，结转补排", batchCaptor.getValue().getDecisionReason());
        assertTrue(batchCaptor.getValue().getCapacitySnapshotJson().contains("\"deficitCount\":1"));
        ArgumentCaptor<ProjectSelfMediaScheduleCarryOver> captor =
                ArgumentCaptor.forClass(ProjectSelfMediaScheduleCarryOver.class);
        verify(carryOverMapper).insert(captor.capture());
        assertEquals(66L, captor.getValue().getSourceBatchId());
        assertEquals("2026-06", captor.getValue().getSourceMonth());
        assertEquals("2026-07", captor.getValue().getTargetMonth());
        assertEquals(1, captor.getValue().getCarryOverCount());
    }

    @Test
    void previewForProjectSplitsNormalAndPendingCarryOverDemand() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 7, 1, 9, 0)));
        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setTargetMonth("2026-07");
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-07", 0, 2));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 7)), eq(false)))
                .thenReturn(List.of(
                        slot(LocalDateTime.of(2026, 7, 8, 12, 0)),
                        slot(LocalDateTime.of(2026, 7, 9, 12, 0)),
                        slot(LocalDateTime.of(2026, 7, 10, 12, 0))
                ));
        ProjectSelfMediaScheduleCarryOver pending = carryOver(88L, "2026-06", "2026-07", 1);
        when(carryOverMapper.selectList(any())).thenReturn(List.of(pending));

        var response = service.previewForProject(7L, request);

        assertEquals(3, response.getRequestedCount());
        assertEquals(2, response.getNormalRequiredCount());
        assertEquals(1, response.getPendingCarryOverCount());
        assertEquals(1, response.getCarryOverSources().size());
        assertEquals("2026-06", response.getCarryOverSources().get(0).getSourceMonth());
        assertEquals(1, response.getCarryOverSources().get(0).getPendingCount());
    }

    @Test
    void createForProjectPrioritizesPendingCarryOverWhenTargetMonthCapacityIsTight() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 7, 1, 9, 0)));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(batchMapper.selectByProjectAndMonth(7L, "2026-07")).thenReturn(null);
        doAnswer(invocation -> {
            ProjectSelfMediaScheduleBatch batch = invocation.getArgument(0);
            batch.setId(77L);
            return 1;
        }).when(batchMapper).insert(any(ProjectSelfMediaScheduleBatch.class));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-07", 0, 1));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 7)), eq(false)))
                .thenReturn(List.of(slot(LocalDateTime.of(2026, 7, 8, 12, 0))));
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
        ProjectSelfMediaScheduleCarryOver pending = carryOver(88L, "2026-06", "2026-07", 1);
        when(carryOverMapper.selectList(any()))
                .thenReturn(List.of(pending))
                .thenReturn(List.of(pending))
                .thenReturn(List.of());
        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setTargetMonth("2026-07");
        request.setDecisionStrategy("carry_over");
        request.setDecisionReason("优先补历史结转");

        var response = service.createForProject(7L, request, "manual");

        assertEquals(1, response.getPlannedCount());
        assertEquals(1, response.getCarryOverCount());
        assertEquals("2026-08", response.getCarryOverTargetMonth());
        ArgumentCaptor<ProjectSelfMediaScheduleCarryOver> updateCaptor =
                ArgumentCaptor.forClass(ProjectSelfMediaScheduleCarryOver.class);
        verify(carryOverMapper).updateById(updateCaptor.capture());
        assertEquals(88L, updateCaptor.getValue().getId());
        assertEquals("consumed", updateCaptor.getValue().getStatus());
        ArgumentCaptor<ProjectSelfMediaScheduleCarryOver> insertCaptor =
                ArgumentCaptor.forClass(ProjectSelfMediaScheduleCarryOver.class);
        verify(carryOverMapper).insert(insertCaptor.capture());
        assertEquals("2026-07", insertCaptor.getValue().getSourceMonth());
        assertEquals("2026-08", insertCaptor.getValue().getTargetMonth());
    }

    @Test
    void createForProjectReusesPendingCarryOverWhenNoCurrentMonthSlotExists() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 21, 9, 0)));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(null);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 2));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of());
        ProjectSelfMediaScheduleCarryOver existed = carryOver(99L, "2026-06", "2026-07", 2);
        when(carryOverMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of(existed));
        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setDecisionStrategy("carry_over");
        request.setDecisionReason("无可排槽位，结转补排");

        var first = service.createForProject(7L, request, "manual");
        var second = service.createForProject(7L, request, "manual");

        assertEquals(2, first.getCarryOverCount());
        assertEquals(2, second.getCarryOverCount());
        assertEquals("2026-07", second.getCarryOverTargetMonth());
        verify(carryOverMapper, times(1)).insert(any(ProjectSelfMediaScheduleCarryOver.class));
        verify(generationService, never()).createSystemBatch(any(), any());
    }

    @Test
    void createForProjectRejectsCarryOverWithoutLeadPermission() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 21, 9, 0)));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(null);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 2));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(LocalDateTime.of(2026, 6, 21, 12, 0))));
        doThrow(new BizException(403, "No permission: content.self_media_schedule.late_start_decide"))
                .when(currentUserService).ensurePermission("content.self_media_schedule.late_start_decide");
        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setDecisionStrategy("carry_over");
        request.setDecisionReason("容量不足，结转补排");

        BizException ex = assertThrows(BizException.class, () -> service.createForProject(7L, request, "manual"));

        assertEquals(403, ex.getCode());
        verify(carryOverMapper, never()).insert(any(ProjectSelfMediaScheduleCarryOver.class));
        verify(generationService, never()).createSystemBatch(any(), any());
    }

    @Test
    void createForProjectRejectsCarryOverWithoutDecisionReason() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 21, 9, 0)));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(null);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 2));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(LocalDateTime.of(2026, 6, 21, 12, 0))));
        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setDecisionStrategy("carry_over");

        BizException ex = assertThrows(BizException.class, () -> service.createForProject(7L, request, "manual"));

        assertEquals(400, ex.getHttpStatus());
        assertTrue(String.valueOf(ex.getData()).contains("SELF_MEDIA_SCHEDULE_DECISION_REASON_REQUIRED"));
        verify(carryOverMapper, never()).insert(any(ProjectSelfMediaScheduleCarryOver.class));
    }

    @Test
    void createForProjectRejectsCompressedCurrentMonthWithStableCode() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 21, 9, 0)));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(null);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 2));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(LocalDateTime.of(2026, 6, 21, 12, 0))));
        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setDecisionStrategy("compressed_current_month");

        BizException ex = assertThrows(BizException.class, () -> service.createForProject(7L, request, "manual"));

        assertEquals(400, ex.getHttpStatus());
        assertTrue(String.valueOf(ex.getData()).contains("SELF_MEDIA_SCHEDULE_COMPRESS_NOT_SUPPORTED"));
    }

    @Test
    void createForProjectConsumesCarryOverWithFallbackAccountOnSamePlatform() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 7, 1, 9, 0)));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(batchMapper.selectByProjectAndMonth(7L, "2026-07")).thenReturn(null);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(null);
        when(selfMediaAccountMapper.selectById(21L)).thenReturn(account(21L, "active"));
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-07", 0, 0));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 7)), eq(false)))
                .thenReturn(List.of(slot(LocalDateTime.of(2026, 7, 8, 12, 0))));
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
        ProjectSelfMediaScheduleCarryOver pending = carryOver(88L, "2026-06", "2026-07", 1);
        when(carryOverMapper.selectList(any()))
                .thenReturn(List.of(pending))
                .thenReturn(List.of(pending));
        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setTargetMonth("2026-07");
        request.setSelfMediaAccountIds(List.of(21L));
        request.setDecisionStrategy("carry_over");
        request.setDecisionReason("混合可用状态保持历史结转 pending");

        var response = service.createForProject(7L, request, "manual");

        assertEquals(1, response.getPlannedCount());
        verify(generationService, never()).createSystemBatch(any(), any());
        ArgumentCaptor<ProjectSelfMediaScheduleBatch> batchCaptor =
                ArgumentCaptor.forClass(ProjectSelfMediaScheduleBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        assertTrue(batchCaptor.getValue().getRequestPayload().contains("\"selfMediaAccountId\":21"),
                batchCaptor.getValue().getRequestPayload());
        verify(carryOverMapper).updateById(any(ProjectSelfMediaScheduleCarryOver.class));
    }

    @Test
    void createForProjectDoesNotConsumeMixedAvailableUnavailableCarryOverRow() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 7, 1, 9, 0)));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(batchMapper.selectByProjectAndMonth(7L, "2026-07")).thenReturn(null);
        doAnswer(invocation -> {
            ProjectSelfMediaScheduleBatch batch = invocation.getArgument(0);
            batch.setId(77L);
            return 1;
        }).when(batchMapper).insert(any(ProjectSelfMediaScheduleBatch.class));
        when(selfMediaAccountMapper.selectById(21L)).thenReturn(account(21L, "active"));
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-07", 0, 1));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 7)), eq(false)))
                .thenReturn(List.of(slot(LocalDateTime.of(2026, 7, 8, 12, 0))));
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
        ProjectSelfMediaScheduleCarryOver pending = carryOver(88L, "2026-06", "2026-07", 2);
        pending.setCarryOverPlanJson("""
                [
                  {"accountId":21,"platform":"toutiao"},
                  {"accountId":999,"platform":"xhs"}
                ]
                """);
        when(carryOverMapper.selectList(any())).thenReturn(List.of(pending));
        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setTargetMonth("2026-07");
        request.setSelfMediaAccountIds(List.of(21L));
        request.setDecisionStrategy("carry_over");
        request.setDecisionReason("混合可用状态保持历史结转 pending");

        var response = service.createForProject(7L, request, "manual");

        assertEquals(1, response.getPlannedCount());
        verify(generationService, never()).createSystemBatch(any(), any());
        verify(carryOverMapper, never()).updateById(any(ProjectSelfMediaScheduleCarryOver.class));
    }

    @Test
    void previewKeepsCarryOverPendingWhenPlatformHasNoAvailableAccount() {
        ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
        request.setTargetMonth("2026-07");
        request.setSelfMediaAccountIds(List.of(20L));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(null);
        ProjectSelfMediaScheduleCarryOver pending = carryOver(88L, "2026-06", "2026-07", 1);
        when(carryOverMapper.selectList(any())).thenReturn(List.of(pending));

        var response = service.previewForProject(7L, request);

        assertEquals(0, response.getPlannedCount());
        assertEquals(1, response.getUnavailableCarryOverCount());
        assertTrue(response.getWarnings().stream().anyMatch(message -> message.contains("历史结转")));
        verify(carryOverMapper, never()).updateById(any(ProjectSelfMediaScheduleCarryOver.class));
    }

    @Test
    void getBatchReturnsAuditFields() {
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setId(33L);
        batch.setProjectId(7L);
        batch.setBrandId(8L);
        batch.setCompanyId(6L);
        batch.setTargetMonth("2026-06");
        batch.setStatus("processing");
        batch.setRequestedCount(2);
        batch.setDeficitCount(1);
        batch.setCarryOverCount(1);
        batch.setDecisionOperatorId(99L);
        batch.setDecisionReason("容量不足，结转补排");
        batch.setCapacitySnapshotJson("{\"deficitCount\":1}");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);

        var response = service.getBatch(7L, "2026-06");

        assertEquals(2, response.getRequestedCount());
        assertEquals(1, response.getDeficitCount());
        assertEquals(1, response.getCarryOverCount());
        assertEquals(99L, response.getDecisionOperatorId());
        assertEquals("容量不足，结转补排", response.getDecisionReason());
        assertEquals("{\"deficitCount\":1}", response.getCapacitySnapshotJson());
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
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
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
        assertEquals("planned", updated.getStatus());
        assertEquals(1, updated.getPlannedCount());
        assertEquals(0, updated.getCreatedCount());
        assertEquals(0, updated.getRejectedCount());
    }

    @ParameterizedTest
    @CsvSource({"created,toutiao", "planned,toutiao", "planned,sohu"})
    void createForProjectSupplementsExistingBatchWithoutChangingOriginalPlans(String status, String platform) throws Exception {
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setId(33L);
        batch.setProjectId(7L);
        batch.setBrandId(8L);
        batch.setCompanyId(6L);
        batch.setTargetMonth("2026-06");
        batch.setStatus(status);
        batch.setPlannedCount(1);
        batch.setCreatedCount(1);
        batch.setRequestedCount(1);
        batch.setCreatedBy(99L);
        batch.setRequestPayload("""
                {
                  "targetMonth": "2026-06",
                  "scheduleStrategy": "platform_schedule",
                  "includeAdjustedWorkdays": false,
                  "plans": [
                    {"generationBatchId": 44, "generationTaskId": 55, "selfMediaAccountId": 20, "platform": "toutiao",
                     "plannedExecutionAt": "2026-06-11T09:00:00", "plannedPublishAt": "2026-06-11T10:00:00"}
                  ]
                }
                """);
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        SelfMediaAccount newAccount = account(21L, "active");
        newAccount.setPlatform(platform);
        when(selfMediaAccountMapper.selectById(21L)).thenReturn(newAccount);
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq(platform), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:" + platform, "month", "2026-06", 0, 1));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(21, 10)));
        when(scheduleAdapterRouter.rules("toutiao", "platform_schedule"))
                .thenReturn(SelfMediaPlatformScheduleRules.defaults());
        KeywordGroupResult question = new KeywordGroupResult();
        question.setKeywordText("测试问题");
        question.setSceneCode("news_brief");
        when(keywordGroupResultMapper.selectProjectQuestionsByTiers(7L, "'A'")).thenReturn(List.of(question));
        BatchArticleGenerateResponse generation = new BatchArticleGenerateResponse(45L, 1, "pending", false, false, List.of());
        when(generationService.createSystemBatch(any(), eq(99L))).thenReturn(generation);
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(56L);
        task.setBatchId(45L);
        when(generationTaskMapper.selectList(any())).thenReturn(List.of(task));

        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setArticleIds(List.of());
        request.setSelfMediaAccountIds(List.of(21L));
        request.setSupplementExistingBatch(true);
        request.setScheduleStrategy(SelfMediaPublishAutoScheduleService.STRATEGY_PLATFORM_SPECIFIC);
        if ("sohu".equals(platform)) {
            var adapter = new com.huanjing.geo.module.content.service.adapter.SohuPlatformScheduleAdapter();
            when(scheduleAdapterRouter.contract("sohu")).thenReturn(Optional.of(adapter.capabilityContract()));
            when(scheduleAdapterRouter.rules("sohu", "backend_delayed_publish")).thenReturn(adapter.scheduleRules("backend_delayed_publish"));
        }
        var response = service.createForProject(7L, request, "manual", "supplement-key");

        assertTrue(response.getCreated());
        assertEquals(1, response.getPlannedCount());
        ArgumentCaptor<ProjectSelfMediaScheduleBatch> captor = ArgumentCaptor.forClass(ProjectSelfMediaScheduleBatch.class);
        verify(batchMapper).updateById(captor.capture());
        ProjectSelfMediaScheduleBatch updated = captor.getValue();
        assertEquals("planned", updated.getStatus());
        assertEquals(2, updated.getPlannedCount());
        assertTrue(updated.getRequestPayload().contains("\"generationTaskId\":55"));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var originalPlan = mapper.readTree(updated.getRequestPayload()).get("plans").get(0);
        assertEquals(LocalDateTime.of(2026, 6, 11, 9, 0),
                mapper.convertValue(originalPlan.get("plannedExecutionAt"), LocalDateTime.class));
        assertEquals(LocalDateTime.of(2026, 6, 11, 10, 0),
                mapper.convertValue(originalPlan.get("plannedPublishAt"), LocalDateTime.class));
        verify(scheduleItemMapper, never()).updateById(org.mockito.ArgumentMatchers.argThat(
                (ProjectSelfMediaScheduleItem row) -> Long.valueOf(20L).equals(row.getSelfMediaAccountId())));
        assertTrue(updated.getRequestPayload().contains("\"generationTaskId\":null"));
        assertTrue(updated.getRequestPayload().contains("\"selfMediaAccountId\":21"));
        var newPlan = mapper.readTree(updated.getRequestPayload()).get("plans").get(1);
        assertEquals(platform, newPlan.get("platform").asText());
        if ("sohu".equals(platform)) {
            assertEquals(newPlan.get("plannedExecutionAt"), newPlan.get("plannedPublishAt"));
        }
        assertTrue(updated.getRequestPayload().contains("\"idempotencyKey\":\"supplement-key\""));
        var retry = service.createForProject(7L, request, "manual", "supplement-key");
        assertEquals(2, retry.getPlannedCount());
        verify(batchMapper, times(1)).updateById(batch);
        verify(scheduleItemMapper, times(1)).insert(any(ProjectSelfMediaScheduleItem.class));
        verify(companyChannelQuotaService, times(1)).reserveProjectSelfMediaPlan(
                eq(6L), eq(7L), eq(platform), any(), any());
    }

    @Test
    void createForProjectDefersReviewRecommendationTopicUntilSubjectIsSelected() {
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
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
        BatchArticleGenerateResponse generation = new BatchArticleGenerateResponse(44L, 1, "pending", false, false, List.of());
        when(generationService.createSystemBatch(any(), eq(99L))).thenReturn(generation);
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(55L);
        task.setBatchId(44L);
        when(generationTaskMapper.selectList(any())).thenReturn(List.of(task));

        service.createForProject(7L, request(), "manual");

        verify(generationService, never()).createSystemBatch(any(), any());
        ArgumentCaptor<ProjectSelfMediaScheduleBatch> batchCaptor =
                ArgumentCaptor.forClass(ProjectSelfMediaScheduleBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        ProjectSelfMediaScheduleBatch plannedBatch = batchCaptor.getValue();
        plannedBatch.setId(33L);
        when(batchMapper.selectProcessing(5)).thenReturn(List.of(plannedBatch));
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 11, 5, 0)));
        service.progressProcessingBatches(5);

        ArgumentCaptor<BatchArticleGenerateRequest> captor = ArgumentCaptor.forClass(BatchArticleGenerateRequest.class);
        verify(generationService).createSystemBatch(captor.capture(), eq(99L));
        assertNull(captor.getValue().getTopics().get(0).getQuestionSceneCode());
        assertEquals("第三方品牌评测推荐", captor.getValue().getTopics().get(0).getTopic());
    }

    @Test
    void createForProjectDoesNotRequireSourceQuestionsForThirdPartyPerspective() {
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 1));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(keywordGroupResultMapper.selectProjectQuestionsByTiers(7L, "'A'")).thenReturn(List.of());
        when(perspectiveService.resolve(8L, "self_media", "toutiao"))
                .thenReturn(new TemplatePerspectiveService.ResolvedPerspective("review_recommend", "channel", 99L));
        BatchArticleGenerateResponse generation = new BatchArticleGenerateResponse(44L, 1, "pending", false, false, List.of());
        when(generationService.createSystemBatch(any(), eq(99L))).thenReturn(generation);
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(55L);
        task.setBatchId(44L);
        when(generationTaskMapper.selectList(any())).thenReturn(List.of(task));

        service.createForProject(7L, request(), "manual");

        verify(generationService, never()).createSystemBatch(any(), any());
        ArgumentCaptor<ProjectSelfMediaScheduleBatch> batchCaptor =
                ArgumentCaptor.forClass(ProjectSelfMediaScheduleBatch.class);
        verify(batchMapper).insert(batchCaptor.capture());
        ProjectSelfMediaScheduleBatch plannedBatch = batchCaptor.getValue();
        plannedBatch.setId(33L);
        when(batchMapper.selectProcessing(5)).thenReturn(List.of(plannedBatch));
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 11, 5, 0)));
        service.progressProcessingBatches(5);

        ArgumentCaptor<BatchArticleGenerateRequest> captor = ArgumentCaptor.forClass(BatchArticleGenerateRequest.class);
        verify(generationService).createSystemBatch(captor.capture(), eq(99L));
        assertNull(captor.getValue().getTopics().get(0).getQuestionSceneCode());
        assertEquals("第三方品牌评测推荐", captor.getValue().getTopics().get(0).getTopic());
    }

    @Test
    void createDueEnabledProjectsContinuesAfterSingleProjectFailure() {
        ProjectSelfMediaScheduleConfig first = config(true);
        first.setProjectId(1L);
        ProjectSelfMediaScheduleConfig second = config(true);
        second.setProjectId(2L);
        when(configMapper.selectEnabledPage(0L, 2)).thenReturn(List.of(first, second));
        when(configMapper.selectEnabledPage(2L, 2)).thenReturn(List.of());
        Project firstProject = project();
        firstProject.setId(1L);
        Project secondProject = project();
        secondProject.setId(2L);
        when(projectMapper.selectById(1L)).thenReturn(firstProject);
        when(projectMapper.selectById(2L)).thenReturn(secondProject);
        when(batchMapper.selectByProjectAndMonth(1L, "2026-06")).thenThrow(new RuntimeException("broken project"));
        ProjectSelfMediaScheduleBatch existing = new ProjectSelfMediaScheduleBatch();
        existing.setId(22L);
        when(batchMapper.selectByProjectAndMonth(2L, "2026-06")).thenReturn(existing);

        assertEquals(0, service.createDueEnabledProjects("2026-06", 2));
        verify(batchMapper).selectByProjectAndMonth(1L, "2026-06");
        verify(batchMapper).selectByProjectAndMonth(2L, "2026-06");
    }

    @Test
    void createDueEnabledProjectsIgnoresInactiveProjectWithoutCreatingBatch() {
        ProjectSelfMediaScheduleConfig config = config(true);
        when(configMapper.selectEnabledPage(0L, 2)).thenReturn(List.of(config));
        when(configMapper.selectEnabledPage(7L, 2)).thenReturn(List.of());
        Project paused = project();
        paused.setStatus("paused");
        when(projectMapper.selectById(7L)).thenReturn(paused);

        assertEquals(0, service.createDueEnabledProjects("2026-06", 2));

        verify(batchMapper, never()).insert(any());
        verify(batchMapper, never()).selectByProjectAndMonth(7L, "2026-06");
        verify(generationService, never()).createSystemBatch(any(), any());
    }

    @Test
    void createDueEnabledProjectPlansIgnoresInactiveProjectWithoutCreatingPlan() {
        ProjectSelfMediaScheduleConfig config = config(true);
        when(configMapper.selectEnabledPage(0L, 2)).thenReturn(List.of(config));
        when(configMapper.selectEnabledPage(7L, 2)).thenReturn(List.of());
        Project paused = project();
        paused.setStatus("paused");
        when(projectMapper.selectById(7L)).thenReturn(paused);

        int created = service.createDueEnabledProjectPlans(
                "2026-06", "job", LocalDateTime.of(2026, 6, 1, 1, 0), 2, 0, 2);

        assertEquals(0, created);
        verify(planMapper, never()).insert(any());
        verify(batchMapper, never()).insert(any());
    }

    @Test
    void queuedPlanForInactiveProjectIsSilentlySkippedBeforeCreatingBatch() {
        ProjectSelfMediaSchedulePlan plan = new ProjectSelfMediaSchedulePlan();
        plan.setId(77L);
        plan.setProjectId(7L);
        plan.setTargetMonth("2026-06");
        plan.setStatus("pending");
        plan.setRetryCount(0);
        plan.setCreatedBy(99L);
        when(planMapper.selectList(any())).thenReturn(List.of(), List.of(plan));
        when(planMapper.update(eq(null), any())).thenReturn(1);
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        Project paused = project();
        paused.setStatus("paused");
        when(projectMapper.selectById(7L)).thenReturn(paused);

        assertEquals(1, service.processDueProjectSchedulePlans(5, 3, 120));

        verify(batchMapper, never()).selectByProjectAndMonth(any(), anyString());
        verify(batchMapper, never()).insert(any());
        verify(generationService, never()).createSystemBatch(any(), any());
    }

    @Test
    void recoverStaleRunningProjectSchedulePlansReleasesRetryableRows() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 21, 12, 0)));
        ProjectSelfMediaSchedulePlan plan = new ProjectSelfMediaSchedulePlan();
        plan.setId(77L);
        plan.setStatus("running");
        plan.setRetryCount(1);
        plan.setUpdatedAt(LocalDateTime.of(2026, 6, 21, 9, 0));
        when(planMapper.selectList(any())).thenReturn(List.of(plan));
        when(planMapper.update(eq(null), any())).thenReturn(1);

        int recovered = service.recoverStaleRunningProjectSchedulePlans(3, 120);

        assertEquals(1, recovered);
        verify(planMapper).update(eq(null), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sohu", "toutiao"})
    void dailyQuotaExpandsAcrossEveryRemainingBusinessDay(String platform) {
        prepareDailyQuota(platform, 2, List.of(slot(11, 10), slot(11, 15), slot(12, 10), slot(12, 15)));

        var response = service.previewForProject(7L, dailyQuotaRequest());

        assertEquals(4, response.getRequestedCount());
        assertTrue(response.getEnough());
        var selected = response.getSlotGroups().get(0).getSelectedSlots();
        assertEquals(4, selected.size());
        assertEquals(2, selected.stream().filter(slot -> slot.getExecutionAt().toLocalDate()
                .equals(LocalDate.of(2026, 6, 11))).count());
        assertEquals(2, selected.stream().filter(slot -> slot.getExecutionAt().toLocalDate()
                .equals(LocalDate.of(2026, 6, 12))).count());
    }

    @Test
    void dailyQuotaDeductsUsagePerDateInsteadOfMonthlyTotal() {
        prepareDailyQuota("sohu", 2, List.of(slot(11, 10), slot(11, 15), slot(12, 10), slot(12, 15)));
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("sohu"), any()))
                .thenAnswer(invocation -> {
                    LocalDate date = invocation.getArgument(2);
                    return new CompanyChannelQuotaService.DistributionQuotaView(
                            "self_media:sohu", "day", date.toString(), date.getDayOfMonth() == 11 ? 2 : 0, 2);
                });
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("sohu"), eq(LocalDateTime.of(2026, 6, 1, 0, 0)), any(), anyList())).thenReturn(8L);

        var response = service.previewForProject(7L, dailyQuotaRequest());

        assertEquals(2, response.getRequestedCount());
        assertTrue(response.getSlotGroups().get(0).getSelectedSlots().stream()
                .allMatch(slot -> slot.getExecutionAt().toLocalDate().equals(LocalDate.of(2026, 6, 12))));
    }

    @Test
    void dailyQuotaCanUseMoreThanTwoTimesInsideBusinessWindows() {
        prepareDailyQuota("sohu", 5, List.of(slot(11, 10), slot(11, 15), slot(12, 10), slot(12, 15)));

        var response = service.previewForProject(7L, dailyQuotaRequest());

        assertEquals(10, response.getRequestedCount());
        assertTrue(response.getEnough());
        assertEquals(10, response.getSlotGroups().get(0).getSelectedSlots().stream()
                .map(slot -> slot.getExecutionAt()).distinct().count());
    }

    @Test
    void supplementDailyQuotaPreservesOriginalPlansAndConsumesOnlyMissingDailyQuota() throws Exception {
        prepareDailyQuota("sohu", 2, List.of(slot(11, 10), slot(11, 15), slot(12, 10), slot(12, 15)));
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setId(33L);
        batch.setStatus("planned");
        batch.setPlannedCount(2);
        batch.setRequestedCount(2);
        batch.setRejectedCount(2);
        batch.setRequestPayload("""
                {"targetMonth":"2026-06","scheduleStrategy":"platform_specific","includeAdjustedWorkdays":false,
                 "plans":[
                   {"selfMediaAccountId":20,"platform":"sohu","plannedExecutionAt":"2026-06-11T10:00:00","plannedPublishAt":"2026-06-11T10:00:00"},
                   {"selfMediaAccountId":20,"platform":"sohu","plannedExecutionAt":"2026-06-12T10:00:00","plannedPublishAt":"2026-06-12T10:00:00"}]}
                """);
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        java.util.Map<LocalDate, Integer> used = new java.util.HashMap<>();
        used.put(LocalDate.of(2026, 6, 11), 1);
        used.put(LocalDate.of(2026, 6, 12), 1);
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("sohu"), any()))
                .thenAnswer(invocation -> {
                    LocalDate date = invocation.getArgument(2);
                    return new CompanyChannelQuotaService.DistributionQuotaView(
                            "self_media:sohu", "day", date.toString(), used.getOrDefault(date, 0), 2);
                });
        doAnswer(invocation -> {
            LocalDate date = invocation.getArgument(4);
            used.merge(date, 1, Integer::sum);
            return null;
        }).when(companyChannelQuotaService).reserveProjectSelfMediaPlan(any(), any(), any(), any(), any());
        List<ProjectSelfMediaScheduleItem> originals = new java.util.ArrayList<>();
        for (int day : List.of(11, 12)) {
            ProjectSelfMediaScheduleItem item = storedItem();
            item.setId((long) day);
            item.setSelfMediaAccountId(20L);
            item.setPlatform("sohu");
            item.setPlannedExecutionAt(LocalDateTime.of(2026, 6, day, 10, 0));
            item.setPlannedPublishAt(item.getPlannedExecutionAt());
            originals.add(item);
        }
        when(scheduleItemMapper.selectList(any())).thenReturn(originals);
        ProjectSelfMediaAutoScheduleRequest request = dailyQuotaRequest();
        request.setSupplementExistingBatch(true);

        var response = service.createForProject(7L, request, "manual", "daily-supplement");

        assertEquals(2, response.getPlannedCount());
        assertEquals(4, batch.getPlannedCount());
        assertEquals(2, batch.getRejectedCount());
        verify(scheduleItemMapper, times(2)).insert(any(ProjectSelfMediaScheduleItem.class));
        verify(companyChannelQuotaService).reserveProjectSelfMediaPlan(eq(6L), eq(7L), eq("sohu"), any(), eq(LocalDate.of(2026, 6, 11)));
        verify(companyChannelQuotaService).reserveProjectSelfMediaPlan(eq(6L), eq(7L), eq("sohu"), any(), eq(LocalDate.of(2026, 6, 12)));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var savedPlans = mapper.readTree(batch.getRequestPayload()).get("plans");
        for (int i = 0; i < 2; i++) {
            assertEquals(originals.get(i).getPlannedExecutionAt(),
                    mapper.convertValue(savedPlans.get(i).get("plannedExecutionAt"), LocalDateTime.class));
            verify(scheduleItemMapper, never()).updateById(originals.get(i));
        }
        ArgumentCaptor<ProjectSelfMediaScheduleItem> inserted = ArgumentCaptor.forClass(ProjectSelfMediaScheduleItem.class);
        verify(scheduleItemMapper, times(2)).insert(inserted.capture());
        assertTrue(inserted.getAllValues().stream().allMatch(item -> item.getPlannedExecutionAt().getHour() == 15));
        assertEquals(0, service.previewForProject(7L, request).getRequestedCount());
        service.createForProject(7L, request, "manual", "daily-supplement");
        verify(scheduleItemMapper, times(2)).insert(any(ProjectSelfMediaScheduleItem.class));
        assertThrows(BizException.class, () -> service.createForProject(7L, request, "manual", "new-key"));
    }

    @Test
    void dailyQuotaOnlyUsesFutureTimesAndProjectServiceDates() {
        prepareDailyQuota("sohu", 2, List.of(slot(11, 10), slot(12, 10), slot(12, 15), slot(13, 10)));
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 12, 11, 0)));
        Project project = project();
        project.setEndDate(LocalDate.of(2026, 6, 12));
        when(projectMapper.selectById(7L)).thenReturn(project);

        var response = service.previewForProject(7L, dailyQuotaRequest());

        assertEquals(2, response.getRequestedCount());
        assertTrue(response.getSlotGroups().get(0).getSelectedSlots().stream()
                .allMatch(slot -> slot.getExecutionAt().isAfter(LocalDateTime.of(2026, 6, 12, 11, 0))
                        && slot.getExecutionAt().toLocalDate().equals(LocalDate.of(2026, 6, 12))));
    }

    @Test
    void dailyQuotaStillChecksActiveSchedulesOnEachPublishDate() {
        prepareDailyQuota("sohu", 2, List.of(slot(11, 10), slot(12, 10)));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(eq(8L), eq("sohu"),
                eq(LocalDateTime.of(2026, 6, 11, 0, 0)), eq(LocalDateTime.of(2026, 6, 12, 0, 0)), anyList()))
                .thenReturn(2L);

        var response = service.previewForProject(7L, dailyQuotaRequest());

        assertEquals(2, response.getRequestedCount());
        assertTrue(response.getSlotGroups().get(0).getSelectedSlots().stream()
                .allMatch(slot -> slot.getExecutionAt().toLocalDate().equals(LocalDate.of(2026, 6, 12))));
    }

    @Test
    void dailyQuotaIsSharedBySelectedAccountsRatherThanMultipliedPerAccount() {
        prepareDailyQuota("sohu", 2, List.of(slot(11, 10), slot(11, 15), slot(12, 10), slot(12, 15)));
        SelfMediaAccount second = account(21L, "active");
        second.setPlatform("sohu");
        when(selfMediaAccountMapper.selectById(21L)).thenReturn(second);
        ProjectSelfMediaAutoScheduleRequest request = dailyQuotaRequest();
        request.setSelfMediaAccountIds(List.of(20L, 21L));

        var response = service.previewForProject(7L, request);

        assertEquals(4, response.getRequestedCount());
        assertEquals(2, response.getPlannedItems().stream().filter(item -> item.getSelfMediaAccountId() == 20L).count());
        assertEquals(2, response.getPlannedItems().stream().filter(item -> item.getSelfMediaAccountId() == 21L).count());
    }

    @Test
    void dailyQuotaPrioritizesHistoricalCarryOverWithinDailyBudget() {
        prepareDailyQuota("sohu", 2, List.of(slot(11, 10), slot(11, 15), slot(12, 10), slot(12, 15)));
        ProjectSelfMediaScheduleCarryOver pending = carryOver(88L, "2026-05", "2026-06", 1);
        pending.setCarryOverPlanJson("[{\"accountId\":20,\"platform\":\"sohu\"}]");
        when(carryOverMapper.selectList(any())).thenReturn(List.of(pending));

        var response = service.previewForProject(7L, dailyQuotaRequest());

        assertEquals(4, response.getRequestedCount());
        assertEquals(3, response.getNormalRequiredCount());
        assertEquals(1, response.getPendingCarryOverCount());
        assertTrue(response.getEnough());
    }

    @Test
    void supplementDailyQuotaCarriesOnlyPlansWithoutAnAvailableTime() {
        prepareDailyQuota("sohu", 2, List.of(slot(11, 10)));
        var onlyTime = new BusinessCalendarService.PublishSlot(LocalDate.of(2026, 6, 11), "上午",
                LocalTime.of(10, 0), LocalTime.of(10, 0), LocalDateTime.of(2026, 6, 11, 10, 0), 0, "工作日", 4, false);
        when(businessCalendarService.allPublishSlots(any(), anyBoolean())).thenReturn(List.of(onlyTime));
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setId(33L);
        batch.setStatus("planned");
        batch.setPlannedCount(0);
        batch.setRequestedCount(0);
        batch.setRequestPayload("{\"targetMonth\":\"2026-06\",\"plans\":[]}");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        ProjectSelfMediaAutoScheduleRequest request = dailyQuotaRequest();
        request.setSupplementExistingBatch(true);
        assertThrows(BizException.class, () -> service.createForProject(7L, request, "manual", "without-decision"));
        request.setDecisionStrategy("carry_over");
        request.setDecisionReason("发布窗口不足");

        var response = service.createForProject(7L, request, "manual", "with-decision");

        assertEquals(1, response.getPlannedCount());
        assertEquals(1, response.getCarryOverCount());
        verify(scheduleItemMapper, times(1)).insert(any(ProjectSelfMediaScheduleItem.class));
        verify(companyChannelQuotaService, times(1)).reserveProjectSelfMediaPlan(
                eq(6L), eq(7L), eq("sohu"), any(), eq(LocalDate.of(2026, 6, 11)));
    }

    @Test
    void previewForProjectUsesQuotaWithoutArticleInventory() {
        ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
        request.setTargetMonth("2026-06");
        request.setSelfMediaAccountIds(List.of(20L));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
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
    void previewForProjectAllowsTwoScheduleSlotsOnSameDayWhenNeeded() {
        ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
        request.setTargetMonth("2026-06");
        request.setSelfMediaAccountIds(List.of(20L));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 2));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(11, 9), slot(11, 15)));
        when(scheduleAdapterRouter.rules("toutiao", "platform_schedule"))
                .thenReturn(SelfMediaPlatformScheduleRules.defaults());

        var response = service.previewForProject(7L, request);

        assertEquals(2, response.getAvailableSlotCount());
        assertEquals(1, response.getSlotGroups().get(0).getRemainingWorkdayCount());
        assertEquals(true, response.getEnough());
        assertEquals(0, response.getDeficitCount());
    }

    @Test
    void previewForProjectUsesQuotaPlatformForQuotaAndPublishPlatformForScheduleOccupation() {
        ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
        request.setTargetMonth("2026-06");
        request.setSelfMediaAccountIds(List.of(20L));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        SelfMediaAccount account = account();
        account.setPlatform("wechat_mp");
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account);
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("wechat"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:wechat", "month", "2026-06", 2, 5));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("wechat_mp"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(1L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(11, 9), slot(12, 9), slot(13, 9)));
        when(scheduleAdapterRouter.rules("wechat", "platform_schedule"))
                .thenReturn(SelfMediaPlatformScheduleRules.defaults());

        assertEquals(3, service.previewForProject(7L, request).getPlannedCount());
        verify(companyChannelQuotaService).selfMediaDistributionQuota(
                eq(6L), eq("wechat"), eq(LocalDate.of(2026, 6, 1)));
        verify(selfMediaPublishScheduleMapper).countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("wechat_mp"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList());
    }

    @Test
    void previewForProjectReportsInsufficientFutureSlots() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 21, 9, 0)));
        ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
        request.setTargetMonth("2026-06");
        request.setSelfMediaAccountIds(List.of(20L));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
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
        assertEquals(0, response.getAvailableSlotCount());
        assertEquals(2, response.getDeficitCount());
        assertEquals(false, response.getEnough());
        assertEquals("carry_over", response.getRecommendedStrategy());
        assertEquals(2, response.getRejectedCount());
        assertEquals(1, response.getSlotGroups().size());
        assertEquals(false, response.getSlotGroups().get(0).getEnough());
        assertEquals(2, response.getSlotGroups().get(0).getDeficitCount());
        assertEquals(0, response.getSlotGroups().get(0).getRemainingWorkdayCount());
        assertTrue(response.getSlotGroups().get(0).getMessage().contains("目标月份剩余可自动排期时间不足"));
    }

    @Test
    void previewForProjectSummarizesActualCapacityByPlatformMix() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 21, 9, 0)));
        ProjectSelfMediaAutoScheduleRequest request = new ProjectSelfMediaAutoScheduleRequest();
        request.setTargetMonth("2026-06");
        request.setSelfMediaAccountIds(List.of(20L, 21L));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        SelfMediaAccount wechat = account();
        wechat.setId(21L);
        wechat.setPlatform("wechat_mp");
        when(selfMediaAccountMapper.selectById(21L)).thenReturn(wechat);
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("toutiao"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:toutiao", "month", "2026-06", 0, 1));
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq("wechat"), any()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView(
                        "self_media:wechat", "month", "2026-06", 0, 3));
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("toutiao"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(selfMediaPublishScheduleMapper.countActiveByBrandPlatformAndPeriod(
                eq(8L), eq("wechat_mp"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(0L);
        when(businessCalendarService.allPublishSlots(eq(java.time.YearMonth.of(2026, 6)), eq(false)))
                .thenReturn(List.of(slot(21, 10), slot(21, 11), slot(22, 10), slot(23, 10), slot(24, 10)));
        when(scheduleAdapterRouter.rules(eq("toutiao"), anyString()))
                .thenReturn(SelfMediaPlatformScheduleRules.defaults());
        when(scheduleAdapterRouter.rules(eq("wechat"), anyString()))
                .thenReturn(new SelfMediaPlatformScheduleRules(10, 10_000, 4, 7 * 24 * 60));

        var response = service.previewForProject(7L, request);

        assertEquals(4, response.getRequestedCount());
        assertEquals(1, response.getAvailableSlotCount());
        assertEquals(3, response.getDeficitCount());
        assertEquals(false, response.getEnough());
        assertEquals("carry_over", response.getRecommendedStrategy());
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
        schedule.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
        schedule.setPlannedPublishAt(LocalDateTime.of(2026, 6, 1, 9, 10));
        when(selfMediaPublishScheduleMapper.selectLatestByArticleAccountAndPlatform(66L, 20L, "toutiao"))
                .thenReturn(schedule);
        SelfMediaPublishScheduleVO retried = new SelfMediaPublishScheduleVO();
        retried.setId(88L);
        when(scheduleService.recheckPublishResult(88L)).thenReturn(retried);

        ProjectSelfMediaScheduleBatchDetailVO detail = service.retryAbnormalScheduleItems(7L, "2026-06");

        assertEquals(2, detail.getItems().size());
        assertEquals(List.of("RECHECK_PUBLISH_RESULT"), detail.getItems().get(0).getAllowedActions());
        verify(scheduleService).recheckPublishResult(88L);
    }

    @Test
    void rescheduleAbnormalScheduleItemsRejectsPublishResultCheckQueue() {
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        batch.setStatus("partial_failed");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(generationTaskMapper.selectById(55L)).thenReturn(generationTask(55L, 66L));
        SelfMediaPublishSchedule schedule = new SelfMediaPublishSchedule();
        schedule.setId(88L);
        schedule.setStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        schedule.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
        schedule.setFailureCode("PUBLISH_RESULT_CHECK_HELPER_FAILED");
        schedule.setDiagnosticsJson("{\"published\":\"unknown\"}");
        schedule.setPlannedPublishAt(LocalDateTime.of(2026, 6, 1, 9, 10));
        when(selfMediaPublishScheduleMapper.selectLatestByArticleAccountAndPlatform(66L, 20L, "toutiao"))
                .thenReturn(schedule);

        BizException error = assertThrows(
                BizException.class,
                () -> service.rescheduleAbnormalScheduleItemsToNextMonth(7L, "2026-06")
        );

        assertTrue(error.getMessage().contains("PUBLISH_RESULT_CHECK_RESCHEDULE_FORBIDDEN"));
        assertEquals(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED, schedule.getStatus());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, schedule.getQueueKind());
        assertEquals("PUBLISH_RESULT_CHECK_HELPER_FAILED", schedule.getFailureCode());
        assertEquals("{\"published\":\"unknown\"}", schedule.getDiagnosticsJson());
        verify(selfMediaPublishScheduleMapper, never()).updateById(any());
    }

    @Test
    void ignoreAbnormalScheduleItemsRejectsPublishResultCheckQueue() {
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        batch.setStatus("partial_failed");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(generationTaskMapper.selectById(55L)).thenReturn(generationTask(55L, 66L));
        SelfMediaPublishSchedule schedule = new SelfMediaPublishSchedule();
        schedule.setId(88L);
        schedule.setStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        schedule.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
        schedule.setFailureCode("PUBLISH_RESULT_CHECK_HELPER_FAILED");
        schedule.setDiagnosticsJson("{\"published\":\"unknown\"}");
        schedule.setPlannedPublishAt(LocalDateTime.of(2026, 6, 1, 9, 10));
        when(selfMediaPublishScheduleMapper.selectLatestByArticleAccountAndPlatform(66L, 20L, "toutiao"))
                .thenReturn(schedule);

        BizException error = assertThrows(
                BizException.class,
                () -> service.ignoreAbnormalScheduleItems(7L, "2026-06")
        );

        assertTrue(error.getMessage().contains("PUBLISH_RESULT_CHECK_IGNORE_FORBIDDEN"));
        assertEquals(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED, schedule.getStatus());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, schedule.getQueueKind());
        assertEquals("PUBLISH_RESULT_CHECK_HELPER_FAILED", schedule.getFailureCode());
        assertEquals("{\"published\":\"unknown\"}", schedule.getDiagnosticsJson());
        verify(selfMediaPublishScheduleMapper, never()).updateById(any());
    }

    @Test
    void rescheduleAbnormalScheduleItemsRechecksQueueUnderLock() {
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        batch.setStatus("partial_failed");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(generationTaskMapper.selectById(55L)).thenReturn(generationTask(55L, 66L));
        SelfMediaPublishSchedule detailSnapshot = new SelfMediaPublishSchedule();
        detailSnapshot.setId(88L);
        detailSnapshot.setStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        detailSnapshot.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        detailSnapshot.setPlannedPublishAt(LocalDateTime.of(2026, 6, 1, 9, 10));
        when(selfMediaPublishScheduleMapper.selectLatestByArticleAccountAndPlatform(66L, 20L, "toutiao"))
                .thenReturn(detailSnapshot);
        SelfMediaPublishSchedule locked = new SelfMediaPublishSchedule();
        locked.setId(88L);
        locked.setStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        locked.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
        locked.setFailureCode("PUBLISH_RESULT_CHECK_HELPER_FAILED");
        locked.setDiagnosticsJson("{\"published\":\"unknown\"}");
        when(selfMediaPublishScheduleMapper.selectByIdForUpdate(88L)).thenReturn(locked);

        BizException error = assertThrows(
                BizException.class,
                () -> service.rescheduleAbnormalScheduleItemsToNextMonth(7L, "2026-06")
        );

        assertTrue(error.getMessage().contains("PUBLISH_RESULT_CHECK_RESCHEDULE_FORBIDDEN"));
        verify(selfMediaPublishScheduleMapper).selectByIdForUpdate(88L);
        verify(selfMediaPublishScheduleMapper, never()).updateById(any());
    }

    @Test
    void ignoreAbnormalScheduleItemsRechecksQueueUnderLock() {
        ProjectSelfMediaScheduleBatch batch = processingBatchWithTwoGenerationPlans();
        batch.setStatus("partial_failed");
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(generationTaskMapper.selectById(55L)).thenReturn(generationTask(55L, 66L));
        SelfMediaPublishSchedule detailSnapshot = new SelfMediaPublishSchedule();
        detailSnapshot.setId(88L);
        detailSnapshot.setStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        detailSnapshot.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        detailSnapshot.setPlannedPublishAt(LocalDateTime.of(2026, 6, 1, 9, 10));
        when(selfMediaPublishScheduleMapper.selectLatestByArticleAccountAndPlatform(66L, 20L, "toutiao"))
                .thenReturn(detailSnapshot);
        SelfMediaPublishSchedule locked = new SelfMediaPublishSchedule();
        locked.setId(88L);
        locked.setStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        locked.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
        locked.setFailureCode("PUBLISH_RESULT_CHECK_HELPER_FAILED");
        locked.setDiagnosticsJson("{\"published\":\"unknown\"}");
        when(selfMediaPublishScheduleMapper.selectByIdForUpdate(88L)).thenReturn(locked);

        BizException error = assertThrows(
                BizException.class,
                () -> service.ignoreAbnormalScheduleItems(7L, "2026-06")
        );

        assertTrue(error.getMessage().contains("PUBLISH_RESULT_CHECK_IGNORE_FORBIDDEN"));
        verify(selfMediaPublishScheduleMapper).selectByIdForUpdate(88L);
        verify(selfMediaPublishScheduleMapper, never()).updateById(any());
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
    void getBatchDetailMatchesRetryScheduleByPublishPlatformAlias() {
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
                  "scheduleStrategy": "platform_specific",
                  "includeAdjustedWorkdays": false,
                  "plans": [
                    {
                      "generationBatchId": 44,
                      "generationTaskId": 55,
                      "selfMediaAccountId": 20,
                      "platform": "wechat"
                    }
                  ]
                }
                """);
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        SelfMediaAccount account = account();
        account.setPlatform("wechat_mp");
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account);

        BatchArticleGenerationTask task = generationTask(55L, 66L);
        when(generationTaskMapper.selectById(55L)).thenReturn(task);

        ArticleDraft article = new ArticleDraft();
        article.setId(66L);
        article.setTitle("公众号排期文章");
        when(articleDraftMapper.selectById(66L)).thenReturn(article);
        when(selfMediaPublishScheduleRequestMapper.selectByRequestKey(8L, "project-auto-33-55"))
                .thenReturn(null);

        SelfMediaPublishSchedule retrySchedule = new SelfMediaPublishSchedule();
        retrySchedule.setId(132L);
        retrySchedule.setStatus("pending");
        retrySchedule.setPlatform("wechat_mp");
        retrySchedule.setPlannedPublishAt(LocalDateTime.of(2026, 6, 11, 10, 10));
        when(selfMediaPublishScheduleMapper.selectLatestByArticleAccountAndPlatform(66L, 20L, "wechat_mp"))
                .thenReturn(retrySchedule);

        ProjectSelfMediaScheduleBatchDetailVO detail = service.getBatchDetail(7L, "2026-06");

        assertEquals("created", detail.getBatch().getStatus());
        assertEquals(1, detail.getBatch().getCreatedCount());
        assertEquals(0, detail.getBatch().getRejectedCount());
        ProjectSelfMediaScheduleBatchDetailVO.Item item = detail.getItems().get(0);
        assertEquals(132L, item.getScheduleId());
        assertEquals("pending", item.getScheduleStatus());
        assertEquals(LocalDateTime.of(2026, 6, 11, 10, 10), item.getPlannedPublishAt());
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
        SelfMediaPublishSchedule persisted = new SelfMediaPublishSchedule();
        persisted.setId(88L);
        persisted.setStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        when(selfMediaPublishScheduleMapper.selectLatestByArticleAccountAndPlatform(66L, 20L, "toutiao"))
                .thenReturn(null, persisted);

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
        SelfMediaPublishSchedule persisted = new SelfMediaPublishSchedule();
        persisted.setId(88L);
        persisted.setStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        when(selfMediaPublishScheduleMapper.selectLatestByArticleAccountAndPlatform(66L, 20L, "toutiao"))
                .thenReturn(null, persisted);
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

    @Test
    void cancelStoredPlanItemRefundsReservedQuota() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        ProjectSelfMediaScheduleItem item = storedItem();
        when(scheduleItemMapper.selectById(100L)).thenReturn(item);
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(item));
        when(batchMapper.selectById(33L)).thenReturn(batch);
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);

        ProjectSelfMediaScheduleItemActionRequest request = new ProjectSelfMediaScheduleItemActionRequest();
        request.setReason("运营取消");
        service.cancelPlanItem(7L, 100L, request);

        assertEquals("cancelled", item.getStatus());
        assertEquals("refunded", item.getQuotaReservationStatus());
        verify(companyChannelQuotaService).refundProjectSelfMediaPlan(100L);
    }

    @Test
    void batchCanBePausedAndResumedWithoutChangingStoredItems() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        ProjectSelfMediaScheduleItem item = storedItem();
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(item));

        service.pauseBatch(7L, "2026-06");
        assertEquals("paused", batch.getStatus());

        service.resumeBatch(7L, "2026-06");
        assertEquals("planned", batch.getStatus());
        assertEquals("planned", item.getStatus());
    }

    @Test
    void delayedGenerationStartUsesFiniteBackoffAndRefundsAfterThirdFailure() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setGenerationDueAt(LocalDateTime.of(2026, 6, 1, 7, 0));
        when(batchMapper.selectProcessing(5)).thenReturn(List.of(batch));
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(item));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(keywordGroupResultMapper.selectProjectQuestionsByTiers(7L, "'A'"))
                .thenThrow(new BizException(70043, "question pool temporarily unavailable"));

        for (int attempt = 1; attempt <= 3; attempt++) {
            item.setNextGenerationAttemptAt(LocalDateTime.of(2026, 6, 1, 8, 0));
            service.progressProcessingBatches(5);
        }

        assertEquals(3, item.getGenerationAttemptCount());
        assertEquals("generation_failed", item.getStatus());
        assertEquals("refunded", item.getQuotaReservationStatus());
        verify(companyChannelQuotaService).refundProjectSelfMediaPlan(100L);
    }

    @Test
    void inactiveProjectSkipsDueItemWithoutCreatingGenerationTaskOrError() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setFailureMessage("旧批次错误");
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setGenerationClaimToken("old-claim");
        item.setGenerationClaimedAt(LocalDateTime.of(2026, 6, 1, 7, 55));
        item.setGenerationErrorMessage("旧生成错误");
        item.setScheduleErrorCode("OLD_ERROR");
        item.setScheduleErrorMessage("旧排期错误");
        Project paused = project();
        paused.setStatus("paused");
        when(projectMapper.selectById(7L)).thenReturn(paused);
        when(batchMapper.selectProcessing(5)).thenReturn(List.of(batch));
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(item));
        AtomicReference<String> persistedItemSet = new AtomicReference<>();
        doAnswer(invocation -> {
            Object wrapper = invocation.getArgument(1);
            if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?> update) {
                persistedItemSet.set(update.getSqlSet());
            }
            return 1;
        }).when(scheduleItemMapper).update(eq(null), any());
        AtomicReference<String> persistedBatchSet = new AtomicReference<>();
        doAnswer(invocation -> {
            Object wrapper = invocation.getArgument(1);
            if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?> update) {
                persistedBatchSet.set(update.getSqlSet());
            }
            return 1;
        }).when(batchMapper).update(eq(null), any());

        assertEquals(1, service.progressProcessingBatches(5));

        assertEquals("skipped", item.getStatus());
        assertNull(item.getGenerationErrorMessage());
        assertNull(item.getScheduleErrorCode());
        assertNull(item.getScheduleErrorMessage());
        assertEquals("refunded", item.getQuotaReservationStatus());
        assertTrue(persistedItemSet.get().contains("generation_claim_token"));
        assertTrue(persistedItemSet.get().contains("generation_error_message"));
        assertTrue(persistedItemSet.get().contains("schedule_error_code"));
        assertTrue(persistedItemSet.get().contains("schedule_error_message"));
        assertTrue(persistedBatchSet.get().contains("failure_message"));
        verify(generationService, never()).createSystemBatch(any(), any(), anyString());
        verify(companyChannelQuotaService).refundProjectSelfMediaPlan(100L);
    }

    @Test
    void historicalDouyinCapacityFailureReturnsToAutomaticWaiting() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setStatus("failed");
        batch.setFailureMessage("部分文章生成或排期失败");
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setPlatform("douyin");
        item.setStatus("schedule_failed");
        item.setArticleId(66L);
        item.setScheduleErrorCode("DOUYIN_IMAGE_GENERATION_FAILED");
        item.setScheduleErrorMessage("抖音图片生成失败，请在文章详情中重试：All LLM candidates are waiting for permits");
        when(batchMapper.selectRecoverableDouyinImageFailures(5)).thenReturn(List.of(batch));
        when(batchMapper.selectProcessing(4)).thenReturn(List.of());
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(item));
        when(douyinArticleImageService.prepareAutomatic(66L, 99L)).thenReturn(false);
        AtomicReference<String> persistedItemSet = new AtomicReference<>();
        doAnswer(invocation -> {
            Object wrapper = invocation.getArgument(1);
            if (wrapper instanceof com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?> update) {
                persistedItemSet.set(update.getSqlSet());
            }
            return 1;
        }).when(scheduleItemMapper).update(eq(null), any());

        assertEquals(1, service.progressProcessingBatches(5));

        assertEquals("generated", item.getStatus());
        assertNull(item.getScheduleErrorCode());
        assertNull(item.getScheduleErrorMessage());
        assertTrue(persistedItemSet.get().contains("schedule_error_code"));
        assertTrue(persistedItemSet.get().contains("schedule_error_message"));
        verify(douyinArticleImageService).prepareAutomatic(66L, 99L);
        verify(scheduleService, never()).createSystemSchedulesUsingReservedQuota(any(), anyString(), any(), any());
    }

    @Test
    void refundedHistoricalImageFailureIsNotRecovered() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setStatus("failed");
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setPlatform("douyin");
        item.setStatus("schedule_failed");
        item.setArticleId(66L);
        item.setQuotaReservationStatus("refunded");
        item.setScheduleErrorCode("DOUYIN_IMAGE_GENERATION_FAILED");
        item.setScheduleErrorMessage("All LLM candidates are waiting for permits");
        when(batchMapper.selectRecoverableDouyinImageFailures(5)).thenReturn(List.of(batch));
        when(batchMapper.selectProcessing(4)).thenReturn(List.of());
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(item));

        assertEquals(0, service.progressProcessingBatches(5));

        assertEquals("schedule_failed", item.getStatus());
        verify(douyinArticleImageService, never()).prepareAutomatic(any(), any());
        verify(scheduleItemMapper, never()).update(eq(null), any());
    }

    @Test
    void historicalSystemRefundedImageFailureShowsManualRescheduleAfterImagesApplied() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setStatus("created");
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setPlatform("douyin");
        item.setStatus("cancelled");
        item.setArticleId(66L);
        item.setQuotaReservationStatus("refunded");
        item.setScheduleErrorCode("DOUYIN_IMAGE_GENERATION_FAILED");
        item.setScheduleErrorMessage("抖音图片生成失败");
        item.setCancelledAt(LocalDateTime.of(2026, 6, 4, 9, 0));
        item.setCancellationReason("排期失败超过可重试时限，系统已释放预占额度");
        ProjectSelfMediaScheduleItem scheduled = storedItem();
        scheduled.setId(99L);
        scheduled.setStatus("scheduled");
        ArticleDraft article = new ArticleDraft();
        article.setId(66L);
        article.setDouyinImageBatchId(12L);
        article.setCurrentVersionNo(3);
        article.setStatus("ready");
        DouyinArticleImageBatch imageBatch = appliedImageBatch(12L, 66L, 3);

        when(batchMapper.selectRecoverableDouyinImageFailures(5)).thenReturn(List.of(batch));
        when(batchMapper.selectProcessing(4)).thenReturn(List.of());
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(item, scheduled));
        when(articleDraftMapper.selectById(66L)).thenReturn(article);
        when(douyinArticleImageBatchMapper.selectById(12L)).thenReturn(imageBatch);
        when(scheduleItemMapper.markRefundedDouyinScheduleForManualReschedule(eq(66L), any(), any()))
                .thenReturn(1);

        assertEquals(1, service.progressProcessingBatches(5));

        assertEquals("schedule_failed", item.getStatus());
        assertEquals("DOUYIN_IMAGE_READY_REQUIRES_RESCHEDULE", item.getScheduleErrorCode());
        assertTrue(item.getScheduleErrorMessage().contains("重新处理本批次"));
        assertNull(item.getCancelledAt());
        assertNull(item.getCancellationReason());
        assertEquals("partial_failed", batch.getStatus());
        verify(douyinArticleImageService, never()).prepareAutomatic(any(), any());
    }

    @Test
    void historicalAllCancelledBatchShowsManualRescheduleAfterImagesApplied() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setStatus("cancelled");
        ProjectSelfMediaScheduleItem item = systemRefundedImageFailureItem(100L, 66L);
        item.setScheduleErrorMessage("抖音图片生成失败");
        ArticleDraft article = new ArticleDraft();
        article.setId(66L);
        article.setDouyinImageBatchId(12L);
        article.setCurrentVersionNo(3);
        article.setStatus("ready");

        when(batchMapper.selectRecoverableDouyinImageFailures(5)).thenReturn(List.of(batch));
        when(batchMapper.selectProcessing(4)).thenReturn(List.of());
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(item));
        when(articleDraftMapper.selectById(66L)).thenReturn(article);
        when(douyinArticleImageBatchMapper.selectById(12L)).thenReturn(appliedImageBatch(12L, 66L, 3));
        when(scheduleItemMapper.markRefundedDouyinScheduleForManualReschedule(eq(66L), any(), any()))
                .thenReturn(1);

        assertEquals(1, service.progressProcessingBatches(5));

        assertEquals("schedule_failed", item.getStatus());
        assertEquals("DOUYIN_IMAGE_READY_REQUIRES_RESCHEDULE", item.getScheduleErrorCode());
        assertEquals("failed", batch.getStatus());
    }

    @Test
    void dueStoredPlanItemCreatesGenerationTaskFromRandomATierQuestion() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        ProjectSelfMediaScheduleItem item = storedItem();
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 11, 5, 0)));
        when(batchMapper.selectProcessing(5)).thenReturn(List.of(batch));
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(item));
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());

        KeywordGroupResult first = new KeywordGroupResult();
        first.setKeywordText("第一个 A 类问题");
        first.setSceneCode("first-scene");
        KeywordGroupResult selected = new KeywordGroupResult();
        selected.setKeywordText("本次随机选中的 A 类问题");
        selected.setSceneCode("selected-scene");
        List<KeywordGroupResult> questions = List.of(first, selected);
        when(keywordGroupResultMapper.selectProjectQuestionsByTiers(7L, "'A'"))
                .thenReturn(questions);

        BatchArticleGenerateResponse generation = new BatchArticleGenerateResponse(
                44L, 1, "pending", false, false, List.of());
        when(generationService.createSystemBatch(any(), eq(99L), eq("project-self-media-plan-33-100")))
                .thenReturn(generation);
        BatchArticleGenerationTask pendingTask = new BatchArticleGenerationTask();
        pendingTask.setId(55L);
        pendingTask.setBatchId(44L);
        pendingTask.setStatus("pending");
        pendingTask.setCreatedAt(LocalDateTime.of(2026, 6, 11, 5, 0));
        when(generationTaskMapper.selectList(any())).thenReturn(List.of(pendingTask));
        when(generationTaskMapper.selectById(55L)).thenReturn(pendingTask);

        ProjectSelfMediaScheduleService testedService = spy(service);
        doReturn(selected).when(testedService).randomQuestion(questions);

        assertEquals(1, testedService.progressProcessingBatches(5));

        ArgumentCaptor<BatchArticleGenerateRequest> requestCaptor =
                ArgumentCaptor.forClass(BatchArticleGenerateRequest.class);
        verify(generationService).createSystemBatch(
                requestCaptor.capture(), eq(99L), eq("project-self-media-plan-33-100"));
        BatchArticleGenerateRequest.TopicConfig topic = requestCaptor.getValue().getTopics().get(0);
        assertEquals("本次随机选中的 A 类问题", topic.getTopic());
        assertEquals("本次随机选中的 A 类问题", topic.getTopicAsQuestion());
        assertEquals("selected-scene", topic.getQuestionSceneCode());
        assertEquals("generating", item.getStatus());
        assertEquals(55L, item.getGenerationTaskId());
    }

    @Test
    void failedStoredGenerationRetriesOriginalTaskWithoutReselectingTopic() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setStatus("generating");
        item.setGenerationBatchId(44L);
        item.setGenerationTaskId(55L);
        item.setGenerationAttemptCount(1);
        BatchArticleGenerationTask failedTask = failedGenerationTask(55L, "model timeout");
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 11, 6, 0)));
        when(batchMapper.selectProcessing(5)).thenReturn(List.of(batch));
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(item));
        when(generationTaskMapper.selectById(55L)).thenReturn(failedTask);

        assertEquals(1, service.progressProcessingBatches(5));

        assertEquals("generating", item.getStatus());
        assertEquals(44L, item.getGenerationBatchId());
        assertEquals(55L, item.getGenerationTaskId());
        assertEquals(2, item.getGenerationAttemptCount());
        verify(generationService).retryFailedSystem(44L);
        verify(generationService, never()).createSystemBatch(any(), any(), anyString());
        verify(keywordGroupResultMapper, never()).selectProjectQuestionsByTiers(any(), anyString());
    }

    @Test
    void runningGenerationUsesTaskStartTimeInsteadOfStalePlanTimestamp() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setStatus("generating");
        item.setGenerationTaskId(55L);
        item.setGenerationAttemptCount(1);
        item.setUpdatedAt(LocalDateTime.of(2026, 5, 20, 8, 0));
        BatchArticleGenerationTask task = new BatchArticleGenerationTask();
        task.setId(55L);
        task.setStatus("running");
        task.setCreatedAt(LocalDateTime.of(2026, 6, 1, 7, 55));
        task.setStartedAt(LocalDateTime.of(2026, 6, 1, 7, 56));
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 1, 8, 0)));
        when(batchMapper.selectProcessing(5)).thenReturn(List.of(batch));
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(item));
        when(generationTaskMapper.selectById(55L)).thenReturn(task);

        service.progressProcessingBatches(5);

        assertEquals("generating", item.getStatus());
        assertEquals(55L, item.getGenerationTaskId());
        assertEquals(1, item.getGenerationAttemptCount());
        assertTrue(item.getGenerationErrorMessage() == null);
        verify(companyChannelQuotaService, never()).refundProjectSelfMediaPlan(100L);
    }

    @Test
    void retryFailedStoredItemReusesArticleWhenGenerationTaskActuallySucceeded() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setStatus("partial_failed");
        ProjectSelfMediaScheduleItem failed = storedItem();
        failed.setStatus("generation_failed");
        failed.setGenerationBatchId(44L);
        failed.setGenerationTaskId(55L);
        failed.setQuotaReservationStatus("refunded");
        AtomicReference<ProjectSelfMediaScheduleItem> replacementRef = new AtomicReference<>();
        BatchArticleGenerationTask completed = generationTask(55L, 66L);
        completed.setBatchId(44L);
        completed.setPromptTemplateId(77L);
        completed.setPromptTemplateVersionId(78L);
        ArticlePromptTemplate template = new ArticlePromptTemplate();
        template.setId(77L);
        template.setName("最新品牌模板");
        SelfMediaPublishScheduleVO createdSchedule = new SelfMediaPublishScheduleVO();
        createdSchedule.setId(88L);
        SelfMediaPublishScheduleCreateResponse created = new SelfMediaPublishScheduleCreateResponse();
        created.setCreatedSchedules(List.of(createdSchedule));

        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(scheduleItemMapper.selectByBatchId(33L)).thenAnswer(invocation -> replacementRef.get() == null
                ? List.of(failed)
                : List.of(failed, replacementRef.get()));
        doAnswer(invocation -> {
            ProjectSelfMediaScheduleItem inserted = invocation.getArgument(0);
            inserted.setId(101L);
            replacementRef.set(inserted);
            return 1;
        }).when(scheduleItemMapper).insert(any(ProjectSelfMediaScheduleItem.class));
        when(generationTaskMapper.selectById(55L)).thenReturn(completed);
        when(promptTemplateMapper.selectById(77L)).thenReturn(template);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(scheduleService.createSystemSchedulesUsingReservedQuota(any(), anyString(), eq(99L), eq(101L)))
                .thenReturn(created);

        service.retryFailedItems(7L, "2026-06");

        ProjectSelfMediaScheduleItem replacement = replacementRef.get();
        assertEquals("scheduled", replacement.getStatus());
        assertEquals(66L, replacement.getArticleId());
        assertEquals(55L, replacement.getGenerationTaskId());
        assertEquals("最新品牌模板", replacement.getPromptTemplateName());
        assertEquals("skipped", failed.getStatus());
        verify(generationService, never()).createSystemBatch(any(), any(), anyString());
    }

    @Test
    void imageFailureRefundThenImageSuccessCanBeManuallyRescheduled() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 12, 10, 0)));
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setStatus("failed");
        ProjectSelfMediaScheduleItem failed = storedItem();
        failed.setPlatform("douyin");
        failed.setStatus("schedule_failed");
        failed.setArticleId(66L);
        failed.setScheduleErrorCode("DOUYIN_IMAGE_GENERATION_FAILED");
        failed.setScheduleErrorMessage("抖音图片生成失败，请在文章详情中重试");
        failed.setQuotaReservationStatus("reserved");
        CompanyChannelQuotaLedger ledger = new CompanyChannelQuotaLedger();
        ledger.setId(901L);
        ledger.setBizId("100");
        AtomicReference<ProjectSelfMediaScheduleItem> replacementRef = new AtomicReference<>();
        SelfMediaPublishScheduleVO createdSchedule = new SelfMediaPublishScheduleVO();
        createdSchedule.setId(88L);
        SelfMediaPublishScheduleCreateResponse created = new SelfMediaPublishScheduleCreateResponse();
        created.setCreatedSchedules(List.of(createdSchedule));
        ArticleDraft article = new ArticleDraft();
        article.setId(66L);
        article.setDouyinImageBatchId(12L);
        article.setCurrentVersionNo(3);
        article.setStatus("ready");

        when(quotaLedgerMapper.selectProjectPlanReconciliationCandidates(any(), eq(0L), eq(10)))
                .thenReturn(List.of(ledger));
        when(scheduleItemMapper.selectById(100L)).thenReturn(failed);
        when(articleDraftMapper.selectById(66L)).thenReturn(article);
        when(douyinArticleImageBatchMapper.selectById(12L)).thenReturn(appliedImageBatch(12L, 66L, 3));
        doAnswer(invocation -> {
            assertEquals("cancelled", failed.getStatus());
            assertEquals("refunded", failed.getQuotaReservationStatus());
            assertEquals("DOUYIN_IMAGE_GENERATION_FAILED", failed.getScheduleErrorCode());
            assertEquals("排期失败超过可重试时限，系统已释放预占额度", failed.getCancellationReason());
            failed.setStatus("schedule_failed");
            failed.setScheduleErrorCode("DOUYIN_IMAGE_READY_REQUIRES_RESCHEDULE");
            failed.setScheduleErrorMessage("图片已生成，原排期额度已退还，请在排期管理中点击“重新处理本批次”重新排期");
            failed.setCancelledBy(null);
            failed.setCancelledAt(null);
            failed.setCancellationReason(null);
            return 1;
        }).when(scheduleItemMapper).markRefundedDouyinScheduleForManualReschedule(eq(66L), eq(99L), any());
        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(scheduleItemMapper.selectByBatchId(33L)).thenAnswer(invocation -> replacementRef.get() == null
                ? List.of(failed)
                : List.of(failed, replacementRef.get()));
        doAnswer(invocation -> {
            ProjectSelfMediaScheduleItem inserted = invocation.getArgument(0);
            inserted.setId(101L);
            replacementRef.set(inserted);
            return 1;
        }).when(scheduleItemMapper).insert(any(ProjectSelfMediaScheduleItem.class));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(douyinArticleImageService.prepareAutomatic(66L, 99L)).thenReturn(true);
        when(scheduleService.createSystemSchedulesUsingReservedQuota(any(), anyString(), eq(99L), eq(101L)))
                .thenReturn(created);

        assertEquals(1, service.reconcilePlanQuotaReservations(10, 72));
        assertEquals("cancelled", failed.getStatus());
        assertEquals("refunded", failed.getQuotaReservationStatus());

        assertEquals(1, scheduleItemMapper.markRefundedDouyinScheduleForManualReschedule(
                66L, 99L, LocalDateTime.of(2026, 6, 12, 10, 0)));
        assertEquals("schedule_failed", failed.getStatus());
        assertEquals("DOUYIN_IMAGE_READY_REQUIRES_RESCHEDULE", failed.getScheduleErrorCode());

        service.retryFailedItems(7L, "2026-06");

        ProjectSelfMediaScheduleItem replacement = replacementRef.get();
        assertEquals("scheduled", replacement.getStatus());
        assertEquals(66L, replacement.getArticleId());
        assertEquals("transferred", replacement.getQuotaReservationStatus());
        assertEquals("skipped", failed.getStatus());
        assertEquals(LocalDateTime.of(2026, 6, 13, 9, 0), replacement.getPlannedExecutionAt());
        assertEquals(LocalDateTime.of(2026, 6, 13, 10, 0), replacement.getPlannedPublishAt());
        verify(companyChannelQuotaService).reserveProjectSelfMediaPlan(6L, 7L, "douyin", 101L,
                LocalDate.of(2026, 6, 13));
        verify(generationService, never()).createSystemBatch(any(), any(), anyString());
    }

    @Test
    void retryDoesNotRestoreManuallyCancelledRefundedImageFailure() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setStatus("failed");
        ProjectSelfMediaScheduleItem cancelled = storedItem();
        cancelled.setPlatform("douyin");
        cancelled.setStatus("cancelled");
        cancelled.setArticleId(66L);
        cancelled.setScheduleErrorCode("DOUYIN_IMAGE_GENERATION_FAILED");
        cancelled.setQuotaReservationStatus("refunded");
        cancelled.setCancelledBy(99L);
        cancelled.setCancellationReason("运营人员取消");
        ArticleDraft article = new ArticleDraft();
        article.setId(66L);
        article.setDouyinImageBatchId(12L);

        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(cancelled));
        when(articleDraftMapper.selectById(66L)).thenReturn(article);

        BizException error = assertThrows(BizException.class,
                () -> service.retryFailedItems(7L, "2026-06"));

        assertEquals("当前批次没有可重试的失败项", error.getMessage());
        verify(companyChannelQuotaService, never()).reserveProjectSelfMediaPlan(any(), any(), anyString(), any(), any());
    }

    @Test
    void retryRecognizesHistoricalSystemRefundedImageFailureAfterImagesApplied() {
        service.setClock(fixedClock(LocalDateTime.of(2026, 6, 12, 10, 0)));
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setStatus("failed");
        ProjectSelfMediaScheduleItem cancelled = storedItem();
        cancelled.setPlatform("douyin");
        cancelled.setStatus("cancelled");
        cancelled.setArticleId(66L);
        cancelled.setScheduleErrorCode("DOUYIN_IMAGE_GENERATION_FAILED");
        cancelled.setQuotaReservationStatus("refunded");
        cancelled.setCancellationReason("排期失败超过可重试时限，系统已释放预占额度");
        ArticleDraft article = new ArticleDraft();
        article.setId(66L);
        article.setDouyinImageBatchId(12L);
        article.setCurrentVersionNo(3);
        article.setStatus("ready");
        AtomicReference<ProjectSelfMediaScheduleItem> replacementRef = new AtomicReference<>();
        SelfMediaPublishScheduleVO createdSchedule = new SelfMediaPublishScheduleVO();
        createdSchedule.setId(88L);
        SelfMediaPublishScheduleCreateResponse created = new SelfMediaPublishScheduleCreateResponse();
        created.setCreatedSchedules(List.of(createdSchedule));

        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(scheduleItemMapper.selectByBatchId(33L)).thenAnswer(invocation -> replacementRef.get() == null
                ? List.of(cancelled)
                : List.of(cancelled, replacementRef.get()));
        when(articleDraftMapper.selectById(66L)).thenReturn(article);
        when(douyinArticleImageBatchMapper.selectById(12L)).thenReturn(appliedImageBatch(12L, 66L, 3));
        doAnswer(invocation -> {
            ProjectSelfMediaScheduleItem inserted = invocation.getArgument(0);
            inserted.setId(101L);
            replacementRef.set(inserted);
            return 1;
        }).when(scheduleItemMapper).insert(any(ProjectSelfMediaScheduleItem.class));
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account());
        when(douyinArticleImageService.prepareAutomatic(66L, 99L)).thenReturn(true);
        when(scheduleService.createSystemSchedulesUsingReservedQuota(any(), anyString(), eq(99L), eq(101L)))
                .thenReturn(created);

        service.retryFailedItems(7L, "2026-06");

        ProjectSelfMediaScheduleItem replacement = replacementRef.get();
        assertEquals("scheduled", replacement.getStatus());
        assertEquals(66L, replacement.getArticleId());
        assertEquals("transferred", replacement.getQuotaReservationStatus());
        assertEquals("skipped", cancelled.getStatus());
        verify(companyChannelQuotaService).reserveProjectSelfMediaPlan(6L, 7L, "douyin", 101L,
                LocalDate.of(2026, 6, 13));
    }

    @Test
    void retryRejectsDeletedArticleAndStaleAppliedImageBatch() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        batch.setStatus("cancelled");
        ProjectSelfMediaScheduleItem deletedArticleItem = systemRefundedImageFailureItem(100L, 66L);
        ProjectSelfMediaScheduleItem staleImageItem = systemRefundedImageFailureItem(101L, 67L);

        ArticleDraft deletedArticle = new ArticleDraft();
        deletedArticle.setId(66L);
        deletedArticle.setDouyinImageBatchId(12L);
        deletedArticle.setCurrentVersionNo(3);
        deletedArticle.setStatus("deleted");
        ArticleDraft editedArticle = new ArticleDraft();
        editedArticle.setId(67L);
        editedArticle.setDouyinImageBatchId(13L);
        editedArticle.setCurrentVersionNo(4);
        editedArticle.setStatus("ready");

        when(batchMapper.selectByProjectAndMonth(7L, "2026-06")).thenReturn(batch);
        when(scheduleItemMapper.selectByBatchId(33L)).thenReturn(List.of(deletedArticleItem, staleImageItem));
        when(articleDraftMapper.selectById(66L)).thenReturn(deletedArticle);
        when(articleDraftMapper.selectById(67L)).thenReturn(editedArticle);
        when(douyinArticleImageBatchMapper.selectById(13L)).thenReturn(appliedImageBatch(13L, 67L, 3));

        BizException error = assertThrows(BizException.class,
                () -> service.retryFailedItems(7L, "2026-06"));

        assertEquals("当前批次没有可重试的失败项", error.getMessage());
        verify(companyChannelQuotaService, never()).reserveProjectSelfMediaPlan(any(), any(), anyString(), any(), any());
    }

    @Test
    void monitorCreatesDeduplicatedRiskAlertForDueStoredItem() {
        ProjectSelfMediaScheduleBatch batch = storedBatch();
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setGenerationDueAt(LocalDateTime.of(2026, 6, 1, 7, 0));
        when(scheduleItemMapper.selectAtRiskItems(any(), eq(0L), eq(10)))
                .thenReturn(List.of(item));
        when(batchMapper.selectById(33L)).thenReturn(batch);

        assertEquals(1, service.monitorAtRiskDelayedGeneration(10));

        verify(systemAlertService).createOrRefreshRecipientAlert(
                eq("PROJECT_SELF_MEDIA_DELAYED_GENERATION_RISK"),
                eq("error"),
                eq("project_self_media_schedule"),
                anyString(),
                any(),
                eq(99L),
                eq(null),
                eq("project-self-media-generation-risk-100")
        );
    }

    private ProjectSelfMediaScheduleBatch storedBatch() {
        ProjectSelfMediaScheduleBatch batch = new ProjectSelfMediaScheduleBatch();
        batch.setId(33L);
        batch.setProjectId(7L);
        batch.setBrandId(8L);
        batch.setCompanyId(6L);
        batch.setTargetMonth("2026-06");
        batch.setStatus("planned");
        batch.setScheduleStrategy("platform_schedule");
        batch.setCreatedBy(99L);
        return batch;
    }

    private ProjectSelfMediaScheduleItem storedItem() {
        ProjectSelfMediaScheduleItem item = new ProjectSelfMediaScheduleItem();
        item.setId(100L);
        item.setBatchId(33L);
        item.setProjectId(7L);
        item.setBrandId(8L);
        item.setCompanyId(6L);
        item.setSelfMediaAccountId(20L);
        item.setPlatform("toutiao");
        item.setStatus("planned");
        item.setPlannedExecutionAt(LocalDateTime.of(2026, 6, 11, 9, 0));
        item.setPlannedPublishAt(LocalDateTime.of(2026, 6, 11, 10, 0));
        item.setGenerationDueAt(LocalDateTime.of(2026, 6, 11, 5, 0));
        item.setGenerationIdempotencyKey("project-self-media-plan-33-100");
        item.setGenerationAttemptCount(0);
        item.setQuotaReservationStatus("reserved");
        item.setCreatedBy(99L);
        return item;
    }

    private ProjectSelfMediaScheduleItem systemRefundedImageFailureItem(Long itemId, Long articleId) {
        ProjectSelfMediaScheduleItem item = storedItem();
        item.setId(itemId);
        item.setPlatform("douyin");
        item.setStatus("cancelled");
        item.setArticleId(articleId);
        item.setScheduleErrorCode("DOUYIN_IMAGE_GENERATION_FAILED");
        item.setQuotaReservationStatus("refunded");
        item.setCancellationReason("排期失败超过可重试时限，系统已释放预占额度");
        return item;
    }

    private DouyinArticleImageBatch appliedImageBatch(Long batchId, Long articleId, int appliedVersionNo) {
        DouyinArticleImageBatch imageBatch = new DouyinArticleImageBatch();
        imageBatch.setId(batchId);
        imageBatch.setArticleId(articleId);
        imageBatch.setStatus("applied");
        imageBatch.setAppliedVersionNo(appliedVersionNo);
        return imageBatch;
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

    private ProjectSelfMediaAutoScheduleRequest dailyQuotaRequest() {
        ProjectSelfMediaAutoScheduleRequest request = request();
        request.setArticleIds(List.of());
        request.setScheduleStrategy(SelfMediaPublishAutoScheduleService.STRATEGY_PLATFORM_SPECIFIC);
        return request;
    }

    private void prepareDailyQuota(String platform, int limit, List<BusinessCalendarService.PublishSlot> slots) {
        when(configMapper.selectByProjectId(7L)).thenReturn(config(true));
        SelfMediaAccount account = account();
        account.setPlatform(platform);
        when(selfMediaAccountMapper.selectById(20L)).thenReturn(account);
        when(businessCalendarService.allPublishSlots(any(), anyBoolean())).thenReturn(slots.stream()
                .map(slot -> new BusinessCalendarService.PublishSlot(slot.date(), slot.windowName(),
                        slot.plannedAt().toLocalTime(), slot.plannedAt().toLocalTime().plusHours(2), slot.plannedAt(),
                        slot.dayType(), slot.dayName(), slot.week(), slot.adjustedWorkday())).toList());
        when(companyChannelQuotaService.selfMediaDistributionQuota(eq(6L), eq(platform), any()))
                .thenAnswer(invocation -> {
                    LocalDate date = invocation.getArgument(2);
                    return new CompanyChannelQuotaService.DistributionQuotaView(
                            "self_media:" + platform, "day", date.toString(), 0, limit);
                });
        if ("sohu".equals(platform)) {
            var adapter = new com.huanjing.geo.module.content.service.adapter.SohuPlatformScheduleAdapter();
            when(scheduleAdapterRouter.contract(platform)).thenReturn(Optional.of(adapter.capabilityContract()));
            when(scheduleAdapterRouter.rules(platform, "backend_delayed_publish"))
                    .thenReturn(adapter.scheduleRules("backend_delayed_publish"));
        }
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
        config.setIncludeAdjustedWorkdays(false);
        return config;
    }

    private Project project() {
        Project project = new Project();
        project.setId(7L);
        project.setBrandId(8L);
        project.setCompanyId(6L);
        project.setStatus("active");
        project.setEndDate(LocalDate.of(2026, 12, 31));
        project.setCreatedBy(99L);
        return project;
    }

    private SelfMediaAccount account() {
        return account(20L, "active");
    }

    private SelfMediaAccount account(Long id, String status) {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(id);
        account.setBrandId(8L);
        account.setPlatform("toutiao");
        account.setStatus(status);
        return account;
    }

    private ProjectSelfMediaScheduleCarryOver carryOver(Long id, String sourceMonth, String targetMonth, int count) {
        ProjectSelfMediaScheduleCarryOver row = new ProjectSelfMediaScheduleCarryOver();
        row.setId(id);
        row.setProjectId(7L);
        row.setBrandId(8L);
        row.setCompanyId(6L);
        row.setSourceMonth(sourceMonth);
        row.setTargetMonth(targetMonth);
        row.setCarryOverCount(count);
        row.setConsumedCount(0);
        row.setStatus("pending");
        row.setCarryOverPlanJson("[{\"accountId\":20,\"platform\":\"toutiao\"}]");
        return row;
    }
}
