package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.distribution.TargetContext;
import com.huanjing.geo.module.content.dto.SelfMediaPlatformQuickScheduleRequest;
import com.huanjing.geo.module.content.dto.SelfMediaPublishScheduleCreateRequest;
import com.huanjing.geo.module.content.dto.ThirdPartySubjectPoolPreviewResponse;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.ArticlePublishRecord;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleRequest;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.ArticlePublishRecordMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleRequestMapper;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformCapabilityContract;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformPublishChannel;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleMode;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleRules;
import com.huanjing.geo.module.content.vo.SelfMediaPlatformQuickScheduleResponse;
import com.huanjing.geo.module.content.vo.SelfMediaAutomationOverviewVO;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleCreateResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.extension.mapper.LocalAgentSessionMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

class SelfMediaPublishScheduleServiceTest {
    private SelfMediaPublishScheduleMapper scheduleMapper;
    private SelfMediaPublishScheduleRequestMapper requestMapper;
    private ArticleDraftMapper articleDraftMapper;
    private ArticlePublishRecordMapper articlePublishRecordMapper;
    private DistributionTaskMapper distributionTaskMapper;
    private SelfMediaAccountMapper accountMapper;
    private ProjectMapper projectMapper;
    private BrandMapper brandMapper;
    private BrowserEnvironmentService browserEnvironmentService;
    private SelfMediaScheduleCapabilityService scheduleCapabilityService;
    private SelfMediaPlatformScheduleAdapterRouter scheduleAdapterRouter;
    private SelfMediaPublishScheduleAlertService alertService;
    private SelfMediaPublishScheduleEnvironmentLockService environmentLockService;
    private ContentDistributionService contentDistributionService;
    private CompanyChannelQuotaService companyChannelQuotaService;
    private BrandAccessService brandAccessService;
    private SysUserMapper sysUserMapper;
    private LocalAgentSessionMapper localAgentSessionMapper;
    private BusinessCalendarService businessCalendarService;
    private ThirdPartySubjectRotationService thirdPartySubjectRotationService;
    private SelfMediaPublishScheduleService service;

    @BeforeEach
    void setUp() {
        scheduleMapper = mock(SelfMediaPublishScheduleMapper.class);
        requestMapper = mock(SelfMediaPublishScheduleRequestMapper.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
        articlePublishRecordMapper = mock(ArticlePublishRecordMapper.class);
        distributionTaskMapper = mock(DistributionTaskMapper.class);
        accountMapper = mock(SelfMediaAccountMapper.class);
        projectMapper = mock(ProjectMapper.class);
        brandMapper = mock(BrandMapper.class);
        browserEnvironmentService = mock(BrowserEnvironmentService.class);
        scheduleCapabilityService = mock(SelfMediaScheduleCapabilityService.class);
        scheduleAdapterRouter = mock(SelfMediaPlatformScheduleAdapterRouter.class);
        when(scheduleAdapterRouter.rules(anyString(), anyString()))
                .thenReturn(new SelfMediaPlatformScheduleRules(130, 120, 4));
        when(scheduleAdapterRouter.contract(anyString())).thenReturn(Optional.empty());
        when(scheduleAdapterRouter.platformsByChannel(SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION))
                .thenReturn(Set.of("toutiao", "baijiahao", "xiaohongshu", "zhihu"));
        when(scheduleAdapterRouter.platformsByChannel(SelfMediaPlatformPublishChannel.OFFICIAL_API))
                .thenReturn(Set.of("douyin", "wechat_mp"));
        alertService = mock(SelfMediaPublishScheduleAlertService.class);
        when(alertService.listOpenAlertsByScheduleIds(any())).thenReturn(Map.of());
        environmentLockService = mock(SelfMediaPublishScheduleEnvironmentLockService.class);
        contentDistributionService = mock(ContentDistributionService.class);
        companyChannelQuotaService = mock(CompanyChannelQuotaService.class);
        when(companyChannelQuotaService.selfMediaDistributionQuota(anyLong(), anyString()))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView("self_media:toutiao", "month", "2026-06", 0, 100));
        brandAccessService = mock(BrandAccessService.class);
        sysUserMapper = mock(SysUserMapper.class);
        localAgentSessionMapper = mock(LocalAgentSessionMapper.class);
        when(localAgentSessionMapper.countOnlineSessionsByOperator(anyLong(), any(), any())).thenReturn(1L);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser user = new SysUser();
        user.setId(99L);
        when(currentUserService.requireCurrentUser()).thenReturn(user);
        businessCalendarService = spy(new BusinessCalendarService(new ObjectMapper()));
        thirdPartySubjectRotationService = mock(ThirdPartySubjectRotationService.class);

        service = new SelfMediaPublishScheduleService(
                scheduleMapper,
                requestMapper,
                articleDraftMapper,
                articlePublishRecordMapper,
                distributionTaskMapper,
                accountMapper,
                projectMapper,
                brandMapper,
                browserEnvironmentService,
                scheduleCapabilityService,
                scheduleAdapterRouter,
                alertService,
                environmentLockService,
                contentDistributionService,
                companyChannelQuotaService,
                brandAccessService,
                currentUserService,
                sysUserMapper,
                localAgentSessionMapper,
                businessCalendarService,
                thirdPartySubjectRotationService,
                mock(ArticleTemplateAllocationService.class),
                mock(TemplatePerspectiveService.class),
                new ObjectMapper()
        );
    }

    @Test
    void createSchedules_duplicateRequestReturnsExistingSchedulesWithoutInserting() {
        SelfMediaPublishScheduleRequest existingRequest = new SelfMediaPublishScheduleRequest();
        existingRequest.setId(5L);
        existingRequest.setBrandId(8L);
        existingRequest.setRequestIdempotencyKey("same-key");
        when(requestMapper.selectByRequestKey(8L, "same-key")).thenReturn(existingRequest);

        SelfMediaPublishSchedule existingSchedule = new SelfMediaPublishSchedule();
        existingSchedule.setId(30L);
        existingSchedule.setRequestId(5L);
        existingSchedule.setArticleId(10L);
        existingSchedule.setSelfMediaAccountId(20L);
        existingSchedule.setStatus("pending");
        when(scheduleMapper.selectByRequestId(5L)).thenReturn(List.of(existingSchedule));

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(validRequest(), "same-key");

        assertEquals(5L, response.getRequestId());
        assertEquals(1, response.getExistingSchedules().size());
        assertEquals(30L, response.getExistingSchedules().get(0).getId());
        verify(requestMapper, never()).insert(any());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void createSchedules_rejectsAccountWithoutBrowserEnvironmentBinding() {
        prepareValidArticleAndAccount();
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(null);
        stubRequestInsert();

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(validRequest(), "new-key");

        assertTrue(response.getCreatedSchedules().isEmpty());
        assertEquals(1, response.getRejectedItems().size());
        assertEquals("ENVIRONMENT_ACCOUNT_BINDING_NOT_FOUND", response.getRejectedItems().get(0).getCode());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void createSchedules_rejectsWhenActiveScheduleAlreadyExists() {
        prepareValidArticleAndAccount();
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        when(scheduleMapper.selectActiveByBaseIdempotencyKey(anyString(), any())).thenReturn(existingActiveSchedule());
        stubRequestInsert();

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(validRequest(), "new-key");

        assertTrue(response.getCreatedSchedules().isEmpty());
        assertEquals(1, response.getRejectedItems().size());
        assertEquals("ACTIVE_SCHEDULE_EXISTS", response.getRejectedItems().get(0).getCode());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void createSchedules_rejectsWhenArticleHasActiveSelfMediaSchedule() {
        prepareValidArticleAndAccount();
        when(scheduleMapper.countActiveByArticleId(eq(10L), eq(null), anyList())).thenReturn(1L);
        stubRequestInsert();

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(validRequest(), "new-key");

        assertTrue(response.getCreatedSchedules().isEmpty());
        assertEquals(1, response.getRejectedItems().size());
        assertEquals("ARTICLE_SELF_MEDIA_SCHEDULE_ACTIVE", response.getRejectedItems().get(0).getCode());
        verify(browserEnvironmentService, never()).validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void createSystemSchedules_allowsWhenBrandActiveQueueFull() {
        prepareValidArticleAndAccount();
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        stubRequestInsert();
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(51L);
            return 1;
        });

        SelfMediaPublishScheduleCreateResponse response = service.createSystemSchedules(validRequest(), "new-key", 99L);

        assertEquals(1, response.getCreatedSchedules().size());
        assertTrue(response.getRejectedItems().isEmpty());
        verify(scheduleMapper, never()).countActiveByBrandId(anyLong(), anyList());
        verify(companyChannelQuotaService).reserveSelfMediaSchedules(anyLong(), anyList());
    }

    @Test
    void createSchedules_marksArticleDistributingWhenScheduleCreated() {
        ArticleDraft article = article();
        when(articleDraftMapper.selectById(10L)).thenReturn(article);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(brandMapper.selectById(8L)).thenReturn(brand());
        when(accountMapper.selectById(20L)).thenReturn(account());
        when(scheduleCapabilityService.readiness("toutiao", SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null, null));
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        stubRequestInsert();
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(51L);
            return 1;
        });

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(validRequest(), "new-key");

        assertEquals(1, response.getCreatedSchedules().size());
        assertEquals("distributing", article.getStatus());
        verify(articleDraftMapper).updateById(article);
    }

