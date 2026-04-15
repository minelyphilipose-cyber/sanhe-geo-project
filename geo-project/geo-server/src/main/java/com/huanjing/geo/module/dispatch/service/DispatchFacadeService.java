package com.huanjing.geo.module.dispatch.service;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.customer.entity.Company;
import com.huanjing.geo.module.customer.mapper.CompanyMapper;
import com.huanjing.geo.module.dispatch.entity.DispatchTask;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskStatus;
import com.huanjing.geo.module.dispatch.enums.DispatchTaskType;
import com.huanjing.geo.module.dispatch.mapper.DispatchTaskMapper;
import com.huanjing.geo.module.project.entity.Project;
import com.huanjing.geo.module.project.mapper.ProjectMapper;
import com.huanjing.geo.module.report.entity.PresaleQuestionSet;
import com.huanjing.geo.module.report.service.PresaleQuestionSetService;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import com.huanjing.geo.module.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
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
    private final PresaleQuestionSetService presaleQuestionSetService;
    private final DispatchTaskMapper dispatchTaskMapper;

    public DispatchTask enqueuePresaleDiagnosis(Long projectId, Long questionSetId, String remark) {
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
        PresaleQuestionSet lockedSet;
        if (questionSetId != null) {
            lockedSet = presaleQuestionSetService.latestLockedSet(projectId);
            if (lockedSet == null || !lockedSet.getId().equals(questionSetId)) {
                throw new BizException(400, "question_set_id must be latest locked set");
            }
        } else {
            lockedSet = presaleQuestionSetService.latestLockedSet(projectId);
            if (lockedSet == null) {
                throw new BizException(400, "No locked presale question set");
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", "presale");
        payload.put("questionSetId", lockedSet.getId());
        payload.put("operatorId", operator.getId());
        payload.put("remark", remark);

        DispatchTask running = dispatchTaskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DispatchTask>()
                        .eq(DispatchTask::getProjectId, projectId)
                        .eq(DispatchTask::getTaskType, DispatchTaskType.PRESALE_DIAGNOSIS.name())
                        .in(DispatchTask::getStatus, Arrays.asList(
                                DispatchTaskStatus.PENDING.value(),
                                DispatchTaskStatus.RUNNING.value(),
                                DispatchTaskStatus.RETRY_PENDING.value()
                        ))
                        .like(DispatchTask::getPayloadJson, "\"questionSetId\":" + lockedSet.getId())
                        .orderByDesc(DispatchTask::getId)
                        .last("LIMIT 1")
        );
        if (running != null) {
            throw new BizException(400, "诊断正在进行中，请等待完成");
        }

        LocalDate idempotentWindow = LocalDate.of(2000, 1, 1).plusDays(lockedSet.getId());

        return dispatchTaskService.createTaskAndEnqueue(
                projectId,
                DispatchTaskType.PRESALE_DIAGNOSIS,
                idempotentWindow,
                idempotentWindow,
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

    public DispatchTask getTaskStatus(Long taskId) {
        SysUser operator = currentUserService.requireCurrentUser();
        DispatchTask task = dispatchTaskService.requireTask(taskId);
        if (!DispatchTaskType.PRESALE_DIAGNOSIS.name().equals(task.getTaskType())) {
            throw new BizException(400, "Only presale diagnosis task is supported");
        }
        if (currentUserService.isPartnerUser(operator)) {
            throw new BizException(403, "Partner role cannot access diagnosis task");
        }
        boolean canRead = permissionService.hasPerm(operator, "project.read")
                || permissionService.hasPerm(operator, "project.write")
                || permissionService.hasPerm(operator, "dispatch.presale.enqueue");
        if (!canRead) {
            throw new BizException(403, "No permission to query diagnosis task");
        }
        if ("sales".equals(operator.getRole())) {
            Project project = projectMapper.selectById(task.getProjectId());
            if (project == null) {
                throw new BizException(404, "Project not found");
            }
            Company company = companyMapper.selectById(project.getCompanyId());
            if (company == null || company.getSalesOwnerId() == null || !company.getSalesOwnerId().equals(operator.getId())) {
                throw new BizException(403, "No permission to query this task");
            }
        }
        return task;
    }
}
