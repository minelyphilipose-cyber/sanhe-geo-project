package com.huanjing.geo.module.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.common.security.LoginUser;
import com.huanjing.geo.module.content.dto.ProjectSelfMediaAutoScheduleRequest;
import com.huanjing.geo.module.content.entity.ProjectSelfMediaPlanningTask;
import com.huanjing.geo.module.content.mapper.ProjectSelfMediaPlanningTaskMapper;
import com.huanjing.geo.module.content.vo.ProjectSelfMediaPlanningTaskVO;
import com.huanjing.geo.module.content.vo.SelfMediaPublishAutoScheduleResponse;
import com.huanjing.geo.module.partner.service.PartnerFeatureAccessGuard;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.service.CurrentUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ProjectSelfMediaPlanningService {
    private final ProjectSelfMediaPlanningTaskMapper taskMapper;
    private final ProjectSelfMediaScheduleService scheduleService;
    private final CurrentUserService currentUserService;
    private final PartnerFeatureAccessGuard accessGuard;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public ProjectSelfMediaPlanningService(ProjectSelfMediaPlanningTaskMapper taskMapper,
            ProjectSelfMediaScheduleService scheduleService, CurrentUserService currentUserService,
            PartnerFeatureAccessGuard accessGuard, ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.taskMapper = taskMapper;
        this.scheduleService = scheduleService;
        this.currentUserService = currentUserService;
        this.accessGuard = accessGuard;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public ProjectSelfMediaPlanningTaskVO submit(Long projectId, ProjectSelfMediaAutoScheduleRequest request, String key) {
        accessGuard.ensureInternalDeliveryFeature("self-media schedule operations");
        var config = scheduleService.getConfig(projectId); // Reuse project/brand operation access checks.
        if (!Boolean.TRUE.equals(config.getAutoScheduleEnabled())) {
            throw new BizException("项目未开启自媒体自动排期开关");
        }
        validateMonth(request.getTargetMonth());
        String requestKey = StringUtils.hasText(key) ? key.trim() : UUID.randomUUID().toString();
        if (requestKey.length() > 160) throw new BizException("排期请求标识过长");
        String payload = write(request);
        ProjectSelfMediaPlanningTask existing = taskMapper.selectByKey(projectId, requestKey);
        if (existing != null) return replay(existing, payload);
        existing = taskMapper.selectLatest(projectId, request.getTargetMonth());
        if (existing != null && "pending".equals(existing.getStatus())) return toVO(existing);
        ProjectSelfMediaPlanningTask row = new ProjectSelfMediaPlanningTask();
        row.setProjectId(projectId);
        row.setTargetMonth(request.getTargetMonth());
        row.setIdempotencyKey(requestKey);
        row.setRequestPayload(payload);
        row.setStatus("pending");
        row.setCreatedBy(currentUserService.requireCurrentUser().getId());
        try {
            taskMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            existing = taskMapper.selectByKey(projectId, requestKey);
            if (existing != null) return replay(existing, payload);
            existing = taskMapper.selectLatest(projectId, request.getTargetMonth());
            if (existing != null && "pending".equals(existing.getStatus())) return toVO(existing);
            throw ex;
        }
        return toVO(row);
    }

    public ProjectSelfMediaPlanningTaskVO latest(Long projectId, String month) {
        accessGuard.ensureInternalDeliveryFeature("self-media schedule operations");
        scheduleService.getConfig(projectId);
        validateMonth(month);
        return toVO(taskMapper.selectLatest(projectId, month));
    }

    public boolean processNext() {
        AtomicReference<Long> taskId = new AtomicReference<>();
        try {
            return Boolean.TRUE.equals(transaction.execute(status -> {
                ProjectSelfMediaPlanningTask task = taskMapper.selectPendingForUpdate();
                if (task == null) return false;
                taskId.set(task.getId());
                SelfMediaPublishAutoScheduleResponse result = executeAsOperator(task);
                task.setStatus("success");
                task.setResultPayload(write(result));
                taskMapper.updateById(task);
                return true;
            }));
        } catch (RuntimeException ex) {
            if (taskId.get() == null) throw ex;
            // Planning has rolled back before recording failure, so no partial quota reservation remains.
            transaction.executeWithoutResult(status -> {
                ProjectSelfMediaPlanningTask task = taskMapper.selectForUpdate(taskId.get());
                if (task != null && "pending".equals(task.getStatus())) {
                    task.setStatus("failed");
                    String message = ex instanceof BizException ? ex.getMessage() : "后台排期失败，请稍后重试或联系管理员";
                    task.setFailureMessage(message == null ? "后台排期失败" : message.substring(0, Math.min(512, message.length())));
                    taskMapper.updateById(task);
                }
            });
            log.warn("project self-media planning failed taskId={}", taskId.get(), ex);
            return true;
        }
    }

    private SelfMediaPublishAutoScheduleResponse executeAsOperator(ProjectSelfMediaPlanningTask task) {
        SysUser operator = currentUserService.requireById(task.getCreatedBy());
        if (Boolean.FALSE.equals(operator.getIsActive())) throw new BizException("提交排期的操作账号已停用");
        var previousContext = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(operator.getId(), operator.getUsername(), operator.getRole(), operator.getTokenVersion()),
                null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            accessGuard.ensureInternalDeliveryFeature("self-media schedule operations");
            return scheduleService.createForProject(task.getProjectId(), read(task.getRequestPayload(), ProjectSelfMediaAutoScheduleRequest.class),
                    ProjectSelfMediaScheduleService.TRIGGER_MANUAL, task.getIdempotencyKey());
        } finally {
            SecurityContextHolder.setContext(previousContext);
        }
    }

    private ProjectSelfMediaPlanningTaskVO replay(ProjectSelfMediaPlanningTask task, String payload) {
        if (!task.getRequestPayload().equals(payload)) throw new BizException("同一排期请求标识不能用于不同参数");
        return toVO(task);
    }

    private ProjectSelfMediaPlanningTaskVO toVO(ProjectSelfMediaPlanningTask row) {
        if (row == null) return null;
        ProjectSelfMediaPlanningTaskVO vo = new ProjectSelfMediaPlanningTaskVO();
        vo.setId(row.getId());
        vo.setProjectId(row.getProjectId());
        vo.setTargetMonth(row.getTargetMonth());
        vo.setStatus(row.getStatus());
        vo.setSupplementExistingBatch(read(row.getRequestPayload(), ProjectSelfMediaAutoScheduleRequest.class).getSupplementExistingBatch());
        vo.setFailureMessage(row.getFailureMessage());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt());
        if (row.getResultPayload() != null) vo.setResult(read(row.getResultPayload(), SelfMediaPublishAutoScheduleResponse.class));
        return vo;
    }

    private void validateMonth(String month) {
        try { YearMonth.parse(month); }
        catch (RuntimeException ex) { throw new BizException("请选择有效的目标月份"); }
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize planning task", ex); }
    }

    private <T> T read(String value, Class<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot read planning task", ex); }
    }
}
