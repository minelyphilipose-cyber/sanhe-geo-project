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
        ensureVisiblePartnerResource(project, operator);
        ensureNotTerminal(project);
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
            if (!"partner".equals(operator.getRole())) {
                throw new BizException(403, "Only partner administrator can start partner project");
            }
            if (project.getPartnerId() == null || !project.getPartnerId().equals(operator.getPartnerId())) {
                throw new BizException(403, "No permission to start this project");
            }
            return;
        }
        if (isPartnerOwned(project)) {
            throw new BizException(403, "Internal users cannot start partner projects");
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

    private boolean isPartnerOwned(Project project) {
        return "partner".equals(project.getOwnerType()) || "joint".equals(project.getOwnerType());
    }
}
