package com.huanjing.geo.module.project.service;

import com.huanjing.geo.module.dispatch.service.BrandStatementDispatchService;
import com.huanjing.geo.module.partner.entity.PartnerAccount;
import com.huanjing.geo.module.partner.entity.PartnerAccountTxn;
import com.huanjing.geo.module.partner.mapper.PartnerAccountMapper;
import com.huanjing.geo.module.partner.mapper.PartnerAccountTxnMapper;
import com.huanjing.geo.module.project.dto.ProjectStatusUpdateRequest;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.ActivityLogService;
import com.huanjing.geo.module.system.service.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceStatusTest {

    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private PartnerAccountMapper partnerAccountMapper;
    @Mock
    private PartnerAccountTxnMapper partnerAccountTxnMapper;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ProjectStateGuard projectStateGuard;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private BrandStatementDispatchService brandStatementDispatchService;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void updateStatus_paidPausedProject_movesActiveWithoutDeductingAgain() {
        SysUser operator = new SysUser();
        operator.setId(10L);

        Project project = new Project();
        project.setId(20L);
        project.setStatus("paused");
        project.setStage("pending_start");
        project.setOwnerType("partner");
        project.setPartnerId(100L);
        project.setDeductionTxnNo("PTD_ALREADY_PAID");

        ProjectStatusUpdateRequest request = new ProjectStatusUpdateRequest();
        request.setStatus("active");

        when(currentUserService.requireCurrentUser()).thenReturn(operator);
        when(projectMapper.selectById(20L)).thenReturn(project);

        projectService.updateStatus(20L, request);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectMapper).updateById(captor.capture());
        assertEquals("active", captor.getValue().getStatus());
        assertEquals("PTD_ALREADY_PAID", captor.getValue().getDeductionTxnNo());
        verify(projectStateGuard).ensureCanStart(project, operator);
        verify(partnerAccountMapper, never()).updateById(any(PartnerAccount.class));
        verify(partnerAccountTxnMapper, never()).insert(any(PartnerAccountTxn.class));
        verify(activityLogService).logAction(any(), any(), any(), any(), any(), any(), any());
        verify(projectMapper, never()).deleteById(20L);
    }
}
