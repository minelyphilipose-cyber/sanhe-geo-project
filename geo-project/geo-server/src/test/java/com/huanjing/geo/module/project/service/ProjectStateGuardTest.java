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
        project.setStatus("draft");

        when(currentUserService.isPartnerUser(operator)).thenReturn(true);

        assertDoesNotThrow(() -> projectStateGuard.ensureCanEditBasicInfo(project, operator));
    }

    @Test
    void partnerStaff_cannotEditSubmittedProject() {
        SysUser operator = partnerStaff(100L);
        Project project = partnerProject(100L);
        project.setStatus("submitted");

        when(currentUserService.isPartnerUser(operator)).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> projectStateGuard.ensureCanEditBasicInfo(project, operator));

        assertEquals("Partner project can only be edited before submission or after rejection", ex.getMessage());
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
        assertEquals("Partner users cannot start projects directly", ex.getMessage());
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

    @Test
    void internalUser_cannotStartPartnerProjectBeforeSetupReady() {
        SysUser operator = internalUser();
        Project project = partnerProject(100L);
        project.setStatus("approved_pending_setup");

        when(currentUserService.isPartnerUser(operator)).thenReturn(false);

        BizException ex = assertThrows(
                BizException.class,
                () -> projectStateGuard.ensureCanStart(project, operator)
        );

        assertEquals(400, ex.getCode());
        assertEquals("Partner project can only start after setup is ready", ex.getMessage());
    }

    @Test
    void internalUser_canStartPartnerProjectWhenSetupReady() {
        SysUser operator = internalUser();
        Project project = partnerProject(100L);
        project.setStatus("setup_ready");

        when(currentUserService.isPartnerUser(operator)).thenReturn(false);

        assertDoesNotThrow(() -> projectStateGuard.ensureCanStart(project, operator));
    }

    @Test
    void internalUser_canResumePausedPartnerProject() {
        SysUser operator = internalUser();
        Project project = partnerProject(100L);
        project.setStatus("paused");

        when(currentUserService.isPartnerUser(operator)).thenReturn(false);

        assertDoesNotThrow(() -> projectStateGuard.ensureCanStart(project, operator));
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

    private SysUser internalUser() {
        SysUser user = new SysUser();
        user.setId(11L);
        user.setRole("delivery_manager");
        return user;
    }
}