    @Test
    void createSchedules_rejectsWhenPlatformCapabilityNotVerified() {
        prepareValidArticleAndAccount();
        when(scheduleCapabilityService.readiness("toutiao", SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(
                        false,
                        "PLATFORM_CAPABILITY_UNVERIFIED",
                        "平台定时发布能力尚未验证",
                        null
                ));
        stubRequestInsert();

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(validRequest(), "new-key");

        assertTrue(response.getCreatedSchedules().isEmpty());
        assertEquals(1, response.getRejectedItems().size());
        assertEquals("PLATFORM_CAPABILITY_UNVERIFIED", response.getRejectedItems().get(0).getCode());
        assertEquals("全自动排期 > 平台能力验证", response.getRejectedItems().get(0).getSettingPath());
        verify(browserEnvironmentService, never()).validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void createSchedules_rejectsCoverRequiredPlatformWhenArticleHasNoCover() {
        ArticleDraft article = article();
        article.setCoverImageUrl(null);
        SelfMediaAccount account = account();
        account.setPlatform("baijiahao");
        account.setPlatformAccountId("1867055852901021");
        BrowserEnvironmentAccount binding = binding();
        binding.setPlatform("baijiahao");

        when(articleDraftMapper.selectById(10L)).thenReturn(article);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(brandMapper.selectById(8L)).thenReturn(brand());
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(scheduleCapabilityService.readiness("baijiahao", SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null, null));
        when(scheduleAdapterRouter.contract("baijiahao")).thenReturn(Optional.of(new SelfMediaPlatformCapabilityContract(
                "baijiahao",
                "百家号",
                SelfMediaPlatformPublishChannel.ADSPOWER_AUTOMATION,
                SelfMediaPlatformScheduleMode.PLATFORM_NATIVE,
                new SelfMediaPlatformScheduleRules(0, 0, 2),
                true,
                false,
                false,
                true
        )));
        stubRequestInsert();
        SelfMediaPublishScheduleCreateRequest request = validRequest();

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(request, "new-key");

        assertTrue(response.getCreatedSchedules().isEmpty());
        assertEquals(1, response.getRejectedItems().size());
        assertEquals("ARTICLE_COVER_REQUIRED", response.getRejectedItems().get(0).getCode());
        assertEquals("文章详情 > 文章封面", response.getRejectedItems().get(0).getSettingPath());
        verify(browserEnvironmentService, never()).validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void createSchedules_rejectsBaijiahaoAccountWithoutAppId() {
        ArticleDraft article = article();
        SelfMediaAccount account = account();
        account.setPlatform("baijiahao");
        account.setPlatformAccountId(null);

        when(articleDraftMapper.selectById(10L)).thenReturn(article);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(brandMapper.selectById(8L)).thenReturn(brand());
        when(accountMapper.selectById(20L)).thenReturn(account);
        stubRequestInsert();

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(validRequest(), "new-key");

        assertTrue(response.getCreatedSchedules().isEmpty());
        assertEquals(1, response.getRejectedItems().size());
        assertEquals("BAIJIAHAO_APP_ID_REQUIRED", response.getRejectedItems().get(0).getCode());
        assertEquals("品牌详情 > 自媒体账号 > 百家号 ID", response.getRejectedItems().get(0).getSettingPath());
        verify(scheduleCapabilityService, never()).readiness(anyString(), anyString());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void createSchedules_rejectsWhenSelfMediaChannelQuotaExhausted() {
        prepareValidArticleAndAccount();
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        when(companyChannelQuotaService.selfMediaDistributionQuota(6L, "toutiao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView("self_media:toutiao", "month", "2026-06", 3, 3));
        stubRequestInsert();

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(validRequest(), "new-key");

        assertTrue(response.getCreatedSchedules().isEmpty());
        assertEquals(1, response.getRejectedItems().size());
        assertEquals("CHANNEL_QUOTA_EXHAUSTED", response.getRejectedItems().get(0).getCode());
        assertEquals("客户套餐 > 渠道额度", response.getRejectedItems().get(0).getSettingPath());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void createSchedules_rejectsToutiaoPlatformScheduleWhenTimeTooClose() {
        prepareValidArticleAndAccount();
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        stubRequestInsert();
        SelfMediaPublishScheduleCreateRequest request = validRequest();
        request.setWindowStart(LocalDateTime.now().plusMinutes(30));
        request.setWindowEnd(LocalDateTime.now().plusMinutes(45));

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(request, "new-key");

        assertTrue(response.getCreatedSchedules().isEmpty());
        assertEquals(1, response.getRejectedItems().size());
        assertEquals("PLATFORM_SCHEDULE_TIME_TOO_CLOSE", response.getRejectedItems().get(0).getCode());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void createSchedules_setsToutiaoPlatformScheduleAttemptBeforeTwoHourLimit() {
        prepareValidArticleAndAccount();
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        stubRequestInsert();
        SelfMediaPublishScheduleCreateRequest request = validRequest();
        LocalDateTime plannedAt = LocalDateTime.now().plusHours(3).withSecond(0).withNano(0);
        request.setWindowStart(plannedAt);
        request.setWindowEnd(plannedAt.plusMinutes(5));
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(51L);
            return 1;
        });

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(request, "new-key");

        assertEquals(1, response.getCreatedSchedules().size());
        ArgumentCaptor<SelfMediaPublishSchedule> captor = ArgumentCaptor.forClass(SelfMediaPublishSchedule.class);
        verify(scheduleMapper).insert(captor.capture());
        assertTrue(!captor.getValue().getNextAttemptAt().isBefore(plannedAt.minusMinutes(130)));
        assertTrue(isInBusinessAttemptWindow(captor.getValue().getNextAttemptAt()));
        assertEquals(4, captor.getValue().getMaxAttempts());
        assertEquals("article title for check", captor.getValue().getPublishCheckTitle());
        assertEquals("https://cdn.example.test/cover.png", captor.getValue().getPublishCheckCoverUrl());
        assertEquals("阜阳", captor.getValue().getPublishCheckLocationName());
        assertTrue(captor.getValue().getPublishCheckFingerprint().matches("[0-9a-f]{64}"));
        ArgumentCaptor<List<CompanyChannelQuotaService.SelfMediaScheduleQuotaReservation>> quotaCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(companyChannelQuotaService).reserveSelfMediaSchedules(eq(6L), quotaCaptor.capture());
        assertEquals(1, quotaCaptor.getValue().size());
        assertEquals(51L, quotaCaptor.getValue().get(0).scheduleId());
        assertEquals(7L, quotaCaptor.getValue().get(0).projectId());
        assertEquals("toutiao", quotaCaptor.getValue().get(0).platform());
    }

    @Test
    void createSchedules_usesExplicitExecutionWindowAsFillTime() {
        prepareValidArticleAndAccount();
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        stubRequestInsert();
        SelfMediaPublishScheduleCreateRequest request = validRequest();
        LocalDateTime executionAt = LocalDateTime.now().plusDays(3).withHour(9).withMinute(15).withSecond(0).withNano(0);
        LocalDateTime plannedAt = executionAt.plusMinutes(130);
        doReturn(List.of(new BusinessCalendarService.BusinessDay(
                executionAt.toLocalDate(),
                0,
                "测试工作日",
                1,
                false,
                List.of(new BusinessCalendarService.PublishWindow(
                        "test",
                        LocalTime.of(9, 15),
                        LocalTime.of(11, 30),
                        LocalTime.of(9, 15)
                ))
        ))).when(businessCalendarService).publishDays(any(), eq(false));
        request.setWindowStart(plannedAt);
        request.setWindowEnd(plannedAt);
        request.setExecutionWindowStart(executionAt);
        request.setExecutionWindowEnd(executionAt);
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(51L);
            return 1;
        });

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(request, "new-key");

        assertEquals(1, response.getCreatedSchedules().size());
        ArgumentCaptor<SelfMediaPublishSchedule> captor = ArgumentCaptor.forClass(SelfMediaPublishSchedule.class);
        verify(scheduleMapper).insert(captor.capture());
        assertEquals(plannedAt, captor.getValue().getPlannedPublishAt());
        assertEquals(plannedAt, captor.getValue().getPlatformScheduledAt());
        assertEquals(executionAt, captor.getValue().getNextAttemptAt());
    }

    @Test
    void createSchedules_acceptsSinglePointScheduleWindow() {
        prepareValidArticleAndAccount();
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        stubRequestInsert();
        SelfMediaPublishScheduleCreateRequest request = validRequest();
        LocalDateTime plannedAt = LocalDateTime.now().plusHours(3).withSecond(0).withNano(0);
        request.setWindowStart(plannedAt);
        request.setWindowEnd(plannedAt);
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(53L);
            return 1;
        });

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(request, "new-key");

        assertEquals(1, response.getCreatedSchedules().size());
        ArgumentCaptor<SelfMediaPublishSchedule> captor = ArgumentCaptor.forClass(SelfMediaPublishSchedule.class);
        verify(scheduleMapper).insert(captor.capture());
        assertEquals(plannedAt, captor.getValue().getPlannedPublishAt());
        assertEquals(plannedAt, captor.getValue().getPlatformScheduledAt());
    }

    @Test
    void createSchedulesReservesCreatedRowsInOneBatch() {
        ArticleDraft first = article();
        ArticleDraft second = article();
        second.setId(11L);
        when(articleDraftMapper.selectById(10L)).thenReturn(first);
        when(articleDraftMapper.selectById(11L)).thenReturn(second);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(brandMapper.selectById(8L)).thenReturn(brand());
        when(accountMapper.selectById(20L)).thenReturn(account());
        when(scheduleCapabilityService.readiness("toutiao", SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null, null));
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        stubRequestInsert();
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(row.getArticleId() == 10L ? 61L : 62L);
            return 1;
        });
        SelfMediaPublishScheduleCreateRequest request = validRequest();
        request.setArticleIds(List.of(10L, 11L));
        request.setWindowEnd(request.getWindowStart().plusHours(1));

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(request, "batch-key");

        assertEquals(2, response.getCreatedSchedules().size());
        ArgumentCaptor<List<CompanyChannelQuotaService.SelfMediaScheduleQuotaReservation>> quotaCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(companyChannelQuotaService).reserveSelfMediaSchedules(eq(6L), quotaCaptor.capture());
        assertEquals(2, quotaCaptor.getValue().size());
        assertEquals(List.of(61L, 62L), quotaCaptor.getValue().stream()
                .map(CompanyChannelQuotaService.SelfMediaScheduleQuotaReservation::scheduleId)
                .toList());
    }

