package com.huanjing.geo.module.presale.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.presale.access.PresaleAccessService;
import com.huanjing.geo.module.presale.export.persist.mapper.PresaleReportExportMapper;
import com.huanjing.geo.module.presale.generate.PresaleGenerateOrchestrator;
import com.huanjing.geo.module.presale.persist.entity.PresaleReport;
import com.huanjing.geo.module.presale.persist.mapper.PresaleAiPromptResultMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresalePromptTemplateMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionMapper;
import com.huanjing.geo.module.presale.persist.mapper.PresaleReportVersionPromptTemplateMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.AiPlatformConfigMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                promptTemplateDraftValidator
        );
    }

    @Test
    void deleteReport_marksReportDeletedWhenNoActiveWork() {
        PresaleReport report = report();
        when(accessService.requireReportWithAccess(REPORT_ID)).thenReturn(report);
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
        when(accessService.requireReportWithAccess(REPORT_ID)).thenReturn(report());
        when(versionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.deleteReport(REPORT_ID));

        assertEquals(409, ex.getCode());
        verify(exportMapper, never()).selectCount(any(LambdaQueryWrapper.class));
        verify(reportMapper, never()).updateById(any());
    }

    @Test
    void deleteReport_blocksActiveExport() {
        when(accessService.requireReportWithAccess(REPORT_ID)).thenReturn(report());
        when(versionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(exportMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.deleteReport(REPORT_ID));

        assertEquals(409, ex.getCode());
        verify(reportMapper, never()).updateById(any());
    }

    @Test
    void deleteReport_blocksFrozenVersion() {
        when(accessService.requireReportWithAccess(REPORT_ID)).thenReturn(report());
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
}
