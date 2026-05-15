package com.huanjing.geo.module.extension.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.audit.AuditMode;
import com.huanjing.geo.module.audit.AuditResult;
import com.huanjing.geo.module.audit.dto.AuditEvent;
import com.huanjing.geo.module.audit.service.AuditService;
import com.huanjing.geo.module.content.ContentErrorCodes;
import com.huanjing.geo.module.content.entity.ArticleDraft;
import com.huanjing.geo.module.content.entity.DistributionTask;
import com.huanjing.geo.module.content.mapper.ArticleDraftMapper;
import com.huanjing.geo.module.content.mapper.DistributionTaskMapper;
import com.huanjing.geo.module.content.service.CompanyChannelQuotaService;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessErrorCodes;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import com.huanjing.geo.module.extension.ExtensionErrorCodes;
import com.huanjing.geo.module.extension.dto.ExtensionTaskPublishReportRequest;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExtensionTaskStateServiceTest {

    private DistributionTaskMapper taskMapper;
    private ArticleDraftMapper articleDraftMapper;
    private ProjectMapper projectMapper;
    private BrandAccessService brandAccessService;
    private CompanyChannelQuotaService companyChannelQuotaService;
    private ExtensionRedisStore redisStore;
    private ExtensionAuditSupport auditSupport;
    private AuditService auditService;
    private ExtensionTaskStateService service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(DistributionTaskMapper.class);
        articleDraftMapper = mock(ArticleDraftMapper.class);
        projectMapper = mock(ProjectMapper.class);
        brandAccessService = mock(BrandAccessService.class);
        companyChannelQuotaService = mock(CompanyChannelQuotaService.class);
        redisStore = mock(ExtensionRedisStore.class);
        auditSupport = mock(ExtensionAuditSupport.class);
        auditService = mock(AuditService.class);
        service = new ExtensionTaskStateService(
                taskMapper,
                articleDraftMapper,
                projectMapper,
                brandAccessService,
                companyChannelQuotaService,
                redisStore,
                auditSupport,
                auditService
        );
    }

    @Test
    void ackFilledUpdatesFillingTaskAndAuditsSuccess() {
        stubTask("filling");
        when(taskMapper.markSemiAutoFilled(eq(30L), any())).thenReturn(1);

        assertEquals("filled", service.ackFilled(30L, 99L, 7L).status());

        verify(brandAccessService).requireBrandAccess(10L, 99L, BrandAccessAction.OPERATE);
        verify(taskMapper).markSemiAutoFilled(eq(30L), any());
        verify(auditSupport).record(
                eq("SEMI_AUTO_TASK_FILLED"),
                eq(AuditResult.SUCCESS),
                eq(AuditMode.SYNC),
                eq(false),
                eq(99L),
                eq(10L),
                eq(20L),
                eq(30L),
                eq(7L),
                eq("DISTRIBUTION_TASK"),
                eq("30"),
                eq(null),
                eq(null),
                any()
        );
    }

    @Test
    void duplicateAckWritesDeniedAudit() {
        stubTask("filling");
        when(taskMapper.markSemiAutoFilled(eq(30L), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.ackFilled(30L, 99L, 7L));

        assertEquals(ExtensionErrorCodes.TASK_STATE_CONFLICT, ex.getCode());
        verify(auditSupport).record(
                eq("SEMI_AUTO_TASK_FILLED"),
                eq(AuditResult.DENIED),
                eq(AuditMode.SYNC),
                eq(false),
                eq(99L),
                eq(10L),
                eq(20L),
                eq(30L),
                eq(7L),
                eq("DISTRIBUTION_TASK"),
                eq("30"),
                eq(String.valueOf(ExtensionErrorCodes.TASK_STATE_CONFLICT)),
                eq("STALE_STATE"),
                any()
        );
    }

    @Test
    void ackNonFillingTaskIsRejectedByConditionalUpdate() {
        stubTask("pending");
        when(taskMapper.markSemiAutoFilled(eq(30L), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.ackFilled(30L, 99L, 7L));

        assertEquals(ExtensionErrorCodes.TASK_STATE_CONFLICT, ex.getCode());
        verify(auditSupport).record(eq("SEMI_AUTO_TASK_FILLED"), eq(AuditResult.DENIED), any(), any(Boolean.class),
                any(), any(), any(), any(), any(), any(), any(), any(), eq("STALE_STATE"), any());
    }

    @Test
    void ackBrandAccessDeniedDoesNotUpdateTask() {
        stubTask("filling");
        when(brandAccessService.requireBrandAccess(10L, 99L, BrandAccessAction.OPERATE))
                .thenThrow(new BizException(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, "denied"));

        BizException ex = assertThrows(BizException.class, () -> service.ackFilled(30L, 99L, 7L));

        assertEquals(BrandAccessErrorCodes.BRAND_ACCESS_DENIED, ex.getCode());
        verify(taskMapper, never()).markSemiAutoFilled(any(), any());
        verify(auditSupport).record(
                eq("SEMI_AUTO_TASK_FILLED"),
                eq(AuditResult.DENIED),
                eq(AuditMode.SYNC),
                eq(false),
                eq(99L),
                eq(10L),
                eq(20L),
                eq(30L),
                eq(7L),
                eq("DISTRIBUTION_TASK"),
                eq("30"),
                eq(String.valueOf(BrandAccessErrorCodes.BRAND_ACCESS_DENIED)),
                eq("denied"),
                any()
        );
    }

    @Test
    void firstHeartbeatTouchesFillingTaskAndAuditsStart() {
        stubTask("filling");
        when(redisStore.incrementWithTtl(eq("extension:task:heartbeat:30"), any(Duration.class))).thenReturn(1L);
        when(taskMapper.touchSemiAutoHeartbeat(eq(30L), any())).thenReturn(1);

        assertEquals("filling", service.heartbeat(30L, 99L, 7L).status());

        verify(taskMapper).touchSemiAutoHeartbeat(eq(30L), any());
        verify(auditSupport).record(
                eq("SEMI_AUTO_TASK_HEARTBEAT_STARTED"),
                eq(AuditResult.SUCCESS),
                eq(AuditMode.SYNC),
                eq(false),
                eq(99L),
                eq(10L),
                eq(20L),
                eq(30L),
                eq(7L),
                eq("DISTRIBUTION_TASK"),
                eq("30"),
                eq(null),
                eq(null),
                any()
        );
        verify(auditSupport, never()).record(eq("SEMI_AUTO_TASK_HEARTBEAT_DENIED"), any(), any(), any(Boolean.class),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void heartbeatRateLimitRejectsSecondCall() {
        stubTask("filling");
        when(redisStore.incrementWithTtl(eq("extension:task:heartbeat:30"), any(Duration.class))).thenReturn(2L);

        BizException ex = assertThrows(BizException.class, () -> service.heartbeat(30L, 99L, 7L));

        assertEquals(ExtensionErrorCodes.TASK_RATE_LIMITED, ex.getCode());
        verify(taskMapper, never()).touchSemiAutoHeartbeat(any(), any());
    }

    @Test
    void heartbeatAcceptsFilledTaskAfterAckAndKeepsFilledStatus() {
        DistributionTask task = task("filled");
        task.setFilledAt(LocalDateTime.now());
        when(taskMapper.selectById(30L)).thenReturn(task);
        Project project = new Project();
        project.setId(40L);
        project.setBrandId(10L);
        when(projectMapper.selectById(40L)).thenReturn(project);
        when(redisStore.incrementWithTtl(eq("extension:task:heartbeat:30"), any(Duration.class))).thenReturn(1L);
        when(taskMapper.touchSemiAutoHeartbeat(eq(30L), any())).thenReturn(1);

        assertEquals("filled", service.heartbeat(30L, 99L, 7L).status());

        verify(taskMapper).touchSemiAutoHeartbeat(eq(30L), any());
        verify(auditSupport).record(eq("SEMI_AUTO_TASK_HEARTBEAT_STARTED"), eq(AuditResult.SUCCESS), any(),
                any(Boolean.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void publishedUpdatesFilledTaskAndAuditsSuccess() {
        stubTask("filled");
        when(taskMapper.markSemiAutoPublished(eq(30L), any(), eq(99L))).thenReturn(1);
        when(articleDraftMapper.update(any(), any())).thenReturn(1);

        assertEquals("published", service.published(30L, 99L, 7L).status());

        verify(taskMapper).markSemiAutoPublished(eq(30L), any(), eq(99L));
        ArgumentCaptor<ArticleDraft> articleUpdate = forClass(ArticleDraft.class);
        verify(articleDraftMapper).update(articleUpdate.capture(), any());
        assertEquals("published", articleUpdate.getValue().getStatus());
        assertNotNull(articleUpdate.getValue().getPublishedAt());
        verify(companyChannelQuotaService).confirmDistribution(30L);
        verify(auditSupport).record(
                eq("SEMI_AUTO_TASK_PUBLISHED"),
                eq(AuditResult.SUCCESS),
                eq(AuditMode.SYNC),
                eq(false),
                eq(99L),
                eq(10L),
                eq(20L),
                eq(30L),
                eq(7L),
                eq("DISTRIBUTION_TASK"),
                eq("30"),
                eq(null),
                eq(null),
                any()
        );
    }

    @Test
    void publishedAuditsPlatformCompletionReport() {
        stubTask("filled");
        when(taskMapper.markSemiAutoPublished(eq(30L), any(), eq(99L))).thenReturn(1);
        when(articleDraftMapper.update(any(), any())).thenReturn(1);

        assertEquals("published", service.published(30L, 99L, 7L, new ExtensionTaskPublishReportRequest(
                "draft_saved_clicked",
                "https://mp.toutiao.com/editor",
                "toutiao",
                "保存草稿"
        )).status());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditSupport).record(
                eq("SEMI_AUTO_TASK_PUBLISHED"),
                eq(AuditResult.SUCCESS),
                eq(AuditMode.SYNC),
                eq(false),
                eq(99L),
                eq(10L),
                eq(20L),
                eq(30L),
                eq(7L),
                eq("DISTRIBUTION_TASK"),
                eq("30"),
                eq(null),
                eq(null),
                detailCaptor.capture()
        );
        assertEquals("draft_saved_clicked", detailCaptor.getValue().get("action"));
        assertEquals("toutiao", detailCaptor.getValue().get("platform"));
        assertEquals("保存草稿", detailCaptor.getValue().get("detectedText"));
    }

    @Test
    void publishedNonFilledTaskIsRejected() {
        stubTask("filling");
        when(taskMapper.markSemiAutoPublished(eq(30L), any(), eq(99L))).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.published(30L, 99L, 7L));

        assertEquals(ExtensionErrorCodes.TASK_STATE_CONFLICT, ex.getCode());
        verifyNoInteractions(articleDraftMapper);
        verifyNoInteractions(companyChannelQuotaService);
    }

    @Test
    void publishedArticleStateConflictDoesNotConfirmQuota() {
        stubTask("filled");
        when(taskMapper.markSemiAutoPublished(eq(30L), any(), eq(99L))).thenReturn(1);
        when(articleDraftMapper.update(any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.published(30L, 99L, 7L));

        assertEquals(ContentErrorCodes.ARTICLE_STATE_CONFLICT, ex.getCode());
        verify(articleDraftMapper).update(any(), any());
        verifyNoInteractions(companyChannelQuotaService);
    }

    @Test
    void abandonMarksTaskFailedRestoresArticleAndRefundsQuota() {
        stubTask("filled");
        when(taskMapper.abandonSemiAutoTask(eq(30L), any(), any())).thenReturn(1);
        when(articleDraftMapper.update(any(), any())).thenReturn(1);

        assertEquals("failed", service.abandon(30L, 99L, 7L).status());

        verify(taskMapper).abandonSemiAutoTask(eq(30L), any(), any());
        ArgumentCaptor<ArticleDraft> articleUpdate = forClass(ArticleDraft.class);
        verify(articleDraftMapper).update(articleUpdate.capture(), any());
        assertEquals("approved", articleUpdate.getValue().getStatus());
        verify(companyChannelQuotaService).refundDistribution(30L);
        verify(auditSupport).record(
                eq("SEMI_AUTO_TASK_ABANDONED"),
                eq(AuditResult.SUCCESS),
                eq(AuditMode.SYNC),
                eq(false),
                eq(99L),
                eq(10L),
                eq(20L),
                eq(30L),
                eq(7L),
                eq("DISTRIBUTION_TASK"),
                eq("30"),
                eq(null),
                eq(null),
                any()
        );
    }

    @Test
    void abandonStateConflictDoesNotRestoreArticleOrRefundQuota() {
        stubTask("published");
        when(taskMapper.abandonSemiAutoTask(eq(30L), any(), any())).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.abandon(30L, 99L, 7L));

        assertEquals(ExtensionErrorCodes.TASK_STATE_CONFLICT, ex.getCode());
        verifyNoInteractions(articleDraftMapper);
        verifyNoInteractions(companyChannelQuotaService);
    }

    @Test
    void reclaimTokenIssuedStaleTaskReturnsItToTokenIssuedWithFreshIssuedAt() {
        DistributionTask task = task("token_issued");
        task.setFillTokenIssuedAt(LocalDateTime.now().minusMinutes(20));
        when(taskMapper.selectStaleSemiAutoTasks(any(), any(), eq(100))).thenReturn(List.of(task));
        when(taskMapper.reclaimSemiAutoTask(eq(30L), eq("token_issued"), any())).thenReturn(1);
        when(articleDraftMapper.update(any(), any())).thenReturn(1);

        assertEquals(1, service.reclaimStaleTasks());

        verify(taskMapper).reclaimSemiAutoTask(eq(30L), eq("token_issued"), any());
        verify(articleDraftMapper).update(any(), any());
        verify(auditService).record(any(AuditEvent.class));
    }

    @Test
    void reclaimFillingStaleTaskReturnsItToTokenIssued() {
        DistributionTask task = task("filling");
        task.setLastHeartbeatAt(LocalDateTime.now().minusMinutes(20));
        when(taskMapper.selectStaleSemiAutoTasks(any(), any(), eq(100))).thenReturn(List.of(task));
        when(taskMapper.reclaimSemiAutoTask(eq(30L), eq("filling"), any())).thenReturn(1);
        when(articleDraftMapper.update(any(), any())).thenReturn(1);

        assertEquals(1, service.reclaimStaleTasks());

        verify(taskMapper).reclaimSemiAutoTask(eq(30L), eq("filling"), any());
    }

    @Test
    void reclaimFilledStaleTaskReturnsItToTokenIssued() {
        DistributionTask task = task("filled");
        task.setFilledAt(LocalDateTime.now().minusMinutes(20));
        when(taskMapper.selectStaleSemiAutoTasks(any(), any(), eq(100))).thenReturn(List.of(task));
        when(taskMapper.reclaimSemiAutoTask(eq(30L), eq("filled"), any())).thenReturn(1);
        when(articleDraftMapper.update(any(), any())).thenReturn(1);

        assertEquals(1, service.reclaimStaleTasks());

        verify(taskMapper).reclaimSemiAutoTask(eq(30L), eq("filled"), any());
    }

    @Test
    void reclaimDoesNotTouchActiveHeartbeatTasks() {
        when(taskMapper.selectStaleSemiAutoTasks(any(), any(), eq(100))).thenReturn(List.of());

        assertEquals(0, service.reclaimStaleTasks());

        verify(taskMapper, never()).reclaimSemiAutoTask(any(), any(), any());
    }

    private void stubTask(String status) {
        when(taskMapper.selectById(30L)).thenReturn(task(status));
        Project project = new Project();
        project.setId(40L);
        project.setBrandId(10L);
        when(projectMapper.selectById(40L)).thenReturn(project);
    }

    private DistributionTask task(String status) {
        DistributionTask task = new DistributionTask();
        task.setId(30L);
        task.setArticleId(50L);
        task.setProjectId(40L);
        task.setSelfMediaAccountId(20L);
        task.setDispatchMode("SEMI_AUTO");
        task.setStatus(status);
        return task;
    }
}
