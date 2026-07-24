package com.huanjing.geo.module.mobiledashboard.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MobileDashboardOpsServicePermissionTest {
    private CurrentUserService currentUserService;
    private MobileDashboardOpsService service;

    @BeforeEach
    void setUp() {
        currentUserService = mock(CurrentUserService.class);
        service = new MobileDashboardOpsService(
                mock(JdbcTemplate.class),
                mock(ProjectMapper.class),
                currentUserService,
                mock(InternalScopeService.class)
        );
    }

    @Test
    void fullOperationsRetainsExportPermission() {
        doThrow(new BizException(403, "forbidden"))
                .when(currentUserService).ensurePermission("project.report.export");

        assertThrows(BizException.class, () -> service.operations(11L, null, null));

        verify(currentUserService).ensurePermission("project.report.export");
    }

    @Test
    void shareAccessSummaryUsesReadPermissionOnly() {
        doThrow(new BizException(403, "forbidden"))
                .when(currentUserService).ensurePermission("project.read");

        assertThrows(BizException.class, () -> service.shareAccessSummary(11L));

        verify(currentUserService).ensurePermission("project.read");
    }
}
