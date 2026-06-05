package com.huanjing.geo.module.content.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.module.content.constant.SelfMediaPublishScheduleConstants;
import com.huanjing.geo.module.content.dto.SelfMediaPublishScheduleCreateRequest;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.BrowserEnvironmentAccount;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.entity.SelfMediaPublishSchedule;
import com.huanjing.geo.module.content.entity.SelfMediaPublishScheduleRequest;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaPublishScheduleRequestMapper;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleAdapterRouter;
import com.huanjing.geo.module.content.service.adapter.SelfMediaPlatformScheduleRules;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleCreateResponse;
import com.huanjing.geo.module.content.vo.SelfMediaPublishScheduleVO;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.customer.entity.Brand;
import com.huanjing.geo.module.customer.mapper.BrandMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfMediaPublishScheduleServiceTest {
    private SelfMediaPublishScheduleMapper scheduleMapper;
    private SelfMediaPublishScheduleRequestMapper requestMapper;
    private ArticleDraftMapper articleDraftMapper;
    private SelfMediaAccountMapper accountMapper;
    private ProjectMapper projectMapper;
    private BrandMapper brandMapper;
    private BrowserEnvironmentService browserEnvironmentService;
    private SelfMediaScheduleCapabilityService scheduleCapabilityService;
    private SelfMediaPlatformScheduleAdapterRouter scheduleAdapterRouter;
    private SelfMediaPublishScheduleEnvironmentLockService environmentLockService;
    private BrandAccessService brandAccessService;
    private SelfMediaPublishScheduleService service;

    @BeforeEach
    void setUp() {
        scheduleMapper = mock(SelfMediaPublishScheduleMapper.class);
        requestMapper = mock(SelfMediaPublishScheduleRequestMapper.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
        accountMapper = mock(SelfMediaAccountMapper.class);
        projectMapper = mock(ProjectMapper.class);
        brandMapper = mock(BrandMapper.class);
        browserEnvironmentService = mock(BrowserEnvironmentService.class);
        scheduleCapabilityService = mock(SelfMediaScheduleCapabilityService.class);
        scheduleAdapterRouter = mock(SelfMediaPlatformScheduleAdapterRouter.class);
        when(scheduleAdapterRouter.rules(anyString(), anyString()))
                .thenReturn(new SelfMediaPlatformScheduleRules(130, 120, 4));
        environmentLockService = mock(SelfMediaPublishScheduleEnvironmentLockService.class);
        brandAccessService = mock(BrandAccessService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        SysUser user = new SysUser();
        user.setId(99L);
        when(currentUserService.requireCurrentUser()).thenReturn(user);

        service = new SelfMediaPublishScheduleService(
                scheduleMapper,
                requestMapper,
                articleDraftMapper,
                accountMapper,
                projectMapper,
                brandMapper,
                browserEnvironmentService,
                scheduleCapabilityService,
                scheduleAdapterRouter,
                environmentLockService,
                mock(ContentDistributionService.class),
                brandAccessService,
                currentUserService,
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
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class))).thenReturn(null);
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
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class))).thenReturn(binding());
        when(scheduleMapper.selectActiveByBaseIdempotencyKey(anyString(), any())).thenReturn(existingActiveSchedule());
        stubRequestInsert();

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(validRequest(), "new-key");

        assertTrue(response.getCreatedSchedules().isEmpty());
        assertEquals(1, response.getRejectedItems().size());
        assertEquals("ACTIVE_SCHEDULE_EXISTS", response.getRejectedItems().get(0).getCode());
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void createSchedules_rejectsWhenPlatformCapabilityNotVerified() {
        prepareValidArticleAndAccount();
        when(scheduleCapabilityService.readiness("toutiao"))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(
                        false,
                        "PLATFORM_CAPABILITY_UNVERIFIED",
                        "平台定时发布能力尚未验证"
                ));
        stubRequestInsert();

        SelfMediaPublishScheduleCreateResponse response = service.createSchedules(validRequest(), "new-key");

        assertTrue(response.getCreatedSchedules().isEmpty());
        assertEquals(1, response.getRejectedItems().size());
        assertEquals("PLATFORM_CAPABILITY_UNVERIFIED", response.getRejectedItems().get(0).getCode());
        assertEquals("全自动排期 > 平台能力验证", response.getRejectedItems().get(0).getSettingPath());
        verify(browserEnvironmentService, never()).validateForTaskCreation(any(SelfMediaAccount.class));
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void createSchedules_rejectsToutiaoPlatformScheduleWhenTimeTooClose() {
        prepareValidArticleAndAccount();
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class))).thenReturn(binding());
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
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class))).thenReturn(binding());
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
        assertEquals(plannedAt.minusMinutes(130), captor.getValue().getNextAttemptAt());
        assertEquals(4, captor.getValue().getMaxAttempts());
        assertEquals("article title for check", captor.getValue().getPublishCheckTitle());
        assertEquals("https://cdn.example.test/cover.png", captor.getValue().getPublishCheckCoverUrl());
        assertEquals("阜阳", captor.getValue().getPublishCheckLocationName());
        assertTrue(captor.getValue().getPublishCheckFingerprint().matches("[0-9a-f]{64}"));
    }

    @Test
    void createSystemSchedulesUsesProvidedOperatorWithoutBrandAccessCheck() {
        prepareValidArticleAndAccount();
        when(browserEnvironmentService.validateForTaskCreation(any(SelfMediaAccount.class))).thenReturn(binding());
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
        when(scheduleMapper.selectPage(any(), any())).thenReturn(mapperPage);

        Page<SelfMediaPublishScheduleVO> response = service.pageSchedules(
                null, "toutiao", SelfMediaPublishScheduleConstants.STATUS_PENDING, null, null, 1L, 20L);

        assertEquals(1, response.getTotal());
        assertEquals(30L, response.getRecords().get(0).getId());
        verify(brandAccessService).listAccessibleBrandIds(99L, BrandAccessAction.OPERATE);
        verify(brandAccessService, never()).requireBrandAccess(anyLong(), anyLong(), any());
        verify(scheduleMapper).selectPage(any(), any());
    }

    @Test
    void pageSchedulesWithoutAccessibleBrandReturnsEmptyPage() {
        when(brandAccessService.listAccessibleBrandIds(99L, BrandAccessAction.OPERATE)).thenReturn(List.of());

        Page<SelfMediaPublishScheduleVO> response = service.pageSchedules(null, null, null, null, null, 1L, 20L);

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
        verify(environmentLockService).release(93L);
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
        verify(environmentLockService).release(94L);
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
    void claimNextPublishCheckForLocalAgentClaimsScheduledPublishCheckQueue() {
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
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_UNKNOWN
                )),
                any(),
                eq(10),
                eq(99L),
                eq("toutiao")
        )).thenReturn(List.of(candidate));
        when(environmentLockService.tryAcquire(eq(15L), eq(103L), any(), any())).thenReturn(true);
        when(scheduleMapper.claimQueueSchedule(
                eq(103L),
                eq(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK),
                eq(List.of(
                        SelfMediaPublishScheduleConstants.STATUS_SCHEDULED,
                        SelfMediaPublishScheduleConstants.STATUS_PUBLISH_DUE,
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
    void markClaimedScheduledReleasesEnvironmentLockAtSuccessTerminalState() {
        SelfMediaPublishSchedule scheduling = scheduleWithStatus(SelfMediaPublishScheduleConstants.STATUS_SCHEDULING);
        scheduling.setId(102L);
        LocalDateTime publishAt = LocalDateTime.of(2026, 6, 1, 18, 30);
        scheduling.setPlatformScheduledAt(publishAt);
        when(scheduleMapper.selectById(102L)).thenReturn(scheduling);

        SelfMediaPublishScheduleVO response = service.markClaimedScheduled(102L, "platform-schedule-1", "{\"ok\":true}");

        assertEquals(SelfMediaPublishScheduleConstants.STATUS_SCHEDULED, response.getStatus());
        assertEquals("platform-schedule-1", response.getPlatformScheduleId());
        assertEquals(SelfMediaPublishScheduleConstants.QUEUE_PUBLISH_RESULT_CHECK, response.getQueueKind());
        assertEquals(publishAt, response.getNextAttemptAt());
        verify(scheduleMapper).updateById(scheduling);
        verify(environmentLockService).release(102L);
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
        verify(environmentLockService).release(100L);
    }

    private void prepareValidArticleAndAccount() {
        when(articleDraftMapper.selectById(10L)).thenReturn(article());
        when(projectMapper.selectById(7L)).thenReturn(project());
        when(brandMapper.selectById(8L)).thenReturn(brand());
        when(accountMapper.selectById(20L)).thenReturn(account());
        when(scheduleCapabilityService.readiness("toutiao"))
                .thenReturn(new SelfMediaScheduleCapabilityService.PlatformScheduleReadiness(true, null, null));
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
}
