package com.huanjing.geo.module.project.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.access.InternalScopeService;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ProjectStateGuard {

    private final CurrentUserService currentUserService;
    private final InternalScopeService internalScopeService;

    public void ensureCanEditBasicInfo(Project project, SysUser operator) {
        currentUserService.ensurePermission("project.update");
        ensureCanEditPartnerProjectData(project, operator);
        ensureNotTerminal(project);
    }

    public void ensureCanEditPartnerProjectData(Project project, SysUser operator) {
        ensureVisiblePartnerResource(project, operator);
        ensurePartnerEditableStatus(project, operator);
    }

    public void ensureCanChangePackage(Project project, SysUser operator) {
        currentUserService.ensurePermission("project.update");
        ensureVisiblePartnerResource(project, operator);
        if (StringUtils.hasText(project.getDeductionTxnNo())
                || "active".equals(project.getStatus())
                || "completed".equals(project.getStatus())
                || "terminated".equals(project.getStatus())
                || "expired".equals(project.getStatus())) {
            throw new BizException(400, "Project package is locked after activation or deduction");
        }
    }

    public void ensureCanStart(Project project, SysUser operator) {
        currentUserService.ensurePermission("project.start");
        ensureVisiblePartnerResource(project, operator);
        if (currentUserService.isPartnerUser(operator)) {
            throw new BizException(403, "Partner users cannot start projects directly");
        }
        if (isPartnerOwned(project) && !"setup_ready".equals(project.getStatus()) && !"paused".equals(project.getStatus())) {
            throw new BizException(400, "Partner project can only start after setup is ready");
        }
    }

    public void ensureCanPause(Project project, SysUser operator) {
        currentUserService.ensurePermission("project.pause");
        ensureVisiblePartnerResource(project, operator);
        if (currentUserService.isPartnerUser(operator)) {
            throw new BizException(403, "Partner users cannot pause projects directly");
        }
    }

    public void ensureCanTerminate(Project project, SysUser operator) {
        currentUserService.ensurePermission("project.terminate");
        ensureVisiblePartnerResource(project, operator);
        if (currentUserService.isPartnerUser(operator)) {
            throw new BizException(403, "Partner users cannot terminate projects directly");
        }
    }

    public void ensureCanDelete(Project project, SysUser operator) {
        currentUserService.ensurePermission("project.delete");
        ensureVisiblePartnerResource(project, operator);
        if (StringUtils.hasText(project.getDeductionTxnNo()) || "active".equals(project.getStatus())) {
            throw new BizException(400, "Activated project cannot be deleted");
        }
    }

    public void ensureCanChangeStage(Project project, SysUser operator, String targetStage) {
        currentUserService.ensurePermission("project.update");
        ensureVisiblePartnerResource(project, operator);
        if (currentUserService.isPartnerUser(operator)) {
            throw new BizException(403, "Partner users cannot change project stage directly");
        }
    }

    private void ensureVisiblePartnerResource(Project project, SysUser operator) {
        currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        internalScopeService.ensureProjectAccess(operator, project, "project");
    }

    private void ensureNotTerminal(Project project) {
        if ("completed".equals(project.getStatus()) || "terminated".equals(project.getStatus()) || "expired".equals(project.getStatus())) {
            throw new BizException(400, "Terminal project cannot be edited");
        }
    }

    private void ensurePartnerEditableStatus(Project project, SysUser operator) {
        if (!currentUserService.isPartnerUser(operator) || !isPartnerOwned(project)) {
            return;
        }
        if ("draft".equals(project.getStatus())
                || "pending_start".equals(project.getStatus())
                || "rejected".equals(project.getStatus())) {
            return;
        }
        throw new BizException(400, "Partner project can only be edited before submission or after rejection");
    }

    private boolean isPartnerOwned(Project project) {
        return "partner".equals(project.getOwnerType());
    }
}
