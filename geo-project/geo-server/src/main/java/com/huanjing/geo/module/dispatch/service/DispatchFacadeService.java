package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DispatchFacadeService {

    private final DispatchTaskService dispatchTaskService;
    private final ProjectMapper projectMapper;
    private final CompanyMapper companyMapper;
    private final CurrentUserService currentUserService;
    private final PermissionService permissionService;

    public DispatchTask enqueuePresaleDiagnosis(Long projectId, String remark) {
        SysUser operator = currentUserService.requireCurrentUser();
        boolean canEnqueue = permissionService.hasPerm(operator, "project.write")
                || permissionService.hasPerm(operator, "dispatch.presale.enqueue");
        if (!canEnqueue) {
            throw new BizException(403, "No permission: dispatch.presale.enqueue");
        }
        if (currentUserService.isPartnerUser(operator)) {
            throw new BizException(403, "Partner role cannot enqueue presale diagnosis");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BizException(404, "Project not found");
        }
        if ("sales".equals(operator.getRole())) {
            Company company = companyMapper.selectById(project.getCompanyId());
            if (company == null || company.getSalesOwnerId() == null || !company.getSalesOwnerId().equals(operator.getId())) {
                throw new BizException(403, "No permission to enqueue this project");
            }
            if (!"signed".equals(company.getStatus())) {
                throw new BizException(400, "Sales can only enqueue project of signed company");
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", "presale");
        payload.put("remark", remark);

        return dispatchTaskService.createTaskAndEnqueue(
                projectId,
                DispatchTaskType.PRESALE_DIAGNOSIS,
                LocalDate.now(),
                LocalDate.now(),
                LocalDateTime.now(),
                payload
        );
    }

    public void replayTask(Long taskId) {
        SysUser operator = currentUserService.requireCurrentUser();
        currentUserService.ensurePermission("project.write");
        DispatchTask task = dispatchTaskService.requireTask(taskId);
        if (DispatchTaskStatus.DEAD_LETTER.value().equals(task.getStatus())
                && !permissionService.hasPerm(operator, "dispatch.task.replay.dead_letter")) {
            throw new BizException(403, "No permission: dispatch.task.replay.dead_letter");
        }
        dispatchTaskService.replayTask(taskId);
    }

    public List<DispatchTask> listReplayableTasks(Long projectId, Integer limit) {
        currentUserService.ensurePermission("project.read");
        return dispatchTaskService.listReplayableTasks(projectId, limit == null ? 20 : limit);
    }
}