    @Test
    void createSystemSchedulesUsesProvidedOperatorWithoutBrandAccessCheck() {
        prepareValidArticleAndAccount();
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        stubRequestInsert();
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(52L);
            return 1;
        });

        SelfMediaPublishScheduleCreateResponse response = service.createSystemSchedules(validRequest(), "system-key", 66L);

        assertEquals(1, response.getCreatedSchedules().size());
        ArgumentCaptor<SelfMediaPublishSchedule> captor = ArgumentCaptor.forClass(SelfMediaPublishSchedule.class);
        verify(scheduleMapper).insert(captor.capture());
        assertEquals(66L, captor.getValue().getCreatedBy());
        assertEquals(66L, captor.getValue().getUpdatedBy());
        verify(brandAccessService, never()).requireBrandAccess(anyLong(), anyLong(), any());
    }

    @Test
    void previewPlatformQuickScheduleRejectsExplicitOtherPlatformArticle() {
        ArticleDraft article = article();
        article.setContentStyle("xiaohongshu_note");
        when(articleDraftMapper.selectById(10L)).thenReturn(article);
        when(projectMapper.selectById(7L)).thenReturn(project());

        SelfMediaPlatformQuickScheduleResponse response = service.previewPlatformQuickSchedule(quickRequest("toutiao", false));

        assertEquals("article_type_mismatch", response.getAction());
        assertEquals("ARTICLE_PLATFORM_MISMATCH", response.getCode());
        verify(accountMapper, never()).selectOne(any());
    }

    @Test
    void previewPlatformQuickScheduleAllowsWechatArticleForWechatMpAlias() {
        ArticleDraft article = article();
        article.setChannelGroupCode("self_media");
        article.setChannelSubCode("wechat");
        article.setContentStyle("wechat");
        when(articleDraftMapper.selectById(10L)).thenReturn(article);
        when(projectMapper.selectById(7L)).thenReturn(project());

        SelfMediaPlatformQuickScheduleResponse response = service.previewPlatformQuickSchedule(quickRequest("wechat_mp", false));

        assertEquals("account_or_environment_not_ready", response.getAction());
        assertEquals("SELF_MEDIA_ACCOUNT_NOT_FOUND", response.getCode());
        assertEquals("wechat_mp", response.getPlatform());
        verify(accountMapper).selectOne(any());
    }

    @Test
    void previewPlatformQuickScheduleNormalizesSelfMediaPlatformAliases() {
        ArticleDraft article = article();
        article.setChannelGroupCode("self_media");
        article.setChannelSubCode("douyin_image_text");
        article.setContentStyle("douyin_image_text");
        when(articleDraftMapper.selectById(10L)).thenReturn(article);
        when(projectMapper.selectById(7L)).thenReturn(project());

        SelfMediaPlatformQuickScheduleResponse response = service.previewPlatformQuickSchedule(quickRequest("douyin_image_text", false));

        assertEquals("account_or_environment_not_ready", response.getAction());
        assertEquals("SELF_MEDIA_ACCOUNT_NOT_FOUND", response.getCode());
        assertEquals("douyin", response.getPlatform());
        verify(accountMapper).selectOne(any());
    }

    @Test
    void previewPlatformQuickScheduleRequiresReplacementWhenMonthlyQuotaAlreadyPlanned() {
        prepareValidArticleAndAccount();
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        when(companyChannelQuotaService.selfMediaDistributionQuota(6L, "toutiao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView("self_media:toutiao", "month", "2026-06", 3, 3));
        SelfMediaPublishSchedule replaceable = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        replaceable.setId(70L);
        replaceable.setPlannedPublishAt(LocalDateTime.now().plusHours(3));
        replaceable.setNextAttemptAt(LocalDateTime.now().plusMinutes(50));
        when(scheduleMapper.selectNextReplaceablePendingByBrandPlatformAndPeriod(anyLong(), eq("toutiao"), any(), any(), any()))
                .thenReturn(replaceable);

        SelfMediaPlatformQuickScheduleResponse response = service.previewPlatformQuickSchedule(quickRequest("toutiao", false));

        assertEquals("replace_required", response.getAction());
        assertEquals(70L, response.getReplaceScheduleId());
        assertEquals("该平台本月文章已做排期处理，若继续发布将替换已排期文章，是否继续？", response.getMessage());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void dispatchPlatformQuickScheduleRejectsWhenBrandActiveQueueFull() {
        prepareValidArticleAndAccount();
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        when(scheduleMapper.countActiveByBrandId(eq(8L), anyList())).thenReturn(10L);

        SelfMediaPlatformQuickScheduleResponse response = service.dispatchPlatformQuickSchedule(quickRequest("toutiao", false), "dispatch-key");

        assertEquals("queue_full", response.getAction());
        assertEquals("BRAND_SELF_MEDIA_QUEUE_FULL", response.getCode());
        verify(requestMapper, never()).insert(any());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void createPlatformQuickScheduleReplacesPendingScheduleAndCreatesNewOne() {
        prepareValidArticleAndAccount();
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        when(companyChannelQuotaService.selfMediaDistributionQuota(6L, "toutiao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView("self_media:toutiao", "month", "2026-06", 3, 3));
        SelfMediaPublishSchedule replaceable = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        replaceable.setId(70L);
        replaceable.setPlannedPublishAt(LocalDateTime.now().plusHours(3));
        replaceable.setNextAttemptAt(LocalDateTime.now().plusMinutes(50));
        when(scheduleMapper.selectNextReplaceablePendingByBrandPlatformAndPeriod(anyLong(), eq("toutiao"), any(), any(), any()))
                .thenReturn(replaceable);
        when(scheduleMapper.selectById(70L)).thenReturn(replaceable);
        when(scheduleMapper.cancelReplaceablePendingSchedule(eq(70L), anyLong(), eq("toutiao"), any(), any(), any()))
                .thenReturn(1);
        when(scheduleMapper.selectBrandActiveScheduleSlots(anyLong(), any(), any(), any())).thenReturn(List.of());
        stubRequestInsert();
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(71L);
            return 1;
        });

        SelfMediaPlatformQuickScheduleResponse response = service.createPlatformQuickSchedule(quickRequest("toutiao", true), "quick-key");

        assertEquals("created", response.getAction());
        assertEquals(1, response.getCreateResponse().getCreatedSchedules().size());
        verify(scheduleMapper).cancelReplaceablePendingSchedule(eq(70L), eq(8L), eq("toutiao"), any(), any(), any());
        verify(companyChannelQuotaService).refundSelfMediaSchedule(70L);
        verify(companyChannelQuotaService).reserveSelfMediaSchedule(eq(6L), eq(7L), eq("toutiao"), eq(71L));
    }

    @Test
    void dispatchPlatformQuickScheduleSafelyReplacesPendingScheduleAndCreatesProtectedTask() {
        prepareValidArticleAndAccount();
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        SelfMediaPublishSchedule replaceable = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        replaceable.setId(72L);
        replaceable.setPlannedPublishAt(LocalDateTime.now().plusHours(4));
        replaceable.setNextAttemptAt(LocalDateTime.now().plusMinutes(40));
        when(scheduleMapper.selectSafeReplaceablePendingByBrandPlatformAndPeriod(
                anyLong(), eq("toutiao"), any(), any(), any(), any()
        )).thenReturn(replaceable);
        when(scheduleMapper.selectById(72L)).thenReturn(replaceable);
        when(scheduleMapper.cancelSafeReplaceablePendingSchedule(
                eq(72L), eq(8L), eq("toutiao"), any(), any(), any(), any()
        )).thenReturn(1);
        SelfMediaPublishSchedule protectedSlot = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        LocalDateTime occupied = LocalDateTime.now().plusMinutes(5).withSecond(0).withNano(0);
        protectedSlot.setId(80L);
        protectedSlot.setNextAttemptAt(occupied);
        protectedSlot.setPlannedPublishAt(occupied.plusHours(2));
        when(scheduleMapper.selectBrandActiveScheduleSlots(anyLong(), any(), any(), any()))
                .thenReturn(List.of(), List.of(), List.of(protectedSlot), List.of());
        stubRequestInsert();
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(73L);
            assertTrue(!row.getNextAttemptAt().isBefore(occupied.plusMinutes(3)));
            return 1;
        });

        SelfMediaPlatformQuickScheduleResponse response = service.dispatchPlatformQuickSchedule(quickRequest("toutiao", false), "dispatch-key");

        assertEquals("created", response.getAction());
        assertEquals("QUICK_DISPATCH_CREATED", response.getCode());
        assertEquals(72L, response.getReplaceScheduleId());
        verify(scheduleMapper).cancelSafeReplaceablePendingSchedule(eq(72L), eq(8L), eq("toutiao"), any(), any(), any(), any());
        verify(companyChannelQuotaService).refundSelfMediaSchedule(72L);
        verify(companyChannelQuotaService).reserveSelfMediaSchedule(eq(6L), eq(7L), eq("toutiao"), eq(73L));
    }

    @Test
    void dispatchPlatformQuickScheduleUsesBufferedAttemptTimeInsteadOfBusinessWindow() {
        prepareValidArticleAndAccount();
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        when(scheduleMapper.selectBrandActiveScheduleSlots(anyLong(), any(), any(), any())).thenReturn(List.of());
        stubRequestInsert();
        LocalDateTime clickedAt = LocalDateTime.now();
        LocalDateTime earliestBufferedAttempt = clickedAt.plusMinutes(1).withSecond(0).withNano(0);
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(73L);
            assertTrue(!row.getNextAttemptAt().isBefore(earliestBufferedAttempt));
            assertTrue(row.getNextAttemptAt().isBefore(clickedAt.plusMinutes(3)));
            return 1;
        });

        SelfMediaPlatformQuickScheduleResponse response = service.dispatchPlatformQuickSchedule(quickRequest("toutiao", false), "dispatch-key");

        assertEquals("created", response.getAction());
        assertTrue(!response.getNextAttemptAt().isBefore(earliestBufferedAttempt));
        assertTrue(response.getNextAttemptAt().isBefore(clickedAt.plusMinutes(3)));
        verify(companyChannelQuotaService).reserveSelfMediaSchedule(eq(6L), eq(7L), eq("toutiao"), eq(73L));
    }

    @Test
    void dispatchPlatformQuickScheduleCreatesDouyinOfficialApiScheduleWithoutBrowserEnvironment() {
        ArticleDraft article = article();
        when(articleDraftMapper.selectById(10L)).thenReturn(article);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(brandMapper.selectById(8L)).thenReturn(brand());
        SelfMediaAccount douyinAccount = account();
        douyinAccount.setId(21L);
        douyinAccount.setPlatform("douyin");
        when(accountMapper.selectOne(any())).thenReturn(douyinAccount);
        when(accountMapper.selectById(21L)).thenReturn(douyinAccount);
        when(scheduleCapabilityService.readiness("douyin", SelfMediaPublishScheduleConstants.STRATEGY_BACKEND_DELAYED_PUBLISH))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null, null));
        when(companyChannelQuotaService.selfMediaDistributionQuota(6L, "douyin"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView("self_media:douyin", "month", "2026-06", 0, 100));
        when(scheduleAdapterRouter.contract("douyin")).thenReturn(Optional.of(new SelfMediaPlatformCapabilityContract(
                "douyin",
                "抖音图文",
                SelfMediaPlatformPublishChannel.OFFICIAL_API,
                SelfMediaPlatformScheduleMode.BACKEND_DELAYED,
                SelfMediaPlatformScheduleRules.defaults(),
                false,
                false,
                false,
                true
        )));
        when(scheduleMapper.selectBrandActiveScheduleSlots(anyLong(), any(), any(), any())).thenReturn(List.of());
        stubRequestInsert();
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(74L);
            assertEquals("douyin", row.getPlatform());
            assertNull(row.getBrowserEnvironmentId());
            assertNull(row.getBrowserEnvironmentAccountId());
            return 1;
        });

        SelfMediaPlatformQuickScheduleResponse response = service.dispatchPlatformQuickSchedule(quickRequest("douyin", false), "dispatch-douyin-key");

        assertEquals("created", response.getAction());
        assertEquals("QUICK_DISPATCH_CREATED", response.getCode());
        assertEquals(21L, response.getSelfMediaAccountId());
        verify(browserEnvironmentService, never()).validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean());
        verify(companyChannelQuotaService).reserveSelfMediaSchedule(eq(6L), eq(7L), eq("douyin"), eq(74L));
    }

    @Test
    void dispatchPlatformQuickScheduleCreatesWechatOfficialApiScheduleWithoutBrowserEnvironment() {
        ArticleDraft article = article();
        when(articleDraftMapper.selectById(10L)).thenReturn(article);
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(brandMapper.selectById(8L)).thenReturn(brand());
        SelfMediaAccount wechatAccount = account();
        wechatAccount.setId(22L);
        wechatAccount.setPlatform("wechat_mp");
        when(accountMapper.selectOne(any())).thenReturn(wechatAccount);
        when(accountMapper.selectById(22L)).thenReturn(wechatAccount);
        when(scheduleCapabilityService.readiness("wechat_mp", SelfMediaPublishScheduleConstants.STRATEGY_BACKEND_DELAYED_PUBLISH))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null, null));
        when(companyChannelQuotaService.selfMediaDistributionQuota(6L, "wechat_mp"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView("self_media:wechat_mp", "month", "2026-06", 0, 100));
        when(scheduleAdapterRouter.contract("wechat_mp")).thenReturn(Optional.of(new SelfMediaPlatformCapabilityContract(
                "wechat_mp",
                "微信公众号",
                SelfMediaPlatformPublishChannel.OFFICIAL_API,
                SelfMediaPlatformScheduleMode.BACKEND_DELAYED,
                SelfMediaPlatformScheduleRules.defaults(),
                false,
                false,
                false,
                true
        )));
        when(scheduleMapper.selectBrandActiveScheduleSlots(anyLong(), any(), any(), any())).thenReturn(List.of());
        stubRequestInsert();
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(75L);
            assertEquals("wechat_mp", row.getPlatform());
            assertNull(row.getBrowserEnvironmentId());
            assertNull(row.getBrowserEnvironmentAccountId());
            return 1;
        });

        SelfMediaPlatformQuickScheduleResponse response = service.dispatchPlatformQuickSchedule(quickRequest("wechat_mp", false), "dispatch-wechat-key");

        assertEquals("created", response.getAction());
        assertEquals("QUICK_DISPATCH_CREATED", response.getCode());
        assertEquals(22L, response.getSelfMediaAccountId());
        verify(browserEnvironmentService, never()).validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean());
        verify(companyChannelQuotaService).reserveSelfMediaSchedule(eq(6L), eq(7L), eq("wechat_mp"), eq(75L));
    }

    @Test
    void dispatchPlatformQuickScheduleUsesSafeReplacementCandidateWhenQuotaRequiresReplacement() {
        prepareValidArticleAndAccount();
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        when(companyChannelQuotaService.selfMediaDistributionQuota(6L, "toutiao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView("self_media:toutiao", "month", "2026-06", 3, 3));
        SelfMediaPublishSchedule unsafePreviewCandidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        unsafePreviewCandidate.setId(70L);
        unsafePreviewCandidate.setPlannedPublishAt(LocalDateTime.now().plusHours(2));
        unsafePreviewCandidate.setNextAttemptAt(LocalDateTime.now().plusMinutes(5));
        when(scheduleMapper.selectNextReplaceablePendingByBrandPlatformAndPeriod(anyLong(), eq("toutiao"), any(), any(), any()))
                .thenReturn(unsafePreviewCandidate);
        SelfMediaPublishSchedule safeCandidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        safeCandidate.setId(72L);
        safeCandidate.setPlannedPublishAt(LocalDateTime.now().plusHours(4));
        safeCandidate.setNextAttemptAt(LocalDateTime.now().plusMinutes(40));
        when(scheduleMapper.selectSafeReplaceablePendingByBrandPlatformAndPeriod(
                anyLong(), eq("toutiao"), any(), any(), any(), any()
        )).thenReturn(safeCandidate);
        when(scheduleMapper.selectById(72L)).thenReturn(safeCandidate);
        when(scheduleMapper.cancelSafeReplaceablePendingSchedule(
                eq(72L), eq(8L), eq("toutiao"), any(), any(), any(), any()
        )).thenReturn(1);
        when(scheduleMapper.selectBrandActiveScheduleSlots(anyLong(), any(), any(), any())).thenReturn(List.of());
        stubRequestInsert();
        when(scheduleMapper.insert(any(SelfMediaPublishSchedule.class))).thenAnswer(invocation -> {
            SelfMediaPublishSchedule row = invocation.getArgument(0);
            row.setId(73L);
            return 1;
        });

        SelfMediaPlatformQuickScheduleResponse response = service.dispatchPlatformQuickSchedule(quickRequest("toutiao", false), "dispatch-key");

        assertEquals("created", response.getAction());
        assertEquals(72L, response.getReplaceScheduleId());
        verify(scheduleMapper).cancelSafeReplaceablePendingSchedule(eq(72L), eq(8L), eq("toutiao"), any(), any(), any(), any());
        verify(scheduleMapper, never()).cancelSafeReplaceablePendingSchedule(eq(70L), anyLong(), eq("toutiao"), any(), any(), any(), any());
        verify(companyChannelQuotaService).refundSelfMediaSchedule(72L);
    }

    @Test
    void createPlatformQuickScheduleStopsWhenReplaceTargetWasClaimed() {
        prepareValidArticleAndAccount();
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        when(companyChannelQuotaService.selfMediaDistributionQuota(6L, "toutiao"))
                .thenReturn(new CompanyChannelQuotaService.DistributionQuotaView("self_media:toutiao", "month", "2026-06", 3, 3));
        SelfMediaPublishSchedule replaceable = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        replaceable.setId(70L);
        replaceable.setPlannedPublishAt(LocalDateTime.now().plusHours(3));
        when(scheduleMapper.selectNextReplaceablePendingByBrandPlatformAndPeriod(anyLong(), eq("toutiao"), any(), any(), any()))
                .thenReturn(replaceable);
        when(scheduleMapper.selectById(70L)).thenReturn(replaceable);
        when(scheduleMapper.cancelReplaceablePendingSchedule(eq(70L), anyLong(), eq("toutiao"), any(), any(), any()))
                .thenReturn(0);

        assertThrows(BizException.class, () -> service.createPlatformQuickSchedule(quickRequest("toutiao", true), "quick-key"));

        verify(scheduleMapper, never()).insert(any(SelfMediaPublishSchedule.class));
        verify(companyChannelQuotaService, never()).refundSelfMediaSchedule(anyLong());
    }

    @Test
    void previewPlatformQuickScheduleUsesBrandLevelSafetyIntervalAcrossProjects() {
        prepareValidArticleAndAccount();
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class), anyBoolean())).thenReturn(binding());
        LocalDateTime occupied = LocalDateTime.now().plusMinutes(1).withSecond(0).withNano(0);
        SelfMediaPublishSchedule slot = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        slot.setId(80L);
        slot.setNextAttemptAt(occupied);
        slot.setPlannedPublishAt(occupied.plusHours(2));
        when(scheduleMapper.selectBrandActiveScheduleSlots(anyLong(), any(), any(), any())).thenReturn(List.of(slot));

        SelfMediaPlatformQuickScheduleResponse response = service.previewPlatformQuickSchedule(quickRequest("toutiao", false));

        assertEquals("ready", response.getAction());
        assertTrue(!response.getNextAttemptAt().isBefore(occupied.plusMinutes(3)));
        assertEquals(3, response.getBrandSafetyIntervalMinutes());
    }

    @Test
    void createSchedules_rejectsSemiAutoStrategy() {
        SelfMediaPublishScheduleCreateRequest request = validRequest();
        request.setScheduleStrategy(SelfMediaPublishScheduleConstants.STRATEGY_SEMI_AUTO);

        assertThrows(RuntimeException.class, () -> service.createSchedules(request, "new-key"));

        verify(scheduleCapabilityService, never()).readiness(anyString());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void pageSchedulesWithoutBrandScopesToAccessibleBrands() {
        when(brandAccessService.listAccessibleBrandIds(99L, BrandAccessAction.OPERATE)).thenReturn(List.of(8L, 9L));
        SelfMediaPublishSchedule row = new SelfMediaPublishSchedule();
        row.setId(30L);
        row.setBrandId(8L);
        row.setArticleId(10L);
        row.setSelfMediaAccountId(20L);
        row.setPlatform("toutiao");
        row.setStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        Page<SelfMediaPublishSchedule> mapperPage = new Page<>(1, 20, 1);
        mapperPage.setRecords(List.of(row));
        ArticleDraft article = article();
        when(scheduleMapper.selectPage(any(), any())).thenReturn(mapperPage);
        when(articleDraftMapper.selectById(10L)).thenReturn(article);

        Page<SelfMediaPublishScheduleVO> response = service.pageSchedules(
                null, null, "toutiao", SelfMediaPublishScheduleConstants.STATUS_PENDING, null, null, null, null, null, 1L, 20L);

        assertEquals(1, response.getTotal());
        assertEquals(30L, response.getRecords().get(0).getId());
        assertEquals("distributing", article.getStatus());
        verify(articleDraftMapper).updateById(article);
        verify(brandAccessService).listAccessibleBrandIds(99L, BrandAccessAction.OPERATE);
        verify(brandAccessService, never()).requireBrandAccess(anyLong(), anyLong(), any());
        verify(scheduleMapper).selectPage(any(), any());
    }

    @Test
    void pageSchedulesWithoutAccessibleBrandReturnsEmptyPage() {
        when(brandAccessService.listAccessibleBrandIds(99L, BrandAccessAction.OPERATE)).thenReturn(List.of());

        Page<SelfMediaPublishScheduleVO> response = service.pageSchedules(null, null, null, null, null, null, null, null, null, 1L, 20L);

        assertEquals(0, response.getTotal());
        assertTrue(response.getRecords().isEmpty());
        verify(scheduleMapper, never()).selectPage(any(), any());
    }

    @Test
    void cancel_pendingScheduleMarksCancelled() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        row.setId(90L);
        when(scheduleMapper.selectById(90L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.cancel(90L, "operator cancel");

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_CANCELLED, response.getStatus());
        assertEquals("CANCELLED_BY_OPERATOR", response.getFailureCode());
        verify(scheduleMapper).updateById(row);
        verify(environmentLockService).release(90L);
    }

    @Test
    void cancel_scheduledScheduleRequiresPlatformCancelConfirmation() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_SCHEDULED);
        row.setId(91L);
        when(scheduleMapper.selectById(91L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.cancel(91L, "cancel after submitted");

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_CANCEL_PENDING_PLATFORM, response.getStatus());
        assertEquals("CANCELLED_BY_OPERATOR", response.getFailureCode());
        verify(scheduleMapper).updateById(row);
        verify(environmentLockService).release(91L);
    }

    @Test
    void confirmPlatformCancelled_closesCancelPendingSchedule() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_CANCEL_PENDING_PLATFORM);
        row.setId(92L);
        when(scheduleMapper.selectById(92L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.confirmPlatformCancelled(92L, "done");

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_CANCELLED, response.getStatus());
        assertEquals("PLATFORM_CANCEL_CONFIRMED", response.getFailureCode());
        verify(scheduleMapper).updateById(row);
        verify(companyChannelQuotaService).refundSelfMediaSchedule(92L);
        verify(environmentLockService).release(92L);
    }

    @Test
    void confirmPublished_marksPublishUnknownAsConfirmed() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN);
        row.setId(93L);
        when(scheduleMapper.selectById(93L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.confirmPublished(93L, "https://example.test/post/1");

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED, response.getStatus());
        assertEquals("https://example.test/post/1", response.getPlatformPublishedUrl());
        verify(scheduleMapper).updateById(row);
        verify(companyChannelQuotaService).confirmSelfMediaSchedule(93L);
        verify(environmentLockService).release(93L);
    }

    @Test
    void confirmPublished_syncsArticlePublishRecord() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN);
        row.setId(93L);
        row.setPlatform("zhihu");
        row.setPlatformPublishId("2055333874897511162");
        ArticleDraft article = article();
        when(scheduleMapper.selectById(93L)).thenReturn(row);
        when(articleDraftMapper.selectById(10L)).thenReturn(article);

        service.confirmPublished(93L, "https://zhuanlan.zhihu.com/p/2055333874897511162");

        ArgumentCaptor<ArticlePublishRecord> recordCaptor = ArgumentCaptor.forClass(ArticlePublishRecord.class);
        verify(articlePublishRecordMapper).insert(recordCaptor.capture());
        ArticlePublishRecord record = recordCaptor.getValue();
        assertEquals(10L, record.getArticleId());
        assertEquals(7L, record.getProjectId());
        assertEquals("self_media_publish_schedule", record.getSourceType());
        assertEquals(93L, record.getSourceId());
        assertEquals("self_media", record.getTargetKind());
        assertEquals("zhihu", record.getTargetChannel());
        assertEquals("https://zhuanlan.zhihu.com/p/2055333874897511162", record.getPublishedUrl());
        assertEquals("public_url", record.getUrlQuality());
        assertEquals("self_media_publish_schedule.platform_published_url", record.getUrlSource());
        assertEquals("2055333874897511162", record.getPlatformPublishId());
        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED, record.getPublishStatus());
        assertNotNull(record.getPublishedAt());
        assertNotNull(record.getVerifiedAt());
    }

    @Test
    void confirmPublishFailed_marksPublishUnknownAsFailed() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN);
        row.setId(94L);
        when(scheduleMapper.selectById(94L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.confirmPublishFailed(94L, null, "not found on platform");

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED, response.getStatus());
        assertEquals("PUBLISH_RESULT_MANUAL_FAILED", response.getFailureCode());
        assertEquals("not found on platform", response.getFailureMessage());
        verify(scheduleMapper).updateById(row);
        verify(companyChannelQuotaService).refundSelfMediaSchedule(94L);
        verify(environmentLockService).release(94L);
    }

    @Test
    void markClaimedPublishFailedRefundsScheduleQuota() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT);
        row.setId(119L);
        when(scheduleMapper.selectById(119L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.markClaimedPublishFailed(
                119L,
                "PLATFORM_REJECTED",
                "平台审核未通过",
                "{\"status\":\"rejected\"}"
        );

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED, response.getStatus());
        assertEquals("PLATFORM_REJECTED", response.getFailureCode());
        assertEquals("平台审核未通过", response.getFailureMessage());
        verify(scheduleMapper).updateById(row);
        verify(companyChannelQuotaService).refundSelfMediaSchedule(119L);
        verify(environmentLockService).release(119L);
    }

    @Test
    void claimNext_usesAtomicUpdateAndReturnsClaimedRow() {
        SelfMediaPublishSchedule candidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        candidate.setId(95L);
        candidate.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        SelfMediaPublishSchedule claimed = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        claimed.setId(95L);
        claimed.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        when(scheduleMapper.selectDueQueueCandidates(
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                any(),
                eq(10)
        )).thenReturn(List.of(candidate));
        when(environmentLockService.tryAcquire(eq(15L), eq(95L), any(), any())).thenReturn(true);
        when(scheduleMapper.claimQueueSchedule(
                eq(95L),
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                eq(SelfMediaPublishScheduleConstants.STATUS_FILLING),
                any(),
                any()
        )).thenReturn(1);
        when(scheduleMapper.selectById(95L)).thenReturn(claimed);

        SelfMediaPublishScheduleVO response = service.claimNext(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION, 10);

        assertEquals(95L, response.getId());
        assertEquals(SelfMediaPublishScheduleConstants.STATUS_FILLING, response.getStatus());
        verify(environmentLockService).tryAcquire(eq(15L), eq(95L), any(), any());
        verify(environmentLockService, never()).release(95L);
    }

    @Test
    void claimNext_doesNotAcquireEnvironmentLockForOfficialApiPlatform() {
        SelfMediaPublishSchedule candidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        candidate.setId(120L);
        candidate.setPlatform("wechat_mp");
        candidate.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        SelfMediaPublishSchedule claimed = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        claimed.setId(120L);
        claimed.setPlatform("wechat_mp");
        claimed.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        when(scheduleAdapterRouter.contract("wechat_mp")).thenReturn(Optional.of(new SelfMediaPlatformCapabilityContract(
                "wechat_mp",
                "微信公众号",
                SelfMediaPlatformPublishChannel.OFFICIAL_API,
                SelfMediaPlatformScheduleMode.BACKEND_DELAYED,
                SelfMediaPlatformScheduleRules.defaults(),
                false,
                false,
                false,
                true
        )));
        when(scheduleMapper.selectDueQueueCandidates(
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                any(),
                eq(10)
        )).thenReturn(List.of(candidate));
        when(scheduleMapper.claimQueueSchedule(
                eq(120L),
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                eq(SelfMediaPublishScheduleConstants.STATUS_FILLING),
                any(),
                any()
        )).thenReturn(1);
        when(scheduleMapper.selectById(120L)).thenReturn(claimed);

        SelfMediaPublishScheduleVO response = service.claimNext(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION, 10);

        assertEquals(120L, response.getId());
        assertEquals(SelfMediaPublishScheduleConstants.STATUS_FILLING, response.getStatus());
        verify(environmentLockService, never()).tryAcquire(anyLong(), anyLong(), any(), any());
        verify(environmentLockService, never()).release(120L);
    }

    @Test
    void claimNextPublishResultCheckClaimsScheduledOfficialApiSchedule() {
        SelfMediaPublishSchedule candidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_SCHEDULED);
        candidate.setId(121L);
        candidate.setPlatform("wechat_mp");
        candidate.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
        SelfMediaPublishSchedule claimed = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT);
        claimed.setId(121L);
        claimed.setPlatform("wechat_mp");
        claimed.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
        when(scheduleAdapterRouter.contract("wechat_mp")).thenReturn(Optional.of(new SelfMediaPlatformCapabilityContract(
                "wechat_mp",
                "微信公众号",
                SelfMediaPlatformPublishChannel.OFFICIAL_API,
                SelfMediaPlatformScheduleMode.BACKEND_DELAYED,
                SelfMediaPlatformScheduleRules.defaults(),
                false,
                false,
                false,
                true
        )));
        when(scheduleMapper.selectDueQueueCandidatesByPlatforms(
                eq(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK),
                eq(List.of(
                        SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_URL_PENDING,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN
                )),
                any(),
                eq(10),
                eq(Set.of("wechat_mp"))
        )).thenReturn(List.of(candidate));
        when(scheduleMapper.claimQueueSchedule(
                eq(121L),
                eq(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK),
                eq(List.of(
                        SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_URL_PENDING,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN
                )),
                eq(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT),
                any(),
                any()
        )).thenReturn(1);
        when(scheduleMapper.selectById(121L)).thenReturn(claimed);

        SelfMediaPublishScheduleVO response = service.claimNext(
                SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK,
                10,
                Set.of("wechat_mp")
        );

        assertEquals(121L, response.getId());
        assertEquals(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT, response.getStatus());
        verify(environmentLockService, never()).tryAcquire(anyLong(), anyLong(), any(), any());
    }

    @Test
    void claimNextTaskForLocalAgentPersistsQuotaFailureWhenDistributionTaskPrepareFails() {
        stubCurrentTimeInsideBusinessWindow();
        SelfMediaPublishSchedule candidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        candidate.setId(104L);
        candidate.setPlatform("xiaohongshu");
        candidate.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        SelfMediaPublishSchedule claimed = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        claimed.setId(104L);
        claimed.setPlatform("xiaohongshu");
        claimed.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(20L);
        account.setPlatform("xiaohongshu");
        when(scheduleMapper.selectDueQueueCandidatesForOperator(
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                any(),
                eq(10),
                eq(99L),
                eq("xiaohongshu"),
                eq(Set.of("toutiao", "baijiahao", "xiaohongshu", "zhihu"))
        )).thenReturn(List.of(candidate));
        when(environmentLockService.tryAcquire(eq(15L), eq(104L), any(), any())).thenReturn(true);
        when(scheduleMapper.claimQueueSchedule(
                eq(104L),
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                eq(SelfMediaPublishScheduleConstants.STATUS_FILLING),
                any(),
                any()
        )).thenReturn(1);
        when(scheduleMapper.selectById(104L)).thenReturn(claimed);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(contentDistributionService.distributeToAsOperator(eq(10L), any(), eq(99L)))
                .thenThrow(new BizException(400, "Distribution quota exhausted for channel self_media:xiaohongshu"));

        assertThrows(BizException.class, () -> service.claimNextTaskForLocalAgent(99L, "xiaohongshu", 3));

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED, claimed.getStatus());
        assertEquals("DISTRIBUTION_QUOTA_EXHAUSTED", claimed.getFailureCode());
        verify(scheduleMapper).updateById(claimed);
        verify(environmentLockService).release(104L);
    }

    @Test
    void claimNextPublishCheckForLocalAgentClaimsScheduledPublishCheckQueue() {
        stubCurrentTimeInsideBusinessWindow();
        SelfMediaPublishSchedule candidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_SCHEDULED);
        candidate.setId(103L);
        candidate.setCreatedBy(99L);
        candidate.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
        SelfMediaPublishSchedule claimed = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT);
        claimed.setId(103L);
        claimed.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
        when(scheduleMapper.selectDueQueueCandidatesForOperator(
                eq(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK),
                eq(List.of(
                        SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_URL_PENDING,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN
                )),
                any(),
                eq(10),
                eq(99L),
                eq("toutiao"),
                eq(Set.of("toutiao", "baijiahao", "xiaohongshu", "zhihu"))
        )).thenReturn(List.of(candidate));
        when(environmentLockService.tryAcquire(eq(15L), eq(103L), any(), any())).thenReturn(true);
        when(scheduleMapper.claimQueueSchedule(
                eq(103L),
                eq(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK),
                eq(List.of(
                        SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_URL_PENDING,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN
                )),
                eq(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT),
                any(),
                any()
        )).thenReturn(1);
        when(scheduleMapper.selectById(103L)).thenReturn(claimed);

        SelfMediaPublishScheduleVO response = service.claimNextPublishCheckForLocalAgent(99L, "toutiao", 10);

        assertEquals(103L, response.getId());
        assertEquals(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT, response.getStatus());
        verify(environmentLockService).tryAcquire(eq(15L), eq(103L), any(), any());
    }

    @Test
    void claimNextTaskForLocalAgentReturnsNullWhenOperatorCapacityIsFull() {
        stubCurrentTimeInsideBusinessWindow();
        when(scheduleMapper.countLockedByOperatorAndStatuses(eq(99L), anyList(), any())).thenReturn(1L);

        var response = service.claimNextTaskForLocalAgent(99L, "toutiao", 10);

        assertNull(response);
        verify(scheduleMapper, never()).selectDueQueueCandidatesForOperator(
                anyString(),
                anyList(),
                any(),
                anyInt(),
                anyLong(),
                any(),
                any()
        );
        verify(environmentLockService, never()).tryAcquire(anyLong(), anyLong(), any(), any());
    }

    @Test
    void claimNextTaskForLocalAgentRecoversExpiredRunningLockBeforeCapacityCheck() {
        stubCurrentTimeInsideBusinessWindow();
        SelfMediaPublishSchedule expiredRunning = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        expiredRunning.setId(108L);
        expiredRunning.setCreatedBy(99L);
        expiredRunning.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        expiredRunning.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        expiredRunning.setAttemptCount(0);
        expiredRunning.setMaxAttempts(2);
        when(scheduleMapper.selectTimedOutRunning(anyList(), any(), eq(10))).thenReturn(List.of(expiredRunning));
        when(scheduleMapper.selectById(108L)).thenReturn(expiredRunning);
        when(scheduleMapper.countLockedByOperatorAndStatuses(eq(99L), anyList(), any())).thenReturn(0L);

        var response = service.claimNextTaskForLocalAgent(99L, "toutiao", 10);

        assertNull(response);
        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PENDING, expiredRunning.getStatus());
        assertEquals("LOCAL_AGENT_HEARTBEAT_TIMEOUT", expiredRunning.getFailureCode());
        verify(scheduleMapper).updateById(expiredRunning);
        verify(environmentLockService).release(108L);
        verify(scheduleMapper).selectDueQueueCandidatesForOperator(
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                any(),
                eq(10),
                eq(99L),
                eq("toutiao"),
                eq(Set.of("toutiao", "baijiahao", "xiaohongshu", "zhihu"))
        );
    }

    @Test
    void automationOverviewMapsFailureCodesAndLocalCapacityStatus() {
        when(scheduleMapper.countByStatuses(anyList())).thenReturn(7L, 1L, 2L, 1L, 1L);
        when(scheduleMapper.countDueByQueue(
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                anyList(),
                any()
        )).thenReturn(3L);
        when(scheduleMapper.countDueByQueue(
                eq(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK),
                anyList(),
                any()
        )).thenReturn(2L);
        when(scheduleMapper.countLockedByStatuses(anyList(), any())).thenReturn(1L);
        when(localAgentSessionMapper.countActiveSessions(any())).thenReturn(1L);
        when(localAgentSessionMapper.countOnlineSessions(any(), any())).thenReturn(0L);
        when(scheduleMapper.countGroupedByStatus())
                .thenReturn(List.of(Map.of("name", "pending", "total", 7L)));
        when(scheduleMapper.countGroupedByPlatform(anyList(), any()))
                .thenReturn(List.of(Map.of(
                        "name", "toutiao",
                        "active_total", 4L,
                        "failed_total", 1L,
                        "due_total", 3L
                )));
        when(scheduleMapper.countGroupedByFailureCode(12))
                .thenReturn(List.of(Map.of("name", "LOCAL_AGENT_OFFLINE", "total", 2L)));
        when(scheduleCapabilityService.list()).thenReturn(List.of());
        Brand sourceBrand = new Brand();
        sourceBrand.setId(80L);
        sourceBrand.setBrandName("百业观察");
        when(brandMapper.selectThirdPartySourceBrands()).thenReturn(List.of(sourceBrand));
        when(thirdPartySubjectRotationService.previewPool(eq(80L), eq(1), eq(0)))
                .thenReturn(new ThirdPartySubjectPoolPreviewResponse(
                        80L,
                        "百业观察",
                        List.of("科技互联网"),
                        false,
                        true,
                        1,
                        3,
                        1,
                        0,
                        List.of(new ThirdPartySubjectPoolPreviewResponse.Item(
                                90L,
                                "华为",
                                "科技互联网",
                                9L,
                                "华为技术有限公司",
                                900L,
                                null,
                                null,
                                null
                        )),
                        List.of()
                ));

        SelfMediaAutomationOverviewVO overview = service.automationOverview();

        assertEquals("blocked", overview.getLocalExecution().getCapacityStatus());
        assertEquals(5L, overview.getLocalExecution().getWaitingForLocalAgent());
        assertEquals("pending", overview.getStatusCounts().get(0).getStatus());
        assertEquals("toutiao", overview.getPlatformCounts().get(0).getPlatform());
        assertEquals("LOCAL_AGENT_OFFLINE", overview.getFailureCodeCounts().get(0).getCode());
        assertEquals("OPEN_LOCAL_HELPER", overview.getFailureCodeCounts().get(0).getActionKey());
        assertEquals(1L, overview.getThirdPartySubjectPool().getSourceTotal());
        assertEquals(1L, overview.getThirdPartySubjectPool().getReadySourceTotal());
        assertEquals("百业观察", overview.getThirdPartySubjectPool().getSources().get(0).getSourceBrandName());
        assertEquals("华为", overview.getThirdPartySubjectPool().getSources().get(0).getNextCandidateBrandName());
    }

    @Test
    void claimNextTaskForLocalAgentPostponesDueTaskOutsideBusinessWindow() {
        SelfMediaPublishSchedule candidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        candidate.setId(109L);
        candidate.setBrandId(8L);
        candidate.setPlatform("toutiao");
        candidate.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        LocalDate nextBusinessDay = LocalDate.now().plusDays(1);
        LocalDateTime nextWindowStart = nextBusinessDay.atTime(9, 15);
        doReturn(List.of(new BusinessCalendarService.BusinessDay(
                nextBusinessDay,
                0,
                "工作日",
                1,
                false,
                List.of(new BusinessCalendarService.PublishWindow(
                        "morning",
                        LocalTime.of(9, 15),
                        LocalTime.of(11, 30),
                        LocalTime.of(9, 15)
                ))
        ))).when(businessCalendarService).publishDays(any(), eq(false));
        when(scheduleMapper.selectDueQueueCandidatesForOperator(
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                any(),
                eq(10),
                eq(99L),
                eq("toutiao"),
                eq(Set.of("toutiao", "baijiahao", "xiaohongshu", "zhihu"))
        )).thenReturn(List.of(candidate));

        var response = service.claimNextTaskForLocalAgent(99L, "toutiao", 10);

        assertNull(response);
        assertEquals(nextWindowStart, candidate.getNextAttemptAt());
        verify(scheduleMapper).updateById(candidate);
        verify(environmentLockService, never()).tryAcquire(anyLong(), anyLong(), any(), any());
        verify(scheduleMapper, never()).claimQueueSchedule(anyLong(), anyString(), anyList(), anyString(), any(), any());
    }

    @Test
    void claimNextTaskForLocalAgentDoesNotPostponeManualQuickDispatchOutsideBusinessWindow() {
        SelfMediaPublishSchedule candidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        candidate.setId(110L);
        candidate.setBrandId(8L);
        candidate.setPlatform("baijiahao");
        candidate.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        candidate.setRequestIdempotencyKey("platform-quick-dispatch-990006650-baijiahao-test");
        SelfMediaPublishSchedule claimed = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        claimed.setId(110L);
        claimed.setBrandId(8L);
        claimed.setPlatform("baijiahao");
        claimed.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        doReturn(List.of(new BusinessCalendarService.BusinessDay(
                LocalDate.now().plusDays(1),
                0,
                "工作日",
                1,
                false,
                List.of(new BusinessCalendarService.PublishWindow(
                        "morning",
                        LocalTime.of(9, 15),
                        LocalTime.of(11, 30),
                        LocalTime.of(9, 15)
                ))
        ))).when(businessCalendarService).publishDays(any(), eq(false));
        when(scheduleMapper.selectDueQueueCandidatesForOperator(
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                any(),
                eq(10),
                eq(99L),
                eq("baijiahao"),
                eq(Set.of("toutiao", "baijiahao", "xiaohongshu", "zhihu"))
        )).thenReturn(List.of(candidate));
        when(environmentLockService.tryAcquire(eq(15L), eq(110L), any(), any())).thenReturn(true);
        when(scheduleMapper.claimQueueSchedule(
                eq(110L),
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                eq(SelfMediaPublishScheduleConstants.STATUS_FILLING),
                any(),
                any()
        )).thenReturn(1);
        when(scheduleMapper.selectById(110L)).thenReturn(claimed);
        SelfMediaAccount account = account();
        account.setPlatform("baijiahao");
        when(accountMapper.selectById(20L)).thenReturn(account);
        DistributionTask task = new DistributionTask();
        task.setId(210L);
        when(contentDistributionService.distributeToAsOperator(eq(10L), any(), eq(99L)))
                .thenReturn(task);

        var response = service.claimNextTaskForLocalAgent(99L, "baijiahao", 10);

        assertNotNull(response);
        assertEquals(110L, response.schedule().getId());
        verify(scheduleMapper, never()).updateById(candidate);
        verify(environmentLockService).tryAcquire(eq(15L), eq(110L), any(), any());
    }

    @Test
    void claimNextTaskForLocalAgentPostponesOutsideBusinessWindowAfterExistingBrandSlot() {
        SelfMediaPublishSchedule candidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        candidate.setId(113L);
        candidate.setBrandId(8L);
        candidate.setPlatform("toutiao");
        candidate.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        LocalDate nextBusinessDay = LocalDate.now().plusDays(1);
        LocalDateTime nextWindowStart = nextBusinessDay.atTime(9, 15);
        doReturn(List.of(new BusinessCalendarService.BusinessDay(
                nextBusinessDay,
                0,
                "工作日",
                1,
                false,
                List.of(new BusinessCalendarService.PublishWindow(
                        "morning",
                        LocalTime.of(9, 15),
                        LocalTime.of(11, 30),
                        LocalTime.of(9, 15)
                ))
        ))).when(businessCalendarService).publishDays(any(), eq(false));
        SelfMediaPublishSchedule occupied = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        occupied.setId(201L);
        occupied.setBrandId(8L);
        occupied.setNextAttemptAt(nextWindowStart);
        when(scheduleMapper.selectBrandActiveScheduleSlots(eq(8L), any(), any(), anyList()))
                .thenReturn(List.of(occupied));
        when(scheduleMapper.selectDueQueueCandidatesForOperator(
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                any(),
                eq(10),
                eq(99L),
                eq("toutiao"),
                eq(Set.of("toutiao", "baijiahao", "xiaohongshu", "zhihu"))
        )).thenReturn(List.of(candidate));

        var response = service.claimNextTaskForLocalAgent(99L, "toutiao", 10);

        assertNull(response);
        assertEquals(nextWindowStart.plusMinutes(3), candidate.getNextAttemptAt());
        verify(scheduleMapper).updateById(candidate);
        verify(environmentLockService, never()).tryAcquire(anyLong(), anyLong(), any(), any());
    }

    @Test
    void markClaimedPublishCheckUnknownSchedulesDelayedRetryWhenAttemptsRemain() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT);
        row.setId(104L);
        row.setAttemptCount(2);
        row.setMaxAttempts(4);
        when(scheduleMapper.selectById(104L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.markClaimedPublishCheckUnknown(104L, "{\"found\":false}");

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN, response.getStatus());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, response.getQueueKind());
        assertEquals("PUBLISH_RESULT_NOT_MATCHED_RETRYING", response.getFailureCode());
        assertTrue(response.getNextAttemptAt().isAfter(LocalDateTime.now()));
        verify(environmentLockService).release(104L);
    }

    @Test
    void markClaimedPublishCheckUnknownKeepsPendingPlatformScheduleAsWaiting() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT);
        row.setId(108L);
        row.setPlatform("xiaohongshu");
        row.setPlatformScheduledAt(LocalDateTime.now().plusHours(2));
        row.setAttemptCount(4);
        row.setMaxAttempts(4);
        when(scheduleMapper.selectById(108L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.markClaimedPublishCheckUnknown(
                108L,
                "{\"pendingScheduled\":true,\"reason\":\"platform schedule time not due\"}"
        );

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN, response.getStatus());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, response.getQueueKind());
        assertEquals("PLATFORM_SCHEDULED_WAITING", response.getFailureCode());
        assertEquals("平台已定时，等待发布时间后至少 1 小时复查", response.getFailureMessage());
        assertTrue(response.getNextAttemptAt().isAfter(row.getPlatformScheduledAt()));
        verify(scheduleMapper).updateById(row);
        verify(environmentLockService).release(108L);
    }

    @Test
    void markClaimedPublishCheckUnknownMovesPendingPlatformScheduleCheckIntoBusinessWindow() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT);
        row.setId(112L);
        row.setBrandId(8L);
        row.setPlatform("xiaohongshu");
        LocalDate platformPublishDate = LocalDate.now().plusDays(1);
        row.setPlatformScheduledAt(platformPublishDate.atTime(23, 0));
        row.setAttemptCount(4);
        row.setMaxAttempts(4);
        LocalDate nextBusinessDay = platformPublishDate.plusDays(1);
        LocalDateTime nextWindowStart = nextBusinessDay.atTime(9, 15);
        doReturn(List.of(new BusinessCalendarService.BusinessDay(
                nextBusinessDay,
                0,
                "工作日",
                1,
                false,
                List.of(new BusinessCalendarService.PublishWindow(
                        "morning",
                        LocalTime.of(9, 15),
                        LocalTime.of(11, 30),
                        LocalTime.of(9, 15)
                ))
        ))).when(businessCalendarService).publishDays(any(), eq(false));
        when(scheduleMapper.selectById(112L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.markClaimedPublishCheckUnknown(
                112L,
                "{\"pendingScheduled\":true,\"reason\":\"platform schedule time not due\"}"
        );

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN, response.getStatus());
        assertEquals(nextWindowStart, response.getNextAttemptAt());
        verify(scheduleMapper).updateById(row);
        verify(environmentLockService).release(112L);
    }

    @Test
    void markClaimedPublishCheckUnknownExtendsLegacyAttemptLimit() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT);
        row.setId(106L);
        row.setAttemptCount(2);
        row.setMaxAttempts(2);
        when(scheduleMapper.selectById(106L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.markClaimedPublishCheckUnknown(106L, "{\"found\":false}");

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN, response.getStatus());
        assertEquals(4, response.getMaxAttempts());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, response.getQueueKind());
        assertEquals("PUBLISH_RESULT_NOT_MATCHED_RETRYING", response.getFailureCode());
        assertTrue(response.getNextAttemptAt().isAfter(LocalDateTime.now()));
        verify(environmentLockService).release(106L);
    }

    @Test
    void markLocalAgentExecutionFailedIsIdempotentAfterPublishConfirmed() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED);
        row.setId(107L);
        row.setDiagnosticsJson("{\"verified\":true}");
        when(scheduleMapper.selectById(107L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.markLocalAgentExecutionFailed(
                107L,
                "ZHIHU_PUBLISH_NOT_SUBMITTED",
                "late failure report",
                "{\"failure\":true}"
        );

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED, response.getStatus());
        assertEquals("{\"verified\":true}", response.getDiagnosticsJson());
        verify(scheduleMapper, never()).updateById(any());
        verify(environmentLockService).release(107L);
    }

    @Test
    void heartbeatLocalAgentScheduleRenewsScheduleAndEnvironmentLock() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        row.setId(110L);
        row.setCreatedBy(99L);
        SelfMediaPublishSchedule renewed = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        renewed.setId(110L);
        renewed.setCreatedBy(99L);
        renewed.setLockedUntil(LocalDateTime.now().plusMinutes(3));
        when(scheduleMapper.selectById(110L)).thenReturn(row, renewed);
        when(scheduleMapper.renewLocalAgentLock(
                eq(110L),
                eq(99L),
                eq(List.of(
                        SelfMediaPublishScheduleConstants.STATUS_FILLING,
                        SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED,
                        SelfMediaPublishScheduleConstants.STATUS_SCHEDULING,
                        SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT
                )),
                any(),
                any()
        )).thenReturn(1);
        when(environmentLockService.renew(eq(15L), eq(110L), any(), any())).thenReturn(true);

        SelfMediaPublishScheduleVO response = service.heartbeatLocalAgentSchedule(99L, 110L, 3);

        assertEquals(110L, response.getId());
        assertEquals(SelfMediaPublishScheduleConstants.STATUS_FILLING, response.getStatus());
        verify(scheduleMapper).renewLocalAgentLock(eq(110L), eq(99L), any(), any(), any());
        verify(environmentLockService).renew(eq(15L), eq(110L), any(), any());
    }

    @Test
    void heartbeatLocalAgentScheduleRejectsOtherOperator() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        row.setId(111L);
        row.setCreatedBy(88L);
        when(scheduleMapper.selectById(111L)).thenReturn(row);

        assertThrows(BizException.class, () -> service.heartbeatLocalAgentSchedule(99L, 111L, 3));

        verify(scheduleMapper, never()).renewLocalAgentLock(anyLong(), anyLong(), any(), any(), any());
        verify(environmentLockService, never()).renew(anyLong(), anyLong(), any(), any());
    }

    @Test
    void recoverTimedOutLocalAgentSchedulesRequeuesScheduleExecution() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        row.setId(112L);
        row.setDistributionTaskId(312L);
        row.setAttemptCount(1);
        row.setMaxAttempts(3);
        row.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(scheduleMapper.selectTimedOutRunning(any(), any(), eq(10))).thenReturn(List.of(row));
        when(scheduleMapper.selectById(112L)).thenReturn(row);

        int recovered = service.recoverTimedOutLocalAgentSchedules(10);

        assertEquals(1, recovered);
        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PENDING, row.getStatus());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION, row.getQueueKind());
        assertEquals("LOCAL_AGENT_HEARTBEAT_TIMEOUT", row.getFailureCode());
        assertTrue(row.getNextAttemptAt().isAfter(LocalDateTime.now()));
        assertNull(row.getLockedUntil());
        verify(scheduleMapper).updateById(row);
        verify(companyChannelQuotaService).refundDistribution(312L);
        verify(environmentLockService).release(112L);
    }

    @Test
    void recoverTimedOutLocalAgentSchedulesRequeuesPublishCheck() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT);
        row.setId(113L);
        row.setAttemptCount(2);
        row.setMaxAttempts(4);
        row.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(scheduleMapper.selectTimedOutRunning(any(), any(), eq(10))).thenReturn(List.of(row));
        when(scheduleMapper.selectById(113L)).thenReturn(row);

        int recovered = service.recoverTimedOutLocalAgentSchedules(10);

        assertEquals(1, recovered);
        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN, row.getStatus());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, row.getQueueKind());
        assertEquals("PUBLISH_RESULT_NOT_MATCHED_RETRYING", row.getFailureCode());
        assertTrue(row.getNextAttemptAt().isAfter(LocalDateTime.now()));
        assertNull(row.getLockedUntil());
        verify(scheduleMapper).updateById(row);
        verify(environmentLockService).release(113L);
    }

    @Test
    void recheckPublishResultQueuesImmediatePublishCheck() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN);
        row.setId(105L);
        row.setAttemptCount(4);
        row.setMaxAttempts(4);
        when(scheduleMapper.selectById(105L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.recheckPublishResult(105L);

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN, response.getStatus());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, response.getQueueKind());
        assertEquals("PUBLISH_RESULT_RECHECK_REQUESTED", response.getFailureCode());
        assertEquals(5, response.getMaxAttempts());
        assertTrue(response.getNextAttemptAt().isBefore(LocalDateTime.now().plusSeconds(5)));
        verify(scheduleMapper).updateById(row);
        verify(environmentLockService).release(105L);
    }

    @Test
    void retryNowQueuesScheduleExecutionForManualSchedule() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        row.setId(114L);
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        row.setAttemptCount(3);
        row.setMaxAttempts(3);
        row.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(scheduleMapper.selectById(114L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.retryNow(114L);

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PENDING, response.getStatus());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION, response.getQueueKind());
        assertEquals("MANUAL_RETRY_REQUESTED", response.getFailureCode());
        assertEquals(4, response.getMaxAttempts());
        assertNull(row.getLockedUntil());
        assertTrue(response.getNextAttemptAt().isAfter(LocalDateTime.now().plusMinutes(1)));
        assertTrue(response.getNextAttemptAt().isBefore(LocalDateTime.now().plusMinutes(3)));
        assertEquals(SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE, response.getScheduleStrategy());
        assertEquals(response.getPlannedPublishAt(), response.getPlatformScheduledAt());
        assertTrue(response.getPlannedPublishAt().isAfter(response.getNextAttemptAt().plusMinutes(120)));
        verify(scheduleMapper).updateById(row);
        verify(environmentLockService).release(114L);
    }

    @Test
    void retryNowKeepsPlatformScheduleAfterExecutionLeadBoundary() {
        when(scheduleAdapterRouter.rules("douyin", SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE))
                .thenReturn(new SelfMediaPlatformScheduleRules(130, 120, 3, 14 * 24 * 60));
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED);
        row.setId(117L);
        row.setPlatform("douyin");
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        row.setScheduleStrategy(SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE);
        row.setAttemptCount(0);
        row.setMaxAttempts(3);
        row.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(scheduleMapper.selectById(117L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.retryNow(117L);

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PENDING, response.getStatus());
        assertEquals(response.getPlannedPublishAt(), response.getPlatformScheduledAt());
        assertTrue(response.getPlannedPublishAt().isAfter(response.getNextAttemptAt().plusMinutes(120)));
        assertEquals(130, java.time.Duration.between(response.getNextAttemptAt(), response.getPlannedPublishAt()).toMinutes());
        verify(scheduleMapper).updateById(row);
        verify(environmentLockService).release(117L);
    }

    @Test
    void retryNowQueuesPublishCheckForPublishFailure() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_FAILED);
        row.setId(115L);
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK);
        row.setAttemptCount(4);
        row.setMaxAttempts(4);
        when(scheduleMapper.selectById(115L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.retryNow(115L);

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN, response.getStatus());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, response.getQueueKind());
        assertEquals("PUBLISH_RESULT_RECHECK_REQUESTED", response.getFailureCode());
        assertEquals(5, response.getMaxAttempts());
        assertTrue(response.getNextAttemptAt().isBefore(LocalDateTime.now().plusSeconds(5)));
        verify(scheduleMapper).updateById(row);
        verify(environmentLockService).release(115L);
    }

    @Test
    void markManualRequiredUnlocksRunningSchedule() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        row.setId(116L);
        row.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        row.setLockedUntil(LocalDateTime.now().plusMinutes(3));
        row.setNextAttemptAt(LocalDateTime.now().plusMinutes(5));
        when(scheduleMapper.selectById(116L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.markManualRequired(116L, "operator takeover");

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED, response.getStatus());
        assertEquals("MANUAL_REQUIRED_BY_OPERATOR", response.getFailureCode());
        assertEquals("operator takeover", response.getFailureMessage());
        assertNull(row.getLockedUntil());
        assertNull(row.getNextAttemptAt());
        verify(scheduleMapper).updateById(row);
        verify(environmentLockService).release(116L);
    }

    @Test
    void claimNext_returnsNullWhenAtomicUpdateMisses() {
        SelfMediaPublishSchedule candidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        candidate.setId(96L);
        when(scheduleMapper.selectDueQueueCandidates(anyString(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(candidate));
        when(environmentLockService.tryAcquire(eq(15L), eq(96L), any(), any())).thenReturn(true);
        when(scheduleMapper.claimQueueSchedule(anyLong(), anyString(), any(), anyString(), any(), any())).thenReturn(0);

        SelfMediaPublishScheduleVO response = service.claimNext(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION, 10);

        assertNull(response);
        verify(environmentLockService).release(96L);
    }

    @Test
    void claimNext_skipsCandidateWhenEnvironmentLockBusy() {
        SelfMediaPublishSchedule candidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        candidate.setId(101L);
        when(scheduleMapper.selectDueQueueCandidates(anyString(), any(), any(), any(Integer.class)))
                .thenReturn(List.of(candidate));
        when(environmentLockService.tryAcquire(eq(15L), eq(101L), any(), any())).thenReturn(false);

        SelfMediaPublishScheduleVO response = service.claimNext(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION, 10);

        assertNull(response);
        verify(scheduleMapper, never()).claimQueueSchedule(anyLong(), anyString(), any(), anyString(), any(), any());
        verify(environmentLockService, never()).release(anyLong());
    }

    @Test
    void claimedScheduleExecutionCanMoveThroughFillAndSchedulingStates() {
        SelfMediaPublishSchedule filling = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        filling.setId(97L);
        when(scheduleMapper.selectById(97L)).thenReturn(filling);

        SelfMediaPublishScheduleVO filled = service.markClaimedFilledVerified(97L, "{\"filled\":true}");

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED, filled.getStatus());
        assertEquals("{\"filled\":true}", filled.getDiagnosticsJson());
        verify(environmentLockService, never()).release(97L);

        SelfMediaPublishSchedule filledRow = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLED_VERIFIED);
        filledRow.setId(97L);
        when(scheduleMapper.selectById(97L)).thenReturn(filledRow);
        SelfMediaPublishScheduleVO scheduling = service.markClaimedScheduling(97L, null);

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_SCHEDULING, scheduling.getStatus());
        verify(environmentLockService, never()).release(97L);
    }

    @Test
    void markLocalAgentExecutionScheduledMovesClaimedScheduleToPublishCheckQueue() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        row.setId(117L);
        row.setDistributionTaskId(317L);
        when(scheduleMapper.selectById(117L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.markLocalAgentExecutionScheduled(
                117L,
                "{\"fillResult\":{\"publishOptions\":{\"publishVerification\":{\"verified\":true,\"platformScheduledAt\":\"2026-06-12 09:30\",\"platformScheduleId\":\"schedule-117\",\"platformPublishId\":\"publish-117\",\"platformPublishedUrl\":\"https://example.test/scheduled/117\",\"coverImageUrl\":\"https://cdn.test/cover-117.jpg\"}}}}"
        );

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_SCHEDULED, response.getStatus());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, response.getQueueKind());
        assertEquals("schedule-117", response.getPlatformScheduleId());
        assertEquals("publish-117", response.getPlatformPublishId());
        assertEquals("https://example.test/scheduled/117", response.getPlatformPublishedUrl());
        assertEquals("https://cdn.test/cover-117.jpg", response.getPublishCheckCoverUrl());
        assertEquals(LocalDateTime.of(2026, 6, 12, 9, 30), response.getPlatformScheduledAt());
        assertEquals(LocalDateTime.of(2026, 6, 12, 10, 30), response.getNextAttemptAt());
        verify(companyChannelQuotaService).confirmSelfMediaSchedule(117L);
        verify(companyChannelQuotaService).confirmDistribution(317L);
        verify(environmentLockService).release(117L);
    }

    @Test
    void markLocalAgentExecutionPublishedConfirmedClosesScheduleAndConfirmsQuota() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        row.setId(118L);
        row.setDistributionTaskId(318L);
        row.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        row.setNextAttemptAt(LocalDateTime.now().plusMinutes(10));
        ArticleDraft article = article();
        when(scheduleMapper.selectById(118L)).thenReturn(row);
        when(articleDraftMapper.selectById(10L)).thenReturn(article);

        SelfMediaPublishScheduleVO response = service.markLocalAgentExecutionPublishedConfirmed(
                118L,
                null,
                "{\"fillResult\":{\"publishOptions\":{\"publishVerification\":{\"verified\":true,\"platformPublishedUrl\":\"https://example.test/article/118\",\"platformPublishId\":\"publish-118\",\"coverImageUrl\":\"https://cdn.test/cover-118.jpg\"}}}}"
        );

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED, response.getStatus());
        assertEquals("https://example.test/article/118", response.getPlatformPublishedUrl());
        assertEquals("publish-118", response.getPlatformPublishId());
        assertEquals("https://cdn.test/cover-118.jpg", response.getPublishCheckCoverUrl());
        assertNull(response.getLockedUntil());
        assertNull(response.getNextAttemptAt());
        assertTrue(response.getDiagnosticsJson().contains("\"platformPublishedUrl\":\"https://example.test/article/118\""));
        assertEquals("published", article.getStatus());
        verify(articleDraftMapper).updateById(article);
        verify(companyChannelQuotaService).confirmSelfMediaSchedule(118L);
        verify(companyChannelQuotaService).confirmDistribution(318L);
        verify(environmentLockService).release(118L);
    }

    @Test
    void markLocalAgentExecutionPublishedConfirmedKeepsUrlPendingWhenPublishedUrlMissing() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT);
        row.setId(122L);
        row.setDistributionTaskId(322L);
        row.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        ArticleDraft article = article();
        when(scheduleMapper.selectById(122L)).thenReturn(row);
        when(articleDraftMapper.selectById(10L)).thenReturn(article);

        SelfMediaPublishScheduleVO response = service.markLocalAgentExecutionPublishedConfirmed(
                122L,
                null,
                "{\"found\":true,\"platformPublishId\":\"publish-122\",\"coverImageUrl\":\"https://cdn.test/cover-122.jpg\"}"
        );

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_URL_PENDING, response.getStatus());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, response.getQueueKind());
        assertEquals("publish-122", response.getPlatformPublishId());
        assertEquals("https://cdn.test/cover-122.jpg", response.getPublishCheckCoverUrl());
        assertEquals("PUBLISHED_URL_PENDING", response.getFailureCode());
        assertEquals("平台已确认发布，等待发布链接回写", response.getFailureMessage());
        assertNull(response.getLockedUntil());
        assertTrue(response.getNextAttemptAt().isAfter(LocalDateTime.now().plusMinutes(30)));
        assertEquals("published", article.getStatus());
        verify(articleDraftMapper).updateById(article);
        verify(companyChannelQuotaService).confirmDistribution(322L);
        verify(environmentLockService).release(122L);
    }

    @Test
    void markClaimedScheduledReleasesEnvironmentLockAtSuccessTerminalState() {
        SelfMediaPublishSchedule scheduling = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_SCHEDULING);
        scheduling.setId(102L);
        scheduling.setDistributionTaskId(302L);
        LocalDateTime publishAt = LocalDateTime.of(2026, 6, 1, 18, 30);
        scheduling.setPlatformScheduledAt(publishAt);
        when(scheduleMapper.selectById(102L)).thenReturn(scheduling);

        SelfMediaPublishScheduleVO response = service.markClaimedScheduled(102L, "platform-schedule-1", "{\"ok\":true}");

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_SCHEDULED, response.getStatus());
        assertEquals("platform-schedule-1", response.getPlatformScheduleId());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, response.getQueueKind());
        assertEquals(LocalDateTime.of(2026, 6, 2, 9, 30), response.getNextAttemptAt());
        verify(scheduleMapper).updateById(scheduling);
        verify(companyChannelQuotaService).confirmDistribution(302L);
        verify(environmentLockService).release(102L);
    }

    @Test
    void markClaimedScheduledKeepsArticleDistributingWhenPlatformScheduled() {
        SelfMediaPublishSchedule scheduling = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_SCHEDULING);
        scheduling.setId(102L);
        ArticleDraft article = article();
        when(scheduleMapper.selectById(102L)).thenReturn(scheduling);
        when(articleDraftMapper.selectById(10L)).thenReturn(article);

        service.markClaimedScheduled(102L, "platform-schedule-1", "{\"ok\":true}");

        assertEquals("distributing", article.getStatus());
        verify(articleDraftMapper).updateById(article);
    }

    @Test
    void markClaimedPublishedConfirmedClosesLinkedDistributionTask() {
        SelfMediaPublishSchedule checking = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT);
        checking.setId(103L);
        checking.setDistributionTaskId(318L);
        ArticleDraft article = article();
        when(scheduleMapper.selectById(103L)).thenReturn(checking);
        when(articleDraftMapper.selectById(10L)).thenReturn(article);

        SelfMediaPublishScheduleVO response = service.markClaimedPublishedConfirmed(
                103L,
                null,
                """
                        {"found":true,"platformPublishedUrl":"https://www.douyin.com/video/123","platformPublishId":"123","coverImageUrl":"https://p.example.test/cover.jpg"}
                        """
        );

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_CONFIRMED, response.getStatus());
        assertEquals("https://www.douyin.com/video/123", response.getPlatformPublishedUrl());
        assertEquals("123", response.getPlatformPublishId());
        assertEquals("https://p.example.test/cover.jpg", response.getPublishCheckCoverUrl());
        assertEquals("published", article.getStatus());
        verify(distributionTaskMapper).update(eq(null), any());
    }

    @Test
    void markClaimedPublishedConfirmedQueuesUrlBackfillWhenUrlMissing() {
        SelfMediaPublishSchedule checking = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_CHECKING_PUBLISH_RESULT);
        checking.setId(123L);
        checking.setDistributionTaskId(323L);
        ArticleDraft article = article();
        when(scheduleMapper.selectById(123L)).thenReturn(checking);
        when(articleDraftMapper.selectById(10L)).thenReturn(article);

        SelfMediaPublishScheduleVO response = service.markClaimedPublishedConfirmed(
                123L,
                null,
                "{\"found\":true,\"platformPublishId\":\"2247484434\"}"
        );

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PUBLISHED_URL_PENDING, response.getStatus());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, response.getQueueKind());
        assertEquals("2247484434", response.getPlatformPublishId());
        assertEquals("PUBLISHED_URL_PENDING", response.getFailureCode());
        assertTrue(response.getNextAttemptAt().isAfter(LocalDateTime.now().plusMinutes(30)));
        assertEquals("published", article.getStatus());
        verify(distributionTaskMapper).update(eq(null), any());
        verify(environmentLockService).release(123L);
    }

    @Test
    void markClaimedScheduledSpreadsDeferredPublishCheckByBrandInterval() {
        SelfMediaPublishSchedule scheduling = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_SCHEDULING);
        scheduling.setId(102L);
        LocalDateTime publishAt = LocalDateTime.of(2026, 6, 1, 18, 30);
        scheduling.setPlatformScheduledAt(publishAt);
        when(scheduleMapper.selectById(102L)).thenReturn(scheduling);
        SelfMediaPublishSchedule occupied = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        occupied.setId(201L);
        occupied.setNextAttemptAt(LocalDateTime.of(2026, 6, 2, 9, 30));
        when(scheduleMapper.selectBrandActiveScheduleSlots(eq(8L), any(), any(), anyList()))
                .thenReturn(List.of(occupied));

        SelfMediaPublishScheduleVO response = service.markClaimedScheduled(102L, "platform-schedule-1", "{\"ok\":true}");

        assertEquals(LocalDateTime.of(2026, 6, 2, 9, 33), response.getNextAttemptAt());
        verify(scheduleMapper).updateById(scheduling);
    }

    @Test
    void claimNextTaskForLocalAgentUsesAttemptScopedDistributionRequestId() {
        stubCurrentTimeInsideBusinessWindow();
        SelfMediaPublishSchedule candidate = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_PENDING);
        candidate.setId(109L);
        candidate.setPlatform("baijiahao");
        candidate.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        SelfMediaPublishSchedule claimed = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        claimed.setId(109L);
        claimed.setPlatform("baijiahao");
        claimed.setGenerationNo(2);
        claimed.setAttemptCount(3);
        claimed.setQueueKind(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION);
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(20L);
        account.setPlatform("baijiahao");
        when(scheduleMapper.selectDueQueueCandidatesForOperator(
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                any(),
                eq(10),
                eq(99L),
                eq("baijiahao"),
                eq(Set.of("toutiao", "baijiahao", "xiaohongshu", "zhihu"))
        )).thenReturn(List.of(candidate));
        when(environmentLockService.tryAcquire(eq(15L), eq(109L), any(), any())).thenReturn(true);
        when(scheduleMapper.claimQueueSchedule(
                eq(109L),
                eq(SelfMediaPublishScheduleConstants.QUEUE_SCHEDULE_EXECUTION),
                eq(List.of(SelfMediaPublishScheduleConstants.STATUS_PENDING)),
                eq(SelfMediaPublishScheduleConstants.STATUS_FILLING),
                any(),
                any()
        )).thenReturn(1);
        when(scheduleMapper.selectById(109L)).thenReturn(claimed);
        when(accountMapper.selectById(20L)).thenReturn(account);
        DistributionTask task = new DistributionTask();
        task.setId(409L);
        when(contentDistributionService.distributeToAsOperator(eq(10L), any(TargetContext.class), eq(99L)))
                .thenReturn(task);

        SelfMediaPublishScheduleVO response = service.claimNextTaskForLocalAgent(99L, "baijiahao", 3).schedule();

        assertEquals(109L, response.getId());
        ArgumentCaptor<TargetContext> targetCaptor = ArgumentCaptor.forClass(TargetContext.class);
        verify(contentDistributionService).distributeToAsOperator(eq(10L), targetCaptor.capture(), eq(99L));
        TargetContext.SelfMediaTarget target = (TargetContext.SelfMediaTarget) targetCaptor.getValue();
        assertEquals("schedule-109-gen-2-attempt-3", target.requestId());
    }

    @Test
    void markClaimFailed_retriesWhenAttemptsRemain() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        row.setId(98L);
        row.setAttemptCount(1);
        row.setMaxAttempts(2);
        when(scheduleMapper.selectById(98L)).thenReturn(row);
        LocalDateTime nextAttemptAt = LocalDateTime.of(2026, 6, 1, 11, 0);

        SelfMediaPublishScheduleVO response = service.markClaimFailed(
                98L,
                SelfMediaPublishScheduleConstants.STATUS_FILLING,
                "EDITOR_NOT_FOUND",
                "editor not ready",
                null,
                nextAttemptAt
        );

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PENDING, response.getStatus());
        assertEquals(nextAttemptAt, response.getNextAttemptAt());
        assertEquals("EDITOR_NOT_FOUND", response.getFailureCode());
        verify(environmentLockService).release(98L);
    }

    @Test
    void markClaimFailedMovesToManualRequiredWhenAttemptsExhausted() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        row.setId(99L);
        row.setAttemptCount(2);
        row.setMaxAttempts(2);
        when(scheduleMapper.selectById(99L)).thenReturn(row);

        SelfMediaPublishScheduleVO response = service.markClaimFailed(
                99L,
                SelfMediaPublishScheduleConstants.STATUS_FILLING,
                null,
                "failed",
                null,
                LocalDateTime.of(2026, 6, 1, 11, 0)
        );

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED, response.getStatus());
        assertEquals("SCHEDULE_EXECUTION_FAILED", response.getFailureCode());
        verify(environmentLockService).release(99L);
    }

    @Test
    void markClaimFailedTruncatesLongFailureMessageForPersistence() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        row.setId(101L);
        row.setAttemptCount(2);
        row.setMaxAttempts(2);
        when(scheduleMapper.selectById(101L)).thenReturn(row);
        String longFailureMessage = "微信公众号发布权限不足：" + "错误详情".repeat(200);
        String diagnosticsJson = "{\"wechatError\":\"full details\"}";

        SelfMediaPublishScheduleVO response = service.markClaimFailed(
                101L,
                SelfMediaPublishScheduleConstants.STATUS_FILLING,
                "WECHAT_API_UNAUTHORIZED",
                longFailureMessage,
                diagnosticsJson,
                LocalDateTime.of(2026, 6, 1, 11, 0)
        );

        ArgumentCaptor<SelfMediaPublishSchedule> captor = ArgumentCaptor.forClass(SelfMediaPublishSchedule.class);
        verify(scheduleMapper).updateById(captor.capture());
        SelfMediaPublishSchedule updated = captor.getValue();
        assertEquals(SelfMediaPublishScheduleConstants.STATUS_MANUAL_REQUIRED, response.getStatus());
        assertEquals(512, updated.getFailureMessage().length());
        assertTrue(longFailureMessage.startsWith(updated.getFailureMessage().substring(0, 12)));
        assertEquals(diagnosticsJson, updated.getDiagnosticsJson());
        verify(environmentLockService).release(101L);
    }

    @Test
    void markDistributionTaskScheduleFailedRetriesWhenNextAttemptProvided() {
        SelfMediaPublishSchedule row = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_FILLING);
        row.setId(100L);
        row.setDistributionTaskId(300L);
        row.setAttemptCount(1);
        row.setMaxAttempts(2);
        when(scheduleMapper.selectActiveByDistributionTaskId(300L)).thenReturn(row);
        LocalDateTime nextAttemptAt = LocalDateTime.of(2026, 6, 1, 11, 5);

        SelfMediaPublishScheduleVO response = service.markDistributionTaskScheduleFailed(
                300L,
                "WORKS_LIST_VERIFY_TIMEOUT",
                "works list not ready",
                "{\"ok\":false}",
                nextAttemptAt
        );

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_PENDING, response.getStatus());
        assertEquals(nextAttemptAt, response.getNextAttemptAt());
        assertEquals("WORKS_LIST_VERIFY_TIMEOUT", response.getFailureCode());
        verify(companyChannelQuotaService).refundDistribution(300L);
        verify(environmentLockService).release(100L);
    }

    private void stubCurrentTimeInsideBusinessWindow() {
        LocalDate today = LocalDate.now();
        doReturn(List.of(new BusinessCalendarService.BusinessDay(
                today,
                0,
                "工作日",
                1,
                false,
                List.of(new BusinessCalendarService.PublishWindow(
                        "test",
                        LocalTime.of(0, 0),
                        LocalTime.of(23, 59),
                        LocalTime.of(9, 15)
                ))
        ))).when(businessCalendarService).publishDays(any(), eq(false));
    }

    private void prepareValidArticleAndAccount() {
        when(articleDraftMapper.selectById(10L)).thenReturn(article());
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(brandMapper.selectById(8L)).thenReturn(brand());
        when(accountMapper.selectById(20L)).thenReturn(account());
        when(scheduleCapabilityService.readiness("toutiao", SelfMediaPublishScheduleConstants.STRATEGY_PLATFORM_SCHEDULE))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null, null));
    }

    private void stubRequestInsert() {
        when(requestMapper.insert(any(SelfMediaPublishScheduleRequest.class))).thenAnswer(invocation -> {
            SelfMediaPublishScheduleRequest row = invocation.getArgument(0);
            row.setId(50L);
            return 1;
        });
    }

    private SelfMediaPublishScheduleCreateRequest validRequest() {
        SelfMediaPublishScheduleCreateRequest request = new SelfMediaPublishScheduleCreateRequest();
        request.setBrandId(8L);
        request.setArticleIds(List.of(10L));
        request.setSelfMediaAccountIds(List.of(20L));
        request.setWindowStart(LocalDateTime.now().plusHours(3));
        request.setWindowEnd(LocalDateTime.now().plusHours(5));
        return request;
    }

    private SelfMediaPlatformQuickScheduleRequest quickRequest(String platform, boolean replaceNextScheduled) {
        SelfMediaPlatformQuickScheduleRequest request = new SelfMediaPlatformQuickScheduleRequest();
        request.setArticleId(10L);
        request.setPlatform(platform);
        request.setReplaceNextScheduled(replaceNextScheduled);
        return request;
    }

    private ArticleDraft article() {
        ArticleDraft row = new ArticleDraft();
        row.setId(10L);
        row.setProjectId(7L);
        row.setStatus("approved");
        row.setTitle("article title for check");
        row.setCoverImageUrl("https://cdn.example.test/cover.png");
        return row;
    }

    private Brand brand() {
        Brand row = new Brand();
        row.setId(8L);
        row.setCompanyId(6L);
        row.setSelfMediaPublishLocationName("阜阳");
        row.setCityName("西安");
        return row;
    }

    private Project project() {
        Project row = new Project();
        row.setId(7L);
        row.setBrandId(8L);
        return row;
    }

    private SelfMediaAccount account() {
        SelfMediaAccount row = new SelfMediaAccount();
        row.setId(20L);
        row.setBrandId(8L);
        row.setPlatform("toutiao");
        row.setStatus("active");
        return row;
    }

    private BrowserEnvironmentAccount binding() {
        BrowserEnvironmentAccount row = new BrowserEnvironmentAccount();
        row.setId(25L);
        row.setBrowserEnvironmentId(15L);
        row.setSelfMediaAccountId(20L);
        row.setPlatform("toutiao");
        return row;
    }

    private SelfMediaPublishSchedule existingActiveSchedule() {
        SelfMediaPublishSchedule row = new SelfMediaPublishSchedule();
        row.setId(99L);
        row.setArticleId(10L);
        row.setSelfMediaAccountId(20L);
        row.setStatus("pending");
        return row;
    }

    private SelfMediaPublishSchedule scheduleWithStatus(String status) {
        SelfMediaPublishSchedule row = new SelfMediaPublishSchedule();
        row.setId(90L);
        row.setBrandId(8L);
        row.setArticleId(10L);
        row.setSelfMediaAccountId(20L);
        row.setBrowserEnvironmentId(15L);
        row.setPlatform("toutiao");
        row.setStatus(status);
        return row;
    }

    private boolean isInBusinessAttemptWindow(LocalDateTime value) {
        LocalTime time = value.toLocalTime();
        return (!time.isBefore(LocalTime.of(9, 15)) && !time.isAfter(LocalTime.of(11, 30)))
                || (!time.isBefore(LocalTime.of(14, 30)) && !time.isAfter(LocalTime.of(17, 30)));
    }
}
