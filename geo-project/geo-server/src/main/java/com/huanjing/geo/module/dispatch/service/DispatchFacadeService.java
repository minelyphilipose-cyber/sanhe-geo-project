package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DispatchFacadeService {

    private final DispatchTaskService dispatchTaskService;
    private final ProjectMapper projectMapper;
    private final CompanyMapper companyMapper;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;

    public void replayTask(Long taskId) {
        SysUser operator = currentUserService.requireCurrentUser();
        DispatchTask task = dispatchTaskService.requireTask(taskId);
        if (!permissionService.hasPerm(operator, "dispatch.task.replay.dead_letter")) {
            throw new BizException(403, "No permission: dispatch.task.replay.dead_letter");
        }
        dispatchTaskService.replayTask(taskId);
    }

    public List<DispatchTask> listReplayableTasks(Long projectId, Integer limit) {
        currentUserService.ensurePermission("project.read");
        if (projectId != null) {
            Project project = projectMapper.selectById(projectId);
            if (project == null || project.getDeletedAt() != null) {
                throw new BizException(404, "Project not found");
            }
            currentUserService.ensurePartnerResourceAccess(currentUserService.requireCurrentUser(), project.getPartnerId(), "project");
        }
        return dispatchTaskService.listReplayableTasks(projectId, limit == null ? 20 : limit);
    }

    public DispatchTask getTaskStatus(Long taskId) {
        SysUser operator = currentUserService.requireCurrentUser();
        DispatchTask task = dispatchTaskService.requireTask(taskId);
        if (currentUserService.isPartnerUser(operator)) {
            throw new BizException(403, "Partner role cannot access dispatch task");
        }
        boolean canRead = permissionService.hasPerm(operator, "project.read")
                || permissionService.hasPerm(operator, "project.update");
        if (!canRead) {
            throw new BizException(403, "No permission to query dispatch task");
        }
        if ("sales".equals(operator.getRole())) {
            Project project = projectMapper.selectById(task.getProjectId());
            if (project == null || project.getDeletedAt() != null) {
                throw new BizException(404, "Project not found");
            }
            Company company = companyMapper.selectById(project.getCompanyId());
            if (company == null || company.getDeletedAt() != null
                    || company.getSalesOwnerId() == null || !company.getSalesOwnerId().equals(operator.getId())) {
                throw new BizException(403, "No permission to query this task");
            }
        }
        if (!"sales".equals(operator.getRole())) {
            Project project = projectMapper.selectById(task.getProjectId());
            if (project == null || project.getDeletedAt() != null) {
                throw new BizException(404, "Project not found");
            }
            currentUserService.ensurePartnerResourceAccess(operator, project.getPartnerId(), "project");
        }
        return task;
    }
}
