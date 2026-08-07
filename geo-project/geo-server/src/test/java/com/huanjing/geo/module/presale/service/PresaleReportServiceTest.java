package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.partner.service.PartnerPresaleReportQuotaService;
import com.huanjing.geo.module.presale.access.PresaleAccessService;
import com.huanjing.geo.module.presale.dto.request.CreateReportRequest;
import com.huanjing.geo.module.presale.dto.response.ReportScopePreviewVO;
import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import com.huanjing.geo.module.presale.generate.PresaleGenerateOrchestrator;
import com.huanjing.geo.module.presale.generate.web.PresaleQueryWebMode;
import com.huanjing.geo.module.presale.generate.web.PresaleWebExecutionContext;
import com.huanjing.geo.module.presale.generate.web.PresaleWebReadinessChecker;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresalePromptTemplateMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.mapper.SysDictItemMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class PresaleReportServiceTest {

    private static final Long REPORT_ID = 9_000_001L;
    private static final Long USER_ID = 1_000_001L;

    @Mock
    private PresaleReportMapper reportMapper;
    @Mock
    private PresaleReportVersionMapper versionMapper;
    @Mock
    private PresaleReportExportMapper exportMapper;
    @Mock
    private PresaleGenerateOrchestrator orchestrator;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PresaleAccessService accessService;
    @Mock
    private AiPlatformConfigMapper aiPlatformConfigMapper;
    @Mock
    private PresaleAiPromptResultMapper aiPromptResultMapper;
    @Mock
    private PresalePromptTemplateMapper promptTemplateMapper;
    @Mock
    private PresaleReportVersionPromptTemplateMapper versionPromptTemplateMapper;
    @Mock
    private PromptTemplateDraftValidator promptTemplateDraftValidator;
    @Mock
    private LlmPromptQuestionDraftValidator llmPromptQuestionDraftValidator;
    @Mock
    private PartnerPresaleReportQuotaService partnerPresaleReportQuotaService;
    @Mock
    private PresaleWebReadinessChecker webReadinessChecker;
    @Mock
    private SysDictItemMapper sysDictItemMapper;
    @Mock
    private PresaleBenchmarkIndustryClassifier benchmarkIndustryClassifier;

    private PresaleReportService service;

    @BeforeEach
    void setUp() {
        service = new PresaleReportService(
                reportMapper,
                versionMapper,
                exportMapper,
                orchestrator,
                currentUserService,
                accessService,
                aiPlatformConfigMapper,
                aiPromptResultMapper,
                promptTemplateMapper,
                versionPromptTemplateMapper,
                promptTemplateDraftValidator,
                llmPromptQuestionDraftValidator,
                partnerPresaleReportQuotaService,
                webReadinessChecker,
                new ObjectMapper(),
                sysDictItemMapper,
                benchmarkIndustryClassifier
        );
        lenient().when(webReadinessChecker.checkConfiguredMode())
                .thenReturn(new PresaleWebExecutionContext(PresaleQueryWebMode.OFF, Map.of()));
        lenient().when(webReadinessChecker.configuredMode()).thenReturn(PresaleQueryWebMode.OFF);
        lenient().when(benchmarkIndustryClassifier.classifyDirectlyOrDefer(any()))
                .thenReturn(new PresaleBenchmarkIndustryClassifier.Classification(
                        "restaurant", "DIRECT", "HIGH", null));
    }

    @Test
    void createReport_returnsExistingReportWhenPartnerRequestIsIdempotent() {
        CreateReportRequest req = createRequest();
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(partnerPresaleReportQuotaService.reserveIfPartner(any(), any()))
                .thenReturn(new PartnerPresaleReportQuotaService.Reservation(
                        true, REPORT_ID, null, null, null, null, null, null
                ));

        Long reportId = service.createReport(req);

        assertEquals(REPORT_ID, reportId);
        verify(currentUserService).ensurePermission("presale.report.create");
        verify(reportMapper, never()).insert(any());
        verify(versionMapper, never()).insert(any());
    }

    @Test
    void createReport_rejectsSpecifiedCompetitorGroupLongerThanDatabaseColumn() {
        CreateReportRequest req = createRequest();
        req.setSpecifiedCompetitors(List.of(
                "甲".repeat(33),
                "乙".repeat(33),
                "丙".repeat(33)
        ));
        when(currentUserService.requireCurrentUser()).thenReturn(user());

        BizException ex = assertThrows(BizException.class, () -> service.createReport(req));

        assertEquals(400, ex.getCode());
        verify(reportMapper, never()).insert(any());
    }

    @Test
    void createReport_rejectsRepresentedBrandsForNonAgentRole() {
        CreateReportRequest req = createRequest();
        req.setRepresentedBrands(List.of("上游品牌A"));
        when(currentUserService.requireCurrentUser()).thenReturn(user());

        BizException ex = assertThrows(BizException.class, () -> service.createReport(req));

        assertEquals(400, ex.getCode());
        verify(reportMapper, never()).insert(any());
    }

    @Test
    void createReport_rejectsDuplicateRepresentedBrands() {
        CreateReportRequest req = createRequest();
        req.setIndustryRole("dealer");
        req.setRepresentedBrands(List.of("上游品牌A", " 上游品牌A "));
        when(currentUserService.requireCurrentUser()).thenReturn(user());

        BizException ex = assertThrows(BizException.class, () -> service.createReport(req));

        assertEquals(400, ex.getCode());
        verify(reportMapper, never()).insert(any());
    }

    @Test
    void createReport_defersManualIndustryClassificationUntilBackgroundGeneration() {
        CreateReportRequest req = createRequest();
        req.setIndustry("spa休闲会所");
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(partnerPresaleReportQuotaService.reserveIfPartner(any(), any()))
                .thenReturn(new PartnerPresaleReportQuotaService.Reservation(
                        false, null, null, null, null, null, null, null
                ));
        when(benchmarkIndustryClassifier.classifyDirectlyOrDefer("spa休闲会所"))
                .thenReturn(new PresaleBenchmarkIndustryClassifier.Classification(null, "PENDING", null, null));
        when(promptTemplateDraftValidator.validateAndBuildSnapshots(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        doAnswer(invocation -> {
            invocation.getArgument(0, PresaleReport.class).setId(REPORT_ID);
            return 1;
        }).when(reportMapper).insert(any(PresaleReport.class));
        doAnswer(invocation -> {
            invocation.getArgument(0, com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion.class)
                    .setId(REPORT_ID + 1);
            return 1;
        }).when(versionMapper).insert(any(com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion.class));

        Long reportId = service.createReport(req);

        assertEquals(REPORT_ID, reportId);
        ArgumentCaptor<com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion> versionCaptor =
                ArgumentCaptor.forClass(com.huanjing.geo.module.presale.persist.entity.PresaleReportVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertEquals("PENDING", versionCaptor.getValue().getIndustryClassificationSource());
        assertNull(versionCaptor.getValue().getBenchmarkIndustryKey());
        verify(benchmarkIndustryClassifier).classifyDirectlyOrDefer("spa休闲会所");
        verify(benchmarkIndustryClassifier, never()).classify(any(), any(), any(Boolean.class));
    }

    @Test
    void scopePreviewUsesRequiredExecutionPlatformsIncludingCompanionOnlyChannel() {
        when(webReadinessChecker.configuredMode()).thenReturn(PresaleQueryWebMode.REQUIRED);
        when(aiPlatformConfigMapper.selectCount(any())).thenReturn(1L);
        when(aiPromptResultMapper.selectTemplateIntentStats(any())).thenReturn(List.of());

        ReportScopePreviewVO preview = service.getScopePreview();

        assertEquals(1, preview.getPlatformCount());
        verify(aiPlatformConfigMapper).selectCount(any());
    }

    @Test
    void deleteReport_marksReportDeletedWhenNoActiveWork() {
        PresaleReport report = report();
        when(accessService.requireReportWithAccess(REPORT_ID)).thenReturn(report);
        when(accessService.canEditCurrentUser(report)).thenReturn(true);
        when(versionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 0L);
        when(exportMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(currentUserService.requireCurrentUser()).thenReturn(user());

        service.deleteReport(REPORT_ID);

        ArgumentCaptor<PresaleReport> captor = ArgumentCaptor.forClass(PresaleReport.class);
        verify(currentUserService).ensurePermission("presale.report.delete");
        verify(reportMapper).updateById(captor.capture());
        PresaleReport updated = captor.getValue();
        assertEquals(REPORT_ID, updated.getId());
        assertEquals(USER_ID, updated.getDeletedBy());
        assertNotNull(updated.getDeletedAt());
        assertNotNull(updated.getUpdatedAt());
    }

    @Test
    void deleteReport_blocksActiveGeneration() {
        PresaleReport report = report();
        when(accessService.requireReportWithAccess(REPORT_ID)).thenReturn(report);
        when(accessService.canEditCurrentUser(report)).thenReturn(true);
        when(versionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.deleteReport(REPORT_ID));

        assertEquals(409, ex.getCode());
        verify(exportMapper, never()).selectCount(any(LambdaQueryWrapper.class));
        verify(reportMapper, never()).updateById(any());
    }

    @Test
    void deleteReport_blocksActiveExport() {
        PresaleReport report = report();
        when(accessService.requireReportWithAccess(REPORT_ID)).thenReturn(report);
        when(accessService.canEditCurrentUser(report)).thenReturn(true);
        when(versionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(exportMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.deleteReport(REPORT_ID));

        assertEquals(409, ex.getCode());
        verify(reportMapper, never()).updateById(any());
    }

    @Test
    void deleteReport_blocksFrozenVersion() {
        PresaleReport report = report();
        when(accessService.requireReportWithAccess(REPORT_ID)).thenReturn(report);
        when(accessService.canEditCurrentUser(report)).thenReturn(true);
        when(versionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 1L);
        when(exportMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        BizException ex = assertThrows(BizException.class, () -> service.deleteReport(REPORT_ID));

        assertEquals(409, ex.getCode());
        verify(reportMapper, never()).updateById(any());
    }

    private static PresaleReport report() {
        PresaleReport report = new PresaleReport();
        report.setId(REPORT_ID);
        report.setCreatedBy(USER_ID);
        return report;
    }

    private static SysUser user() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setIsActive(true);
        return user;
    }

    private static CreateReportRequest createRequest() {
        CreateReportRequest req = new CreateReportRequest();
        req.setRequestId("REQ-1");
        req.setBrandName("三和口腔");
        req.setIndustry("healthcare");
        req.setIndustryRole("chain_brand");
        req.setRegion("上海");
        return req;
    }
}
