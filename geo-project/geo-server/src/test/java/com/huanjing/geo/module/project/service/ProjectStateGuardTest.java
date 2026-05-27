package com.huanjing.geo.module.project.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectStateGuardTest {

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private InternalScopeService internalScopeService;

    @InjectMocks
    private ProjectStateGuard projectStateGuard;

    @Test
    void partnerStaff_canEditDraftProject() {
        SysUser operator = partnerStaff(100L);
        Project project = partnerProject(100L);
        project.setStatus("paused");

        assertDoesNotThrow(() -> projectStateGuard.ensureCanEditBasicInfo(project, operator));
    }

    @Test
    void partnerStaff_cannotStartProject() {
        SysUser operator = partnerStaff(100L);
        Project project = partnerProject(100L);

        when(currentUserService.isPartnerUser(operator)).thenReturn(true);

        BizException ex = assertThrows(
                BizException.class,
                () -> projectStateGuard.ensureCanStart(project, operator)
        );

        assertEquals(403, ex.getCode());
        assertEquals("Only partner administrator can start partner project", ex.getMessage());
    }

    @Test
    void partnerStaff_cannotStartOtherPartnerProject() {
        SysUser operator = partnerStaff(100L);
        Project project = partnerProject(200L);

        doThrow(new BizException(403, "No permission to access this project"))
                .when(currentUserService)
                .ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");

        BizException ex = assertThrows(
                BizException.class,
                () -> projectStateGuard.ensureCanStart(project, operator)
        );

        assertEquals(403, ex.getCode());
        assertEquals("No permission to access this project", ex.getMessage());
    }

    private SysUser partnerStaff(Long partnerId) {
        SysUser user = new SysUser();
        user.setId(10L);
        user.setRole("partner_staff");
        user.setPartnerId(partnerId);
        return user;
    }

    private Project partnerProject(Long partnerId) {
        Project project = new Project();
        project.setId(20L);
        project.setOwnerType("partner");
        project.setPartnerId(partnerId);
        project.setStatus("paused");
        return project;
    }
}
